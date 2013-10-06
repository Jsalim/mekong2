package models;

import com.mongodb.*;
import org.neo4j.graphdb.*;
import utils.Record;
import utils.mongodb.MongoDatabaseConnection;
import utils.neo4j.Neo4jDatabaseConnection;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class Cart extends Record<Cart> {

    private static final String BOOK_COLLECTION = "transactions";
    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;
    private static volatile Cart instance = null;

    protected Cart(DBObject record) {
        super(record);
    }

    protected Cart() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
    }

    public static Cart getInstance() throws UnknownHostException {
        if (instance == null) {
           instance = new Cart();
        }
        return instance;
    }

    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(BOOK_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    @Override
    public Cart fromMongoRecord(DBObject record) {
        return new Cart(record);
    }

    /**
     * Finds the users cart, or creates a new one if it can't be found.
     * Carts exist if they have a status of pending.
     * @param user User to find the cart for.
     * @return New or existing cart for the provided user.
     */
    public static Cart findOrCreateUsersCart(User user) {
        Cart result = null;
        try {
            Cart instance = Cart.getInstance();
            DBCollection cartCollection = instance.getMongoCollection();
            BasicDBObject cartQuery = new BasicDBObject();
            System.out.println("user " + user.toString());
            cartQuery.append("user_id", user.getMongo("_id"));
            cartQuery.append("status", "pending");

            // Find the cart.
            BasicDBObject record = (BasicDBObject) cartCollection.findOne(cartQuery);
            if (null == record) {
                System.out.println("Failed to find user cart " + record);
            }
            result = instance.fromMongoRecord(record);

            // Create the cart if not found.
            if (null == result) {
                cartQuery.put("items", new BasicDBList());
                WriteResult recordWritten = cartCollection.insert(cartQuery);
                CommandResult isRecordWritten = recordWritten.getLastError();
                if (null == isRecordWritten.get("err")) {
                    result= instance.fromMongoRecord(cartQuery);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }


    /**
     *
     * @param entries
     * @param relative
     * @return
     */
    public Cart adjustQuantityOfBook(Map<Book, Integer> entries, boolean relative) {
        try {
            Cart instance = Cart.getInstance();
            BasicDBObject item = null;
            BasicDBList items = (BasicDBList) this.getMongo("items");
            for (Map.Entry<Book, Integer> entry : entries.entrySet()) {
                Book book = entry.getKey();
                Integer quantity = entry.getValue();

                // Check if the book is in the cart.
                for (int i = 0; i < items.size(); i++) {
                    BasicDBObject tempItem = (BasicDBObject) items.get(i);
                    if (tempItem.getString("isbn").equals(book.getMongo("isbn"))) {
                        item = tempItem;
                        break;
                    }
                }

                // Add the item to the basket
                if (item == null) {
                    item = new BasicDBObject();
                    item.put("isbn", String.valueOf(book.getMongo("isbn")));
                    item.put("quantity", quantity);
                    items.add(item);
                } else {
                    if (relative) {
                        item.put("quantity", item.getInt("quantity") + quantity);
                    } else {
                        item.put("quantity", quantity);
                    }
                }

            }

            // If the book is not in the cart add it.
            BasicDBObject updateFields = new BasicDBObject();
            updateFields.put("items", items);
            BasicDBObject updateQuery = new BasicDBObject("$set", updateFields);
            DBCollection collection = instance.getMongoCollection();
            WriteResult recordWritten = collection.update(getMongoRecord(), updateQuery);
            CommandResult isRecordWritten = recordWritten.getLastError();
            if (null == isRecordWritten.get("err")) {
                return this;
            } else {
                return null;
            }

        } catch (Exception e) {
            return null;
        }
    }

    /**
     *
     * @return
     */
    public boolean checkout() {
        Cart instance = null;
        GraphDatabaseService graphDB = null;
        try {
            instance = Cart.getInstance();
            graphDB = Neo4jDatabaseConnection.getInstance().getService();
        } catch (UnknownHostException e) {
            e.printStackTrace();
            return false;
        }

        // Information required for processing the transaction.
        Boolean result = false;
        Transaction tx = graphDB.beginTx();
        Long when = System.currentTimeMillis() / 1000L;
        List<Lock> locks = new ArrayList<Lock>();
        try {

            // Get all of the desired books.
            Map<String, Integer> isbnToQuantity = new HashMap<String, Integer>();
            BasicDBList requestedBooks = (BasicDBList) this.getMongo("items");
            for (int i = 0; i < requestedBooks.size(); i++) {
                BasicDBObject book = (BasicDBObject) requestedBooks.get(i);
                isbnToQuantity.put(book.getString("isbn"), book.getInt("quantity"));
            }

            // Query all of the node representations for the books.
            String query = "START b=node:Users(isbn IN {isbns})\n" +
                    "WITH b.isbn AS isbn, b.stock AS stock, b AS book\n" +
                    "RETURN book, isbn, stock;";
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("isbns", isbnToQuantity.keySet().toArray());
            ExecutionEngine queryExecuter = new ExecutionEngine(graphDB);
            ExecutionResult queryResult = queryExecuter.execute(query, params);

            // Find the user node and acquire a write lock.
            String userQuery = "START u=node:Users(username = {username}) RETURN u";
            ExecutionResult userResult = queryExecuter.execute(userQuery);
            Node userNode = (Node) userResult.columnAs("u").next();
            locks.add(tx.acquireWriteLock(userNode));

            // Map all of the row nodes to their ISBNs.
            List<String> unavailableIsbns = new ArrayList<String>();
            Map<String, Map<String, Object>> isbnToRow = new HashMap<String, Map<String, Object>>();
            for (Map<String, Object> row : queryResult) {

                // Identify the node
                String isbn = String.valueOf(row.get("isbn"));
                Node bookNode = (Node) row.get("book");
                isbnToRow.put(isbn, row);

                // Acquire write lock
                locks.add(tx.acquireWriteLock(bookNode));

                // Check availability
                Integer requiredQuantity = isbnToQuantity.get(isbn);
                Integer availableQuantity = (Integer) row.get("stock");

                // If the book is not available with the right amount add it to
                // the list of unavailable books.
                if (requiredQuantity > availableQuantity) {
                    unavailableIsbns.add(isbn);

                // Otherwise update the stock for the book, create a buys relation
                } else {
                    Relationship buysRelationship = userNode.createRelationshipTo(bookNode,
                          User.RELATIONSHIPS.BUYS);
                    buysRelationship.setProperty("transaction", this.getMongo("_id"));
                    buysRelationship.setProperty("quantity", requiredQuantity);
                    buysRelationship.setProperty("when", when);
                    bookNode.setProperty("stock", availableQuantity - requiredQuantity);
                }
            }

            // Otherwise success! update the MongoDB record and commit the transaction!
            if (0 == unavailableIsbns.size()) {

                // Get the current transaction information
                // Create the buys relationship between the user and all of the
                // books that they have purchased.
                BasicDBObject updateFields = new BasicDBObject();
                updateFields.put("status", "complete");
                updateFields.put("when", when);
                BasicDBObject updateQuery = new BasicDBObject("$set", updateFields);
                DBCollection collection = instance.getMongoCollection();
                WriteResult recordWritten = collection.update(getMongoRecord(), updateQuery);
                CommandResult isRecordWritten = recordWritten.getLastError();
                if (null == isRecordWritten.get("err")) {
                    tx.success();
                } else {
                    tx.failure();
                }

            // If one of the books is unavailable fail the transactions.
            } else {
                tx.failure();
            }

        // Something went wrong, fail the transaction.
        } catch (Exception e) {
            e.printStackTrace();
            tx.failure();

        // Release write locks (if any) that were acquired and finish the
        // transaction.
        } finally {
            for (Lock lock : locks) {
                lock.release();
            }
            tx.finish();
            return result;
        }
    }

}

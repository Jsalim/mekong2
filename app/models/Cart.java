package models;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.neo4j.graphdb.*;
import org.neo4j.helpers.collection.IteratorUtil;
import utils.Record;
import utils.mongodb.MongoDatabaseConnection;
import utils.neo4j.Neo4jDatabaseConnection;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

import java.net.UnknownHostException;
import java.util.*;

/**
 *
 */
public class Cart extends Record<Cart> {

    private static final String CARTS_COLLECTION = "carts";
    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;
    private static volatile Cart instance = null;

    /**
     *
     * @param record
     */
    protected Cart(DBObject record) {
        super(record);
    }

    /**
     *
     * @throws UnknownHostException
     */
    protected Cart() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
    }

    /**
     *
     * @return
     * @throws UnknownHostException
     */
    public static Cart getInstance() throws UnknownHostException {
        if (instance == null) {
           instance = new Cart();
        }
        return instance;
    }

    /**
     *
     * @return
     */
    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(CARTS_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    /**
     *
     * @param record
     * @return
     */
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
            cartQuery.append("user_id", user.getMongo("_id"));
            cartQuery.append("status", "pending");

            // Find the cart.
            System.out.println("Attempting to find user cart with query" + cartQuery.toString());
            BasicDBObject record = (BasicDBObject) cartCollection.findOne(cartQuery);
            if (null == record) {
                System.out.println("Failed to find user cart " + record);
            } else {
                result = instance.fromMongoRecord(record);
            }

            // Create the cart
            System.out.println("Attempting to create user cart");
            if (null == result) {
                cartQuery.put("items", new BasicDBList());
                WriteResult recordWritten = cartCollection.insert(cartQuery);
                CommandResult isRecordWritten = recordWritten.getLastError();
                if (null == isRecordWritten.get("err")) {
                    System.out.println("Created new user cart");
                    result = instance.fromMongoRecord(cartQuery);
                } else {
                    System.out.println("Failed to create user cart " + isRecordWritten.toString());
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
            ObjectId cartId = (ObjectId) getMongo("_id");
            ObjectId userId = (ObjectId) getMongo("user_id");
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

                // Item is not in the cart it must be added.
                BasicDBObject cartProjection = new BasicDBObject();
                cartProjection.append("_id", cartId);
                cartProjection.append("user_id", userId);
                cartProjection.append("status", "pending");

                BasicDBObject updateQuery = new BasicDBObject();
                if (item == null && quantity > 0) {
                    System.out.println("Item is not in the cart, adding ...");

                    item = new BasicDBObject();
                    item.put("isbn", String.valueOf(book.getMongo("isbn")));
                    item.put("title", String.valueOf(book.getMongo("title")));
                    item.put("quantity", quantity);
                    updateQuery.append("$push", new BasicDBObject("items", item));
                    addMessage(MessageType.SUCCESS, "Added " + quantity + " copies of '" + book.getMongo("title") + "(" + book.getMongo("isbn") + ")" + "' to cart.");

                    System.out.println("Built add query (" + cartProjection + ", " + updateQuery);

                // Item is already in the cart it must be updated.
                } else {
                    System.out.println("Item is already in the cart, updating ...");

                    cartProjection.append("items.isbn", item.get("isbn"));
                    if (relative) {
                        quantity += item.getInt("quantity");
                    }

                    // Prevent a relative update of more stock that is available
                    // Silently correct the users wish.
                    if (quantity > book.getStock()) {
                        quantity = book.getStock();
                        addMessage(MessageType.WARNING,
                                "Not enough stock! We've added the rest of our stock for '" + book.getMongo("title") + "(" + book.getMongo("isbn") + ")" + "' to your cart.");
                    }

                    // The item has actually been removed, remove it from items.
                    if (quantity == 0) {
                        addMessage(MessageType.SUCCESS,
                                "Removed '" + book.getMongo("title") + "' from cart.");
                        updateQuery.append("$pull", new BasicDBObject("items",
                                new BasicDBObject("isbn", item.get("isbn"))));

                    // Update the item in the items list.
                    } else {
                        addMessage(MessageType.SUCCESS,
                                "Added " + quantity + " copies of '" + book.getMongo("title") + "(" + book.getMongo("isbn") + ")" + "' to cart.");
                        updateQuery.append("$set", new BasicDBObject("items.$.quantity", quantity));
                    }

                    System.out.println("Built adjust query (" + cartProjection + ", " + updateQuery + ")");
                }

                // Update the item in the cart
                DBCollection collection = instance.getMongoCollection();
                WriteResult recordWritten = collection.update(cartProjection, updateQuery);
                CommandResult isRecordWritten = recordWritten.getLastError();

                // If there was an error then add the error to the record.
                if (null != isRecordWritten.get("err")) {
                    addMessage(MessageType.ERROR, "Unable to add '" + book.getMongo("title") + "(" + book.getMongo("isbn") + ")" + "' to cart.");
                } else {

                }
            }

            loadMongoRecord();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return this;
        }
    }

    /**
     *
     * @return
     */
    public List<Book> getItemsInCart() {
        List<Book> result = new ArrayList<Book>();
        System.out.println("Inferring items for cart " + getMongoRecord());
        try {
            Book instance = Book.getInstance();
            BasicDBList bookRecords = (BasicDBList) getMongo("items");
            for (int i = 0; i < bookRecords.size(); i++) {
                BasicDBObject bookRecord = (BasicDBObject) bookRecords.get(i);
                Book book = instance.fromMongoRecord(bookRecord);
                result.add(book);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            System.out.println("Items in cart include " + result);
            return result;
        }
    }

    /**
     *
     * @return
     */
    public boolean checkout(String username) {

        // Linkup with the database connections.
        Cart instance = null;
        GraphDatabaseService graphDB = null;
        try {
          instance = Cart.getInstance();
          graphDB = Neo4jDatabaseConnection.getInstance().getService();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        // Information required for processing the transaction.
        Boolean result = null;
        Transaction tx = graphDB.beginTx();
        Long when = System.currentTimeMillis() / 1000L;
        List<Lock> locks = new ArrayList<Lock>();
        try {


            /*
             * ACQUIRE THE USER NODE FOR CREATING RELATIONS AGAINST
             */
            String userQuery = "START u=node:Users(username = {username}) RETURN u";
            Map<String, Object> params = new HashMap<String, Object>();
            params.put("username", username);
            ExecutionEngine queryExecuter = new ExecutionEngine(graphDB);
            ExecutionResult userResult = queryExecuter.execute(userQuery, params);
            Node userNode = (Node) userResult.columnAs("u").next();
            locks.add(tx.acquireWriteLock(userNode));
            System.out.println("Conducting transaction for user " + userNode);


            /*
             * CREATE A MAPPING OF ALL THE BOOKS IN THE CART TO THE QUANTITY
             * THAT IS TO BE PURCHASED
             * isbn => quantity
             */
            Map<String, Integer> isbnToQuantity = new HashMap<String, Integer>();
            BasicDBList requestedBooks = (BasicDBList) this.getMongo("items");
            for (int i = 0; i < requestedBooks.size(); i++) {
                BasicDBObject book = (BasicDBObject) requestedBooks.get(i);
                isbnToQuantity.put(book.getString("isbn"), book.getInt("quantity"));
            }
            System.out.println("Checking out with books" + isbnToQuantity);


            /*
             * CREATE A MAPPING OF ALL THE BOOKS IN THE DATABASE TO THE QUANTITY
             * THAT IS TO BE PURCHASED
             */
            String query = "START book=node(*) WHERE book.isbn! IN {isbns} RETURN book";
            params = new HashMap<String, Object>();
            params.put("isbns", new ArrayList<String>(isbnToQuantity.keySet()));
            ExecutionResult executionResult = queryExecuter.execute(query, params);
            System.out.println(executionResult);

            /*
             * CHECKOUT ALL OF THE BOOKS BY UPDATING THE STOCK LEVELS AND CREATING
             * AN APPROPRIATE BUYS RELATIONSHIP BETWEEN THEM AND THE USER.
             */
            Iterator<Node> bookNodes = executionResult.columnAs("book");
            for (Node bookNode : IteratorUtil.asIterable(bookNodes)) {
                locks.add(tx.acquireWriteLock(bookNode));
                System.out.println("Found node " + bookNode);
                System.out.println(bookNode.getPropertyKeys());

                // Check how much the user wants, and what we have available.
                String isbn = bookNode.getProperty("isbn").toString();
                Integer requiredQuantity = isbnToQuantity.get(isbn);
                Integer availableQuantity = Integer.valueOf(bookNode.getProperty("stock").toString());
                System.out.println("Purchasing " + requiredQuantity + " from " + isbn + " with " + availableQuantity + "in stock.");

                // If one book is available force the transaction to fail.
                if (requiredQuantity > availableQuantity) {
                    result = false;
                    addMessage(MessageType.ERROR, requiredQuantity + " of '" + bookNode.getProperty("title").toString() + "' no longer available. Only " + availableQuantity + "left!");

                // Otherwise update the stock for the book, create a buys relation
                } else {
                    Relationship buysRelationship = userNode.createRelationshipTo(bookNode, User.RELATIONSHIPS.BUYS);
                    buysRelationship.setProperty("transaction", getMongo("_id").toString());
                    buysRelationship.setProperty("quantity", requiredQuantity);
                    buysRelationship.setProperty("when", when);
                    bookNode.setProperty("stock", availableQuantity - requiredQuantity);
                }
            }

            /*
             * If no result has been assigned yet we assume that the checkout process
             * has completed the information for Neo4j and we can commit the action
             * to the MongoDB cart, if that is a success then we can complete the
             * transaction.
             */
            if (result == null) {
                BasicDBObject updateFields = new BasicDBObject();
                updateFields.put("status", "complete");
                updateFields.put("when", when);
                BasicDBObject updateQuery = new BasicDBObject("$set", updateFields);
                DBCollection collection = instance.getMongoCollection();
                WriteResult recordWritten = collection.update(getMongoRecord(), updateQuery);
                CommandResult isRecordWritten = recordWritten.getLastError();
                if (null == isRecordWritten.get("err")) {
                    result = true;
                    tx.success();
                } else {
                    tx.failure();
                }

            /*
             * Something has gone wrong, the result has been determined, abort
             * the transaction.
             */
            } else {
                tx.failure();
            }

        // Something went wrong, fail the transaction.
        } catch (Exception e) {
            e.printStackTrace();
            addMessage(MessageType.ERROR, "Unable to checkout " + e);
            tx.failure();

        // Release write locks (if any) that were acquired and finish the
        // transaction.
        } finally {
            for (Lock lock : locks) {
                lock.release();
            }
            tx.finish();
            if (null == result) {
                result = false;
            }
            return result;
        }
    }

}

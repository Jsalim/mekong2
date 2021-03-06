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

import java.math.BigDecimal;
import java.net.UnknownHostException;
import java.util.*;

/**
 * @author Jack Galile (430395187)
 *
 * Handles all of the polyglot database logic for dealing with the assignment
 * requirements for Carts.
 *
 */
public class Cart extends Record<Cart> {

    private static final String CARTS_COLLECTION = "carts";

    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;

    private static volatile Cart instance = null;

    /**
     * Construct a cart object that wraps the MongoDB document
     * @param record
     */
    protected Cart(DBObject record) {
        super(record);
    }

    /**
     * Create the single record for the carts.
     * @throws UnknownHostException
     */
    protected Cart() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
    }

    /**
     * Returns or creates the singleton instance for the Cart records.
     * @return The singleton instance for the Cart records.
     * @throws UnknownHostException This is thrown if there was an issue
     * when connecting with the MongoDB database.
     */
    public static Cart getInstance() throws UnknownHostException {
        if (instance == null) {
           instance = new Cart();
        }
        return instance;
    }

    /**
     * Get the MongoDB collection for the carts so that it can be used for
     * parsing through the carts if necessary.
     * @return MongoDB collection that contains all of the cart objects.
     */
    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(CARTS_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    /**
     * Creates a new Cart record from the provided MongoDB document.
     * @param record MongoDB document to create the cart for.
     * @return The new Cart object from the MongoDB document.
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
            cartQuery.append("username", user.getMongo("username"));
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
     * Provided a map of books and their new amounts it attempts to update
     * the stock for the cart. If the stock levels that are persisted in the
     * database have changed then a message is added and the transaction will
     * not be succeeded. However it will not abort directly until all the stock
     * has been checked. This means that the user can update all of their choices
     * first before anything bad occurs.
     *
     * If the user adds way more stock than is available, instead of failing we
     * simply only given them the rest of the stock. Letting them know with
     * a message that we don't have enough stock, but they have been given the
     * rest of it.
     *
     * @param entries Mapping of Books to their new quantities.
     * @param relative If true then the current stock levels are adjusted by the
     *                 new quantity amounts. If false the the stock levels are
     *                 set explicitly as the new amounts.
     * @return The cart which has been mutated to reflect the changes.
     */
    public Cart adjustQuantityOfBook(Map<Book, Integer> entries, boolean relative) {
        try {
            Cart instance = Cart.getInstance();
            ObjectId cartId = (ObjectId) getMongo("_id");
            String  username = (String) getMongo("username");
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
                cartProjection.append("username", username);
                cartProjection.append("status", "pending");

                BasicDBObject updateQuery = new BasicDBObject();
                if (item == null && quantity > 0) {
                    System.out.println("Item is not in the cart, adding ...");

                    item = new BasicDBObject();
                    item.put("isbn", String.valueOf(book.getMongo("isbn")));
                    item.put("title", String.valueOf(book.getMongo("title")));
                    item.put("quantity", quantity);
                    item.put("price", Double.valueOf(book.getMongo("price").toString()));
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
     * Gets a list of book representations for the items in the cart. This does
     * not mean that each book has all of the information, in order for that
     * to occur is must force load the MongoDB record again.
     * @return List of book representations.
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
     * Helper method to assist in checking the values are within two
     * decimal places as the Double representations on MongoDB and Neo4j
     * are not equal due to precision.
     * @param priceOne The first MongoDB OR Neo4j double to compare.
     * @param priceTwo The first MongoDB OR Neo4j double to compare.
     * @return True if as 2 decimal place prices they are correct, false otherwise.
     */
    private boolean pricesAreEqual(Double priceOne, Double priceTwo) {
        BigDecimal scaledPriceOne = new BigDecimal(priceOne);
        BigDecimal scaledPriceTwo = new BigDecimal(priceTwo);
        scaledPriceOne = scaledPriceOne.setScale(2, BigDecimal.ROUND_DOWN);
        scaledPriceTwo = scaledPriceTwo.setScale(2, BigDecimal.ROUND_DOWN);
        return scaledPriceOne.equals(scaledPriceTwo);
    }

    /**
     * Attempts to checkout all of the items in the cart, either all of the items
     * checkout out and the MongoDB document is updated following, or nothing
     * succeeds.
     *
     * Neo4j is updated first and the status of this update is effectively persisted
     * into the MongoDB document with an atomic write on the document. If this
     * does not succeed, assuming the neo4j transaction is so far valid, then
     * the whole transaction is succeeded.
     *
     * Nothing should be possible to invalidate the transaction as write locks
     * are attached to the Nodes for the stock so the case of the success
     * transaction failing is a serious situation and would point to an issue
     * that lies beyond the bounds of this application.
     *
     * @return True if the checkout was a success, false otherwise.
     */
    public boolean checkout(String username, BasicDBObject creditcard, BasicDBObject address) {

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
            Map<String, BasicDBObject> isbnToQuantity = new HashMap<String, BasicDBObject>();
            BasicDBList requestedBooks = (BasicDBList) this.getMongo("items");
            for (int i = 0; i < requestedBooks.size(); i++) {
                BasicDBObject book = (BasicDBObject) requestedBooks.get(i);
                isbnToQuantity.put(book.getString("isbn"), book);
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
            Double total = 0.0;
            Iterator<Node> bookNodes = executionResult.columnAs("book");
            for (Node bookNode : IteratorUtil.asIterable(bookNodes)) {
                locks.add(tx.acquireWriteLock(bookNode));
                System.out.println("Found node " + bookNode);
                System.out.println(bookNode.getPropertyKeys());

                // Check how much the user wants, and what we have available.
                String isbn = bookNode.getProperty("isbn").toString();
                BasicDBObject bookRecord = isbnToQuantity.get(isbn);

                // Get the information the user assumed, and the information
                // that is true now.
                Double cartedPrice = bookRecord.getDouble("price");
                Double currentPrice = (Double) bookNode.getProperty("price");
                Integer requiredQuantity = bookRecord.getInt("quantity");
                Integer availableQuantity = (Integer) bookNode.getProperty("stock");

                System.out.println("Purchasing " + requiredQuantity + " from " + isbn + " with " + availableQuantity + "in stock.");

                // Update the total cost of the transcation
                total += currentPrice * requiredQuantity;

                // If one book is available force the transaction to fail.
                if (requiredQuantity > availableQuantity) {
                    result = false;
                    addMessage(MessageType.ERROR, requiredQuantity + " of '" + bookNode.getProperty("title").toString() + "' no longer available. Only " + availableQuantity + "left!");

                // Make sure that the price has not changed on the user since the the time they started checking out and now.
                } else if (!pricesAreEqual(cartedPrice, currentPrice)) {
                    result = false;
                    addMessage(MessageType.ERROR, "'" + bookNode.getProperty("title").toString() + "' is no longer available for " + cartedPrice + ", it is now " + currentPrice);

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
                updateFields.put("creditcard", creditcard);
                updateFields.put("address", address);
                updateFields.put("total", total);
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

package models;

import com.mongodb.*;
import org.bson.types.ObjectId;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.UniqueFactory;
import utils.Record;
import utils.mongodb.MongoDatabaseConnection;
import utils.neo4j.Neo4jDatabaseConnection;

import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author Jack Galile (430395187)
 *
 * Handles all of the polyglot database logic for dealing with the assignment
 * requirements for Users.
 *
 */
public class User extends Record<User> {

    private static final String USER_COLLECTION = "users";

    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabaseConnection = null;
    private static DBCollection mongoUsersDatabase = null;

    private static volatile User instance = null;

    /*
     * Relationships for the User record in Neo4j.
     */
    public static enum RELATIONSHIPS implements RelationshipType {
        BUYS
    }

    /**
     * Constructs a new User record from the provided MongoDB document.
     * @param record The mongodb document to construct the new User record
     *               from.
     */
    protected User(DBObject record) {
        super(record);
    }

    /**
     * Constructs the User singleton instance.
     * @throws UnknownHostException If there was an error connecting to the
     * MongoDB server.
     */
    protected User() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.mongoUsersDatabase = mongoDatabase.getCollection(USER_COLLECTION);
        this.neo4jDatabaseConnection = Neo4jDatabaseConnection.getInstance();
    }

    /**
     * Gets or creates the singleton instance for the User.
     * @return The singleton instance for User records.
     * @throws UnknownHostException
     */
    public static User getInstance() throws UnknownHostException {
        if (instance == null) {
            instance = new User();
        }
        return instance;
    }

    /**
     * Returns the Users MongoDB collection
     * @return Users MongoDB collection
     */
    @Override
    public DBCollection getMongoCollection() {
        return this.mongoUsersDatabase;
    }

    /**
     * Constructs a User record from the provided MongoDB document.
     * @param record MongoDB document representing the user.
     * @return The new User record for the provided MongoDB document.
     */
    @Override
    public User fromMongoRecord(DBObject record) {
        return new User(record);
    }

    /**
     * Create a new user in the users collection with the provided username,
     * password, firstname, and lastname.
     * @param username Username desired for registering the user with.
     * @param password Password to associate with the account.
     * @param firstName Fisrt name of the user to be registered.
     * @param lastName Last name of the user to be registered.
     * @return The newly created user in the MongoDB users collection.
     */
    public static User registerWith(String username, String password, String firstName, String lastName) {
        GraphDatabaseService graphDB = Neo4jDatabaseConnection.getInstance().getService();
        Transaction tx = graphDB.beginTx();
        User user = null;
        try {
            // User
            User instance = User.getInstance();

            // Neo4j
            Index<Node> usersIndex = graphDB.index().forNodes("Users");
            UniqueFactory<Node> userFactory = new UniqueFactory.UniqueNodeFactory(usersIndex) {
                @Override
                protected void initialize(Node created, Map<String, Object> properties) {
                    created.setProperty("username", properties.get("username"));
                }
            };

            // Attempt to create a new user node, but also check that the user
            // does not already exist.
            UniqueFactory.UniqueEntity<Node> userEntity = userFactory.getOrCreateWithOutcome("username", username);
            Node userNode = userEntity.entity();

            // Check that the user did not exist.
            if (userEntity.wasCreated()) {

                // MongoDB
                BasicDBObject registration = new BasicDBObject();
                registration.put("firstname", firstName);
                registration.put("lastname", lastName);
                registration.put("username", username);
                registration.put("password", password);
                registration.put("address", new BasicDBObject());
                registration.put("creditcard", new BasicDBObject());

                // Check that the user was created in MongoDB as well, otherwise
                // abort the creation in Neo4j also.
                WriteResult recordWritten = getInstance().getMongoCollection().insert(registration);
                CommandResult isRecordWritten = recordWritten.getLastError();
                if (null == isRecordWritten.get("err")) {
                    user = instance.fromMongoRecord(registration);
                    tx.success();
                } else {
                    System.out.println("User " + username + " already exists in MongoDB");
                    tx.failure();
                }

            // Since the user exists abort the transaction.
            } else {
                System.out.println("User " + username + " already exists in Neo4j");
                tx.failure();
            }

        // If anything goes wrong make sure that the transaction is aborted.
        } catch (Exception e) {
            e.printStackTrace();
            tx.failure();
        } finally {
            tx.finish();
            return user;
        }
    }

    /**
     * Finds a user only by their username. This is valid because there is
     * a unique constraint against the username.
     * @param username Username of the user to find in the users colleciton.
     * @return User record that represents the MongoDB document for the user
     * with the same username.
     */
    public static User findByUsername(String username) {
        User result = null;
        try {
            BasicDBObject userQuery = new BasicDBObject("username", username);
            User instance = getInstance();
            DBObject record = instance.getMongoCollection().findOne(userQuery);
            System.out.println("Found user " + record.toString());
            if (null != record) {
                result = instance.fromMongoRecord(record);
            }
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     * Finds a user with the given username and password combination.
     * @param username Username of the user to find.
     * @param password Password of the user to find.
     * @return User that represents the MongoDB document that has been found
     * with the given username and password parameters.
     */
    public static User findByUsernameAndPassword(String username, String password) {
        User result = null;
        try {
            BasicDBObject userQuery = new BasicDBObject();
            userQuery.put("username", username);
            userQuery.put("password", password);
            User instance = getInstance();
            DBObject record = instance.getMongoCollection().findOne(userQuery);
            if (null != record) {
                result = instance.fromMongoRecord(record);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     * Creates a list of recommendations for users based on the books
     * that they have bought and the books that other users have bought.
     * @return List of books that are recommended for the user and can
     * be rendered to them.
     */
    public List<Book> recommendedBooks() {
        String query = "START user=node:Users(username={username})\n" +
                "MATCH (user)-[:BUYS]->(book)<-[:BUYS]-(other)\n" +
                "WHERE HAS(book.isbn) AND HAS(other.username) AND user <> other\n" +
                "WITH user, other\n" +
                "MATCH (other)-[:BUYS]->(recommendation)\n" +
                "WHERE NOT ((user)-[:BUYS]->(recommendation))\n" +
                "AND HAS (recommendation.isbn)\n" +
                "WITH distinct(recommendation) AS recommendations\n" +
                "RETURN recommendations";
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("username", getMongo("username").toString());
        return Book.queryToNodes(query, params, "recommendations");
    }

    /**
     * Updates the address and credit card information for the user by adding
     * it to their document in MongoDB.
     * @param creditcard The credit card document to embed with the user document.
     * @param address The address document to embed with the user document.
     * @return True if the update was a success, false otherwise.
     */
    public boolean updateCreditcardAndAddress(BasicDBObject creditcard, BasicDBObject address) {
        try {
            User instance = User.getInstance();
            BasicDBObject updateFields = new BasicDBObject();
            updateFields.append("address", address);
            updateFields.append("creditcard", creditcard);
            BasicDBObject updateQuery = new BasicDBObject("$set", updateFields);
            ObjectId oid = new ObjectId(getMongo("_id").toString());
            BasicDBObject findQuery = new BasicDBObject("_id", oid);
            DBCollection collection = instance.getMongoCollection();
            WriteResult recordWritten = collection.update(findQuery, updateQuery);
            CommandResult isRecordWritten = recordWritten.getLastError();
            if (null == isRecordWritten.get("err")) {
                return true;
            } else {
                return false;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
}

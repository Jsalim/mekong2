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

public class User extends Record<User> {

    private static final String USER_COLLECTION = "users";

    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabaseConnection = null;
    private static DBCollection mongoUsersDatabase = null;

    private static User instance = null;

    public static enum RELATIONSHIPS implements RelationshipType {
        BUYS
    }

    /**
     *
     * @param record
     */
    protected User(DBObject record) {
        super(record);
    }

    /**
     *
     * @throws UnknownHostException
     */
    protected User() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.mongoUsersDatabase = mongoDatabase.getCollection(USER_COLLECTION);
        this.neo4jDatabaseConnection = Neo4jDatabaseConnection.getInstance();
    }

    /**
     *
     * @return
     * @throws UnknownHostException
     */
    public static User getInstance() throws UnknownHostException {
        if (instance == null) {
            instance = new User();
        }
        return instance;
    }

    @Override
    public DBCollection getMongoCollection() {
        return this.mongoUsersDatabase;
    }

    @Override
    public User fromMongoRecord(DBObject record) {
        return new User(record);
    }

    /**
     *
     * @param username
     * @param password
     * @return
     */
    public static User registerWith(String username, String password) {
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
     *
     * @param username
     * @return
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
     *
     * @param username
     * @param password
     * @return
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
     *
     * @return
     */
    public List<Book> recommendedBooks() {
        String query = "START book=node(*) WHERE HAS(book.isbn) RETURN book";
        Map<String, Object> params = new HashMap<String, Object>();
        return Book.queryToNodes(query, params, "book");
    }

    /**
     *
     * @param address
     * @return
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

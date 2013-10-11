package utils.mongodb;

import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import com.mongodb.MongoClient;
import com.mongodb.util.JSON;
import utils.DatabaseConnection;
import utils.DatabaseType;

import java.net.UnknownHostException;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Singelton class for maintaining a single connection variable to the MongoDB
 * database.
 *
 */
public class MongoDatabaseConnection extends DatabaseConnection {

    private static final String HOST_ADDRESS = "localhost";
    private static final int HOST_PORT = 27017;
    private static final String DATABASE = "mekong";

    private static MongoDatabaseConnection instance = null;

    private final MongoClient mongoClient;
    private final DB mongoDB;

    /**
     * Creates the instance of the singleton and establishes the appropriate
     * connection with the MongoDB database and the appropriate port.
     * @throws UnknownHostException
     */
    protected MongoDatabaseConnection() throws UnknownHostException {
        this.mongoClient = new MongoClient(HOST_ADDRESS, HOST_PORT);
        this.mongoDB = this.mongoClient.getDB(DATABASE);
    }

    /**
     * Returns the singleton instance for the MongoDB database connection.
     * @return
     * @throws UnknownHostException It may not be possible to connect to the
     * MongoDB instance, for whatever reason. It is assumed that MongoDB is
     * running on the default port for this case.
     */
    public static MongoDatabaseConnection getInstance() throws UnknownHostException {
        if (null == instance) {
            instance = new MongoDatabaseConnection();
        }
        return instance;
    }

    /**
     * Returns the collection in the database that is associated with the provided
     * string.
     * @param collection Name of the collection in the MongoDB instance.
     * @return Collection for the string provided.
     */
    public DBCollection getCollection(String collection) {
        return this.mongoDB.getCollection(collection);
    }

    /**
     * Get the database connection to MongoDB
     * @return
     */
    public DB getDB() {
      return this.mongoDB;
    }

    /**
     * Drop the entire database in MongoDB
     */
    public void dropDB() {
      mongoClient.dropDatabase(DATABASE);
    }

    /**
     * Since it is a MongoDB conneciton it is going to return the MongoDB
     * database type.
     * @return The MongoDB database type enumerable.
     */
    public DatabaseType getDatabaseType() {
        return DatabaseType.MONGODB;
    }

}

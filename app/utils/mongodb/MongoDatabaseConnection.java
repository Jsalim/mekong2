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
 * Created with IntelliJ IDEA.
 * User: jgalilee
 * Date: 9/3/13
 * Time: 12:59 AM
 * To change this template use File | Settings | File Templates.
 */
public class MongoDatabaseConnection extends DatabaseConnection {

    private static final String DATABASE = "mekong";
    private static MongoDatabaseConnection instance = null;

    private final MongoClient mongoClient;
    private final DB mongoDB;

    protected MongoDatabaseConnection() throws UnknownHostException {
        this.mongoClient = new MongoClient("localhost" , 27017);
        this.mongoDB = this.mongoClient.getDB(DATABASE);
    }

    public static MongoDatabaseConnection getInstance() throws UnknownHostException {
        if (null == instance) {
            instance = new MongoDatabaseConnection();
        }
        return instance;
    }

    public DBCollection getCollection(String collection) {
        return this.mongoDB.getCollection(collection);
    }

    public DB getDB() {
      return this.mongoDB;
    }

    public void dropDB()
    {
      mongoClient.dropDatabase(DATABASE);
    }

    public DatabaseType getDatabaseType() {
        return DatabaseType.MONGODB;
    }

}

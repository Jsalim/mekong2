package models;

import com.mongodb.DBCollection;
import com.mongodb.DBObject;
import utils.Record;
import utils.mongodb.MongoDatabaseConnection;
import utils.neo4j.Neo4jDatabaseConnection;

import java.net.UnknownHostException;

public class User extends Record<User> {

    private static final String USER_COLLECTION = "users";

    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabaseConnection = null;
    private static DBCollection mongoUsersDatabase = null;

    private static User instance = null;

    protected User(DBObject record) {
        super(record);
    }

    protected User() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.mongoUsersDatabase = mongoDatabase.getCollection(USER_COLLECTION);
        this.neo4jDatabaseConnection = Neo4jDatabaseConnection.getInstance();
    }

    public static User getInstance() throws UnknownHostException {
        if (instance == null) {
            instance = new User();
        }
        return instance;
    }

    public static User getModel() {
      try {
        return User.getInstance();
      } catch (Exception e) {
        System.out.println("Failed to get instance" + e.toString());
        return null;
      }
    }

    @Override
    public DBCollection getMongoCollection() {
        return this.mongoUsersDatabase;
    }

    @Override
    public User fromMongoRecord(DBObject record) {
        return new User(record);
    }

}

import play.*;

import com.mongodb.MongoClient;
import com.mongodb.MongoException;
import com.mongodb.WriteConcern;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.BasicDBObject;
import com.mongodb.DBObject;
import com.mongodb.DBCursor;
import com.mongodb.ServerAddress;

import java.net.UnknownHostException;

import models.User;
import models.Cart;
import models.Book;

import utils.neo4j.Neo4jDatabaseConnection;
import utils.seeds.*;

public class Global extends GlobalSettings {

    public static User users;
    public static Cart carts;
    public static Book books;

    public void onStart(Application app) {

      Logger.info("Starting seeds ... ");
      try {
        Seeder seeder = new Seeder("seeds/");
        seeder.run("seeds.xml");
        Logger.info("Finishing seeds ... ");
      } catch (Exception e) {
        Logger.info("Failed to seed ... " + e.toString());
      }

      // Setup the database connections by default.
      try {
        System.out.println("");
        users = User.getInstance();
        carts = Cart.getInstance();
        books = Book.getInstance();
      } catch (Exception e) {
        Logger.info("Unable to access MongoDB OR Neo4j");
      }
      Logger.info("Application has started");
      Neo4jDatabaseConnection.getInstance().printDatabase();
      return;
    }

    public void onStop(Application app) {
        Logger.info("Application shutdown...");
    }

}

package models;

import com.mongodb.*;
import utils.Record;
import utils.mongodb.MongoDatabaseConnection;
import utils.neo4j.Neo4jDatabaseConnection;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

import java.net.UnknownHostException;

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

    public static Cart getModel() {
      try {
        return Cart.getInstance();
      } catch (Exception e) {
        System.out.println("Failed to get instance" + e.toString());
        return null;
      }
    }

    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(BOOK_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    public Transaction getNeo4jTransaction() {
      if (this.neo4jDatabase == null) {
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
      }
      return this.neo4jDatabase.getService().beginTx();
    }

    public ExecutionResult executeNeo4jQuery(String query) {
      ExecutionEngine exe = new ExecutionEngine(this.neo4jDatabase.getService());
      return exe.execute(query);
    }

    @Override
    public Cart fromMongoRecord(DBObject record) {
        return new Cart(record);
    }

}

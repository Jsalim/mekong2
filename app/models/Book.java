package models;

import com.mongodb.*;
import org.neo4j.graphdb.RelationshipType;
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

public class Book extends Record<Book> {

    private static final String BOOK_COLLECTION = "books";
    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;
    private static volatile Book instance = null;

    public static enum RELATIONSHIPS implements RelationshipType
    {
        WRITTEN_BY,
        ABOUT
    }

    protected Book(DBObject record) {
        super(record);
    }

    protected Book() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
    }

    public static Book getInstance() throws UnknownHostException {
        if (instance == null) {
           instance = new Book();
        }
        return instance;
    }

    public static Book getModel() {
      try {
        return Book.getInstance();
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
    public Book fromMongoRecord(DBObject record) {
        return new Book(record);
    }

}

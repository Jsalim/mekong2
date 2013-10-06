package models;

import com.mongodb.*;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
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
import java.util.*;
import java.util.regex.Pattern;

public class Book extends Record<Book> {

    private static final String BOOK_COLLECTION = "books";
    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;
    private static volatile Book instance = null;

    public static enum RELATIONSHIPS implements RelationshipType {
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

    public static Book findByISBN(String isbn) {
        Book result = null;
        try {
            Book instance = Book.getInstance();
            BasicDBObject isbnQuery = new BasicDBObject("isbn", isbn);
            DBObject record = instance.getMongoCollection().findOne(isbnQuery);
            result = instance.fromMongoRecord(record);
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    public static Map<String, Object> all(Integer pageSize, Integer page) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("records", new ArrayList<Book>());
        try {
            Book instance = getInstance();
            DBCursor allFoundBooks = instance.getMongoCollection().find();
            result.put("page", page);
            result.put("pages", allFoundBooks.count() / pageSize);
            allFoundBooks = allFoundBooks.skip(pageSize * page).limit(pageSize);
            ((List) result.get("records")).addAll(instance.fromMongoRecord(allFoundBooks));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    public static Map<String, Object> searchByTitleAndISBN(String query, Integer pageSize, Integer page) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("records", new ArrayList<Book>());
        try {
            Book instance = Book.getInstance();
            List<BasicDBObject> search = new ArrayList<BasicDBObject>();
            if (null != query && 0 < query.length()) {
                Pattern compiledQuery = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
                BasicDBObject isbnQuery = new BasicDBObject("isbn", compiledQuery);
                BasicDBObject titleQuery = new BasicDBObject("title", compiledQuery);
                search.add(isbnQuery);
                search.add(titleQuery);
            }
            BasicDBObject searchQuery = new BasicDBObject("$or", search.toArray());
            DBCursor allFoundBooks = instance.getMongoCollection().find(searchQuery);
            result.put("page", page);
            result.put("pages", allFoundBooks.count() / pageSize);
            allFoundBooks = allFoundBooks.skip(pageSize * page - 1).limit(pageSize);
            ((List) result.get("records")).addAll(instance.fromMongoRecord(allFoundBooks));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     *
     * @param isbn
     * @return
     */
    public static GridFSDBFile findCoverByISBN(String isbn) {
        GridFSDBFile result = null;
        try {
            BasicDBObject isbnQueryPortion = new BasicDBObject("$in", Arrays.asList(isbn));
            BasicDBObject query = new BasicDBObject("aliases", isbnQueryPortion);
            DB db = MongoDatabaseConnection.getInstance().getDB();
            GridFS gfsCovers = new GridFS(db, "covers");
            List<GridFSDBFile> files = gfsCovers.find(query);
            result = files.get(0);
        } catch (UnknownHostException e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

}

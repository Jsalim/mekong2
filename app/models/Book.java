package models;

import com.mongodb.*;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import org.neo4j.graphdb.RelationshipType;
import org.neo4j.helpers.collection.IteratorUtil;
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

/**
 *
 */
public class Book extends Record<Book> {

    private static final String BOOK_COLLECTION = "books";
    private static MongoDatabaseConnection mongoDatabase = null;
    private static Neo4jDatabaseConnection neo4jDatabase = null;
    private static DBCollection mongoBooksDatabase = null;
    private static volatile Book instance = null;

    // Cache controls to prevent constant Neo4j queries.
    private Integer stockCache = null;

    public static enum RELATIONSHIPS implements RelationshipType {
        WRITTEN_BY,
        ABOUT
    }

    protected Book(Node node) {
        super(node);
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

    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(BOOK_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    /**
     *
     * @param record
     * @return
     */
    @Override
    public Book fromMongoRecord(DBObject record) {
        return new Book(record);
    }

    /**
     *
     * @param query
     * @param pageSize
     * @param page
     * @return
     */
    private static Map<String, Object> paginateQuery(BasicDBObject query, Integer pageSize, Integer page) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("records", new ArrayList<Book>());
        try {
            Book instance = getInstance();
            DBCursor allFoundBooks = instance.getMongoCollection().find(query);
            Integer pages = allFoundBooks.count() / pageSize;
            if (pages > page) {
                page = pages - 1;
            } else if (page < 0) {
                page = 0;
            }
            Integer pageSkip = pageSize * page;
            allFoundBooks = allFoundBooks.skip(pageSkip).limit(pageSize);
            result.put("page", page);
            result.put("pages", pages);
            ((List) result.get("records")).addAll(instance.fromMongoRecord(allFoundBooks));
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     *
     * @param query
     * @param params
     * @param column
     * @return
     */
    public static List<Book> queryToNodes(String query, Map<String, Object> params, String column) {
        List<Book> result = new ArrayList<Book>();
        GraphDatabaseService graphDB = neo4jDatabase.getService();
        Transaction tx = graphDB.beginTx();
        try {
            ExecutionEngine queryExecuter = new ExecutionEngine(graphDB);
            ExecutionResult executionResult = queryExecuter.execute(query, params);
            Iterator<Node> bookNodes = executionResult.columnAs(column);
            for (Node bookNode : IteratorUtil.asIterable(bookNodes)) {
                Book book = new Book(bookNode);
                result.add(book);
            }
            tx.success();
        } catch (Exception e) {
            tx.failure();
            e.printStackTrace();
        } finally {
            tx.finish();
            return result;
        }
    }

    public List<R> queryNeo4jRecord() {
      String query = "START book=node:Books(isbn={isbn}) RETURN book LIMIT 1";
      Map<String, Object> params = new HashMap<String, Object>();
      String isbn = getMongo("isbn");
      if (null != isbn) {
        return Book.queryToNodes(query, params, "book");
      } else {
        return new ArrayList<Book>();
      }
    }

    /**
     *
     * @param pageSize
     * @param page
     * @return
     */
    public static Map<String, Object> all(Integer pageSize, Integer page) {
        return paginateQuery(new BasicDBObject(), pageSize, page);
    }

    /**
     *
     * @param isbn
     * @return
     */
    public static Book findByISBN(String isbn) {
        Book result = null;
        try {
            System.out.println("Finding by isbn " + isbn);
            Book instance = Book.getInstance();
            BasicDBObject isbnQuery = new BasicDBObject("isbn", isbn);
            DBObject record = instance.getMongoCollection().findOne(isbnQuery);
            result = instance.fromMongoRecord(record);
        } catch (Exception e) {
            System.out.println("Failed to find by isbn " + isbn);
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     *
     * @param rawQuery
     * @param pageSize
     * @param page
     * @return
     */
    public static Map<String, Object> searchByTitleAndISBN(String rawQuery, Integer pageSize, Integer page) {
        List<BasicDBObject> search = new ArrayList<BasicDBObject>();
        Pattern compiledQuery = Pattern.compile(rawQuery, Pattern.CASE_INSENSITIVE);
        BasicDBObject isbnQuery = new BasicDBObject("isbn", compiledQuery);
        BasicDBObject titleQuery = new BasicDBObject("title", compiledQuery);
        search.add(isbnQuery);
        search.add(titleQuery);
        BasicDBObject query = new BasicDBObject("$or", search.toArray());
        return paginateQuery(query, pageSize, page);
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

    /**
     *
     * @return
     */
    public List<Book> similarBooks() {
        String query = "START n=node:Books(isbn = {isbn}), b=node(*)\n" +
                "MATCH n-[:SIMILAR_TO]->b\n" +
                "RETURN b";
        Map<String, Object> params = new HashMap<String, Object>();
        params.put("isbn", String.valueOf(getMongo("isbn")));
        return queryToNodes(query, params, "b");
    }

    /**
     *
     * @return
     */
    public Integer getStock() {
        if (null == stockCache) {
            if (!neo4jRecordLoaded()) {
                loadNeo4jRecord();
            }
            stockCache = Integer.valueOf(getNeo4j("stock").toString());
        }
        return stockCache;
    }

    /**
     *
     * @return
     */
    public String getByLine() {
        StringBuilder sb = new StringBuilder();
        BasicDBList authors = (BasicDBList) getMongoRecord().get("authors");
        for (int i = 0; i < authors.size(); i++) {
            BasicDBObject author = (BasicDBObject) authors.get(i);
            sb.append(author.getString("firstname"));
            sb.append(" ");
            sb.append(author.getString("lastname"));
            if (i != authors.size() - 1) {
                sb.append(", ");
            }
        }
        return sb.toString();
    }

}

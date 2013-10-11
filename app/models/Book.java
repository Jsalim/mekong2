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
 * @author Jack Galile (430395187)
 *
 * Handles all of the polyglot database logic for dealing with the assignment
 * requirements for Books.
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

    /**
     *
     */
    public static enum RELATIONSHIPS implements RelationshipType {
        WRITTEN_BY,
        ABOUT
    }

    /**
     * Constructs a new Book that wraps a Neo4j node.
     * @param node
     */
    protected Book(Node node) {
        super(node);
    }

    /**
     * Constructs a new Book that wraps a MongoDB document.
     * @param record
     */
    protected Book(DBObject record) {
        super(record);
    }

    /**
     * Constructs the singleton instance of the Book record.
     * @throws UnknownHostException
     */
    protected Book() throws UnknownHostException {
        super();
        this.mongoDatabase = MongoDatabaseConnection.getInstance();
        this.neo4jDatabase = Neo4jDatabaseConnection.getInstance();
    }

    /**
     * Creates or returns the singleton instance for the Books class.
     * @return Returns the singleton instance after it has been constructed
     * or after it has been found.
     * @throws UnknownHostException This exception occurs if the MonogDB
     * connection was not successful.
     */
    public static Book getInstance() throws UnknownHostException {
        if (instance == null) {
           instance = new Book();
        }
        return instance;
    }

    /**
     * Returns the collection which is used to store all of the information
     * about books in MongoDB.
     * @return The DBCollection for the Books.
     */
    @Override
    public DBCollection getMongoCollection() {
        if (this.mongoBooksDatabase == null) {
          this.mongoBooksDatabase = mongoDatabase.getCollection(BOOK_COLLECTION);
        }
        return this.mongoBooksDatabase;
    }

    /**
     * Constructs a Book record from a provided MongoDB document object.
     * @param record The MongoDB document object to convert into a Book.
     * @return The Book wrapping the provided MongoDB document.
     */
    @Override
    public Book fromMongoRecord(DBObject record) {
        return new Book(record);
    }

    /**
     * Paginates a query against the collection using the information that is
     * provided. It also handles creating a map of the pagination information and
     * correcting invalid page values to either the max page if it exceeds the
     * number of pages, or the min page if it is less than the min page number.
     * @param query The query to paginate.
     * @param pageSize The size of the pages.
     * @param page The page to return.
     * @return Map which includes both the record and the pagination information
     * so that the calling method is aware of how the records can be paginated.
     */
    private static Map<String, Object> paginateQuery(BasicDBObject query, Integer pageSize, Integer page) {
        Map<String, Object> result = new HashMap<String, Object>();
        result.put("records", new ArrayList<Book>());
        try {
            Book instance = getInstance();
            DBCursor allFoundBooks = instance.getMongoCollection().find(query);
            allFoundBooks.sort(new BasicDBObject("title", 1));
            Integer pages = allFoundBooks.count() / pageSize;
            if (page > pages) {
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
     * Given a query, some params and the appropriate column it constructs
     * a list of the books from the column that it is given.
     * @param query CYPHER query to execute
     * @param params Parameters for the parameter interpolation engine.
     * @param column Column to return after the query has been executed.
     * @return The result of the books being constructed from the returned nodes
     * in the column.
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

    /**
     * Creates a query to find the appropriate node that is associated with
     * the ISBN key.
     * @return The Neo4j record that has the ISBN for the Books index.
     */
    public Book queryNeo4jRecord() {
      String query = "START book=node:Books(isbn={isbn}) RETURN book LIMIT 1";
      Map<String, Object> params = new HashMap<String, Object>();
      String isbn = String.valueOf(getMongo("isbn"));
      params.put("isbn", isbn);
      if (null != isbn) {
        List<Book> books = Book.queryToNodes(query, params, "book");
        if (books.size() > 0) {
            return books.get(0);
        }
      }
      return null;
    }

    /**
     * Finds all of the records for the current page in the collection, this
     * collection is ordered by title.
     * @param pageSize Size of the pages that are being filtered through
     * @param page Page from the filter that is desired.
     * @return Paginated set of results for the books collection using the
     * find all query ordered by title.
     */
    public static Map<String, Object> all(Integer pageSize, Integer page) {
        return paginateQuery(new BasicDBObject(), pageSize, page);
    }

    /**
     * Finds the Book in the collection by the appropriate ISBN constructs
     * the Book record for the found MongoDB document and returns the record.
     * @param isbn ISBN to find the Book for.
     * @return The Book record which wraps the returned MonogDB document.
     */
    public static Book findByISBN(String isbn) {
        Book result = null;
        try {
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
     * Searches the books collection using the title and isbn of all the books.
     * It attempts to match the query that is provided by converting it into
     * a case insenstive regular expression, and then subsequently paginating
     * the results so that they can be displayed more easily to the user.
     * @param rawQuery The query to use for searching by title and ISBN.
     * @param pageSize The size of the pages the information should be returned
     *                 as, this is dependent on the calling method.
     * @param page The page that the pagination should start from this is handled
     *             by the pagination method.
     * @return The resulting map of isbns to their books.
     */
    public static Map<String, Object> searchByTitleAndISBN(String rawQuery, Integer pageSize, Integer page) {
        List<BasicDBObject> search = new ArrayList<BasicDBObject>();
        Pattern compiledQuery = Pattern.compile(Pattern.quote(rawQuery), Pattern.CASE_INSENSITIVE);
        BasicDBObject isbnQuery = new BasicDBObject("isbn", compiledQuery);
        BasicDBObject titleQuery = new BasicDBObject("title", compiledQuery);
        search.add(isbnQuery);
        search.add(titleQuery);
        BasicDBObject query = new BasicDBObject("$or", search.toArray());
        return paginateQuery(query, pageSize, page);
    }

    /**
     * Returns the GridFS file that has the ISBN for the book in its list of
     * aliases.
     * @param isbn ISBN of the book to find the cover for.
     * @return GridFS file returned from the query.
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
            System.out.println("Failed to find  GridFS cover image for " + isbn);
            e.printStackTrace();
        } finally {
            return result;
        }
    }

    /**
     * Finds all the books have an outgoing similarity as found in the RDF files
     * that were used to construct the query. It has been chosen as outgoing
     * because it was not found in the RDF files to have a bidirectional link.
     * A -> B did not mean B -> A, where -> represents a similarity relationship.
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
     * Finds the associated Neo4j record, based on the books isbn. It then checks
     * the stock property on the node item and returns the result to the user.
     * @return The stock level for the book as it is currently in Neo4j.
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
     * Compiles the list of authors in a comma seperated string.
     * @return The comma seperated string of authors names.
     */
    public String getByLine() {
        StringBuilder sb = new StringBuilder();
        BasicDBList authors = (BasicDBList) getMongoRecord().get("authors");
        if (authors != null) {
            for (int i = 0; i < authors.size(); i++) {
                BasicDBObject author = (BasicDBObject) authors.get(i);
                sb.append(author.getString("firstname"));
                sb.append(" ");
                sb.append(author.getString("lastname"));
                if (i != authors.size() - 1) {
                    sb.append(", ");
                }
            }
        }
        return sb.toString();
    }

}

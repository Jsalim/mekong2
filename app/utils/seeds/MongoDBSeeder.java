package utils.seeds;

import com.mongodb.*;
import com.mongodb.gridfs.GridFSDBFile;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import utils.mongodb.MongoDatabaseConnection;

import java.io.IOException;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Assists in handling all of the information that will be persisted and handled
 * on the MongoDB database for the book records.
 *
 */
public class MongoDBSeeder {

    GridFSSeeder gridFsSeeder = null;
    MongoDatabaseConnection connection = null;

    /**
     * Constructs the seeder referencing the connection to MongoDB, and also
     * to the associated GridFS seeder for handling the cover information.
     * @param connection MongoDB connection instance.
     * @param gfsSeeder GridFS seeder instance.
     */
    public MongoDBSeeder(MongoDatabaseConnection connection, GridFSSeeder gfsSeeder) {
        this.connection = connection;
        this.gridFsSeeder = gfsSeeder;
    }

    /**
     * Creates the MongoDB connection and the constraints against the connections
     * @return True if everything worked correctly, false otherwise.
     */
    public boolean createCollections() {
        DB db = this.connection.getDB();

        // Create the collections for the documents.
        DBCollection users = db.getCollection("users");
        DBCollection books = db.getCollection("books");
        DBCollection carts = db.getCollection("carts");

        // Create the indexes on the document collections.
        BasicDBObject uniqueConstraint = new BasicDBObject("unique", true);

        // Create the users collection indexes
        BasicDBObject usernameIndexQuery = new BasicDBObject("username", 1);
        users.ensureIndex(usernameIndexQuery, uniqueConstraint);

        // Create the books collection indexes
        BasicDBObject isbnIndexQuery = new BasicDBObject("isbn", 1);
        books.ensureIndex(isbnIndexQuery, uniqueConstraint);

        // Create the carts collection indexes
        BasicDBObject cartOwnerIndexQuery = new BasicDBObject("username", 1);
        carts.ensureIndex(cartOwnerIndexQuery);

        return true;
    }

    /**
     * Forces the JSON object into an array of items so that it can be
     * handled correctly.
     * @param force The JSON object to be forced into a JSONArray
     * @return The forced result of the JSONObject as a JSONArray.
     */
    private JSONArray forceJsonArray(Object force) {
        JSONArray forced = null;
        if (force instanceof JSONArray) {
            forced = (JSONArray) force;
        } else {
            forced = new JSONArray();
            forced.put(force);
        }
        return forced;
    }

    /**
     * Extracts a list of similar ISBN numbers from the JSON object.
     * @param jsonSimilar
     * @return List of similar book isbns
     * @throws JSONException
     */
    private BasicDBList createSimilar(JSONArray jsonSimilar) throws JSONException {
        System.out.println("Reading similar " + jsonSimilar.toString());
        BasicDBList similar = new BasicDBList();
        for (int i = 0; i < jsonSimilar.length(); i++) {
            String isbn = String.valueOf(jsonSimilar.get(i));
            similar.add(isbn);
        }
        return similar;
    }

    /**
     * Extracts a list of subjects from the JSON object
     * @param jsonSubjects JSON list of subjects
     * @return The list of JSON subjects.
     * @throws JSONException
     */
    private BasicDBList createSubjects(JSONArray jsonSubjects) throws JSONException {
        BasicDBList subjects = new BasicDBList();
        for (int i = 0; i < jsonSubjects.length(); i++) {
            String jsonSubject = String.valueOf(jsonSubjects.get(i));
            subjects.add(jsonSubject);
        }
        return subjects;
    }

    /**
     * Extracts a list of Authors from the original JSON objec.t
     * @param jsonAuthors List of authors for the book/
     * @return The list of authors.
     * @throws JSONException
     */
    private BasicDBList createAuthors(JSONArray jsonAuthors) throws JSONException {
        BasicDBList authors = new BasicDBList();
        for (int i = 0; i < jsonAuthors.length(); i++) {
            JSONObject jsonAuthor = jsonAuthors.getJSONObject(i);
            BasicDBObject author = new BasicDBObject();
            author.put("firstname", jsonAuthor.get("firstname"));
            author.put("lastname", jsonAuthor.get("lastname"));
            authors.add(author);
        }
        return authors;
    }

    /**
     * Creates the schema correct version of the document from the JSON
     * object which has originated from the parsing XML.
     * @param book JSONObject that contains all of the information about the
     *             book.
     * @return The new MongoDB document which has not yet been persisted.
     */
     public BasicDBObject createBookRecord(JSONObject book) {
        BasicDBObject newBookRecordQuery = new BasicDBObject();
        try {
            newBookRecordQuery.put("isbn", book.getString("isbn"));
            newBookRecordQuery.put("title", book.getString("title"));
            newBookRecordQuery.put("cover", book.getString("cover"));
            newBookRecordQuery.put("price", book.getDouble("price"));
            newBookRecordQuery.put("description", book.getString("description"));
            newBookRecordQuery.put("stock",  book.getInt("stock"));

            try {
                Object rawAuthors = book.getJSONObject("authors").get("author");
                JSONArray jsonAuthors = forceJsonArray(rawAuthors);
                BasicDBList authors = createAuthors(jsonAuthors);
                newBookRecordQuery.put("authors", authors);
            } catch (Exception e) {
                System.out.println("Authors missing");
            }

            try {
                Object rawSubjects = book.getJSONObject("subjects").get("subject");
                JSONArray jsonSubjects = forceJsonArray(rawSubjects);
                BasicDBList subjects = createSubjects(jsonSubjects);
                newBookRecordQuery.put("subjects", subjects);
            } catch (Exception e) {
                System.out.println("Subjects missing");
            }

            try {
                Object rawSimilar = book.getJSONObject("similar").get("isbn");
                JSONArray jsonSimilar = forceJsonArray(rawSimilar);
                BasicDBList similar = createSimilar(jsonSimilar);
                newBookRecordQuery.put("similar", similar);
            } catch (Exception e) {
                System.out.println("Similar  missing");
            }

        } catch (JSONException je) {
            System.out.println("JSONException " + je.toString());
        } catch (Exception e) {
            System.out.println("Exception " + e.toString());
        } finally {
            return newBookRecordQuery;
        }
    }

    /**
     * Creates the new Book document and returns the result of the mutation
     * applied to the object by the MongoDB document insertion method.
     * @param newBookRecordQuery
     * @return The result of the insertion as the original mutated document.
     * @throws IOException
     */
    public DBObject insertBookRecord(BasicDBObject newBookRecordQuery) throws IOException {
        String isbn = newBookRecordQuery.getString("isbn");
        String cover = newBookRecordQuery.getString("cover");
        GridFSDBFile coverImage = gridFsSeeder.createGridFSImageRecord(cover, isbn);
        newBookRecordQuery.put("cover", coverImage.get("filename"));

        DB mekongDB = this.connection.getDB();
        DBCollection books = mekongDB.getCollection("books");
        WriteResult recordWritten = books.insert(newBookRecordQuery);
        CommandResult isRecordWritten = recordWritten.getLastError();
        if (null != isRecordWritten.get("err")) {
          System.out.println("Unable to create book record");
          return null;
        }
        return newBookRecordQuery;
    }

    /**
     * Makes any changes that have been done when the MongoDB document has
     * been going through Neo4j related methods.
     * @param book The book document to be updated.
     * @return True if the update was successful, false otherwise.
     */
    public boolean updateRecord(BasicDBObject book) {
        DB mekongDB = this.connection.getDB();
        DBCollection books = mekongDB.getCollection("books");
        ObjectId oid = new ObjectId(book.getString("_id"));
        try {
            WriteResult recordWritten = books.update(new BasicDBObject("_id", oid), book);
            CommandResult isRecordWritten = recordWritten.getLastError();
            if (null != isRecordWritten.get("err")) {
                System.out.println("Unable to update record");
                return false;
            } else {
                System.out.println("Updated mongo record with neo4j");
                return true;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

}

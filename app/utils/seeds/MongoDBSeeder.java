package utils.seeds;

import com.mongodb.*;
import com.mongodb.gridfs.GridFSDBFile;
import org.bson.types.ObjectId;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import utils.mongodb.MongoDatabaseConnection;

import java.io.IOException;

public class MongoDBSeeder
{

  GridFSSeeder gridFsSeeder = null;
  MongoDatabaseConnection connection = null;

  public MongoDBSeeder(MongoDatabaseConnection connection, GridFSSeeder gfsSeeder)
  {
    this.connection = connection;
    this.gridFsSeeder = gfsSeeder;
  }

  public boolean createCollections()
  {
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

  private JSONArray forceJsonArray(Object force)
  {
      JSONArray forced = null;
      if (force instanceof JSONArray)
      {
        forced = (JSONArray) force;
      }
      else
      {
        forced = new JSONArray();
        forced.put(force);
      }
      return forced;
  }

  private BasicDBList createSimilar(JSONArray jsonSimilar)
  throws JSONException
  {
    System.out.println("Reading similar " + jsonSimilar.toString());
    BasicDBList similar = new BasicDBList();
    for (int i = 0; i < jsonSimilar.length(); i++)
    {
      String isbn = String.valueOf(jsonSimilar.get(i));
      similar.add(isbn);
    }
    return similar;
  }

  private BasicDBList createSubjects(JSONArray jsonSubjects)
  throws JSONException
  {
    //System.out.println("Reading subjects " + jsonSubjects.toString());
    BasicDBList subjects = new BasicDBList();
    for (int i = 0; i < jsonSubjects.length(); i++)
    {
        String jsonSubject = String.valueOf(jsonSubjects.get(i));
        subjects.add(jsonSubject);
    }
    return subjects;
  }

  private BasicDBList createAuthors(JSONArray jsonAuthors)
  throws JSONException
  {
    BasicDBList authors = new BasicDBList();
    for (int i = 0; i < jsonAuthors.length(); i++)
    {
      JSONObject jsonAuthor = jsonAuthors.getJSONObject(i);
      BasicDBObject author = new BasicDBObject();
      author.put("firstname", jsonAuthor.get("firstname"));
      author.put("lastname", jsonAuthor.get("lastname"));
      authors.add(author);
    }
    return authors;
  }

  public BasicDBObject createBookRecord(JSONObject book)
  {
    //System.out.println("Received JSON Object");
    //System.out.println(book.toString());
    BasicDBObject newBookRecordQuery = new BasicDBObject();
    try
    {
      //System.out.println("Parsing attributes");
      newBookRecordQuery.put("isbn", book.getString("isbn"));
      newBookRecordQuery.put("title", book.getString("title"));
      newBookRecordQuery.put("cover", book.getString("cover"));
      newBookRecordQuery.put("price", book.getDouble("price"));
      newBookRecordQuery.put("description", book.getString("description"));
      newBookRecordQuery.put("stock",  book.getInt("stock"));

      //System.out.println("Parsing authors");
        try {
          Object rawAuthors = book.getJSONObject("authors").get("author");
          JSONArray jsonAuthors = forceJsonArray(rawAuthors);
          BasicDBList authors = createAuthors(jsonAuthors);
          newBookRecordQuery.put("authors", authors);
        } catch (Exception e) {
            System.out.println("Subjects missing");
        }

      //System.out.println("Parsing subjects");
      try {
          Object rawSubjects = book.getJSONObject("subjects").get("subject");
          JSONArray jsonSubjects = forceJsonArray(rawSubjects);
          BasicDBList subjects = createSubjects(jsonSubjects);
          newBookRecordQuery.put("subjects", subjects);
      } catch (Exception e) {
          System.out.println("Subjects missing");
      }

      //System.out.println("Parsing similar");
        try {
          Object rawSimilar = book.getJSONObject("similar").get("isbn");
          JSONArray jsonSimilar = forceJsonArray(rawSimilar);
          BasicDBList similar = createSimilar(jsonSimilar);
          newBookRecordQuery.put("similar", similar);
        } catch (Exception e) {
            System.out.println("Similar  missing");
        }

    }
    catch (JSONException je)
    {
       System.out.println("JSONException " + je.toString());
    }
    catch (Exception e)
    {
       System.out.println("Exception " + e.toString());
    }
    finally
    {
      return newBookRecordQuery;
    }
  }

    public DBObject insertBookRecord(BasicDBObject newBookRecordQuery) throws IOException {
        //System.out.println("Inserting BSON Object");
        //System.out.println(newBookRecordQuery.toString());

        //System.out.println("Parsing cover");
        String isbn = newBookRecordQuery.getString("isbn");
        String cover = newBookRecordQuery.getString("cover");
        //System.out.println("Download and uploading " + isbn + " cover image " + cover);
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
        //System.out.println("Created mongo record");
        //System.out.println(recordWritten.toString());
        //System.out.println();
        return newBookRecordQuery;
    }

    public DBObject retrieveBookRecord(String isbn) {
        DB mekongDB = this.connection.getDB();
        DBCollection books = mekongDB.getCollection("books");
        BasicDBObject isbnQuery = new BasicDBObject("isbn", isbn);
        DBObject bookRecord = books.findOne(isbnQuery);
        return bookRecord;
    }

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

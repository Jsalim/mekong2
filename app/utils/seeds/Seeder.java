package utils.seeds;

import java.io.*;

import java.net.*;
import java.util.ArrayList;
import java.util.List;

import org.json.*;

import com.mongodb.*;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Transaction;
import utils.mongodb.*;
import utils.neo4j.Neo4jDatabaseConnection;

public class Seeder {

   private String folder;

   private MongoDatabaseConnection mongoConnection = null;
   private Neo4jDatabaseConnection neo4jConnection = null;
   private GridFSSeeder gridfsSeeder = null;
   private MongoDBSeeder mongoSeeder = null;
   private Neo4jSeeder neo4jSeeder = null;

   private File lock = null;

   public Seeder(String folder) throws UnknownHostException {
     lock = new File(folder + "seeds.lock");
     this.folder = folder;
   }

   private String xmlDocument(String filename) throws IOException {
     FileReader fileReader = new FileReader(folder + filename);
     BufferedReader bufferedReader = new BufferedReader(fileReader);
     String xmlContent;
     try
     {
       StringBuilder contentBuilder = new StringBuilder();
       String line = bufferedReader.readLine();
       while (line != null)
       {
         contentBuilder.append(line);
         contentBuilder.append('\n');
         line = bufferedReader.readLine();
       }
       xmlContent = contentBuilder.toString();
     }
     finally
     {
       bufferedReader.close();
     }
     return xmlContent;
   }

   public void createLock() {
       try {
           lock.createNewFile();
       } catch (IOException e) {
           System.out.println("Failed to create lock");
           e.printStackTrace();
       }
   }

   public boolean lockExists() {
       return lock.exists();
   }

   public void run(String filename) throws IOException, JSONException {
     if (!lockExists()) {
       System.out.println("Getting MongoDB Seeder");
       this.mongoConnection = MongoDatabaseConnection.getInstance();
       this.gridfsSeeder = new GridFSSeeder(mongoConnection, folder);
       this.mongoSeeder = new MongoDBSeeder(mongoConnection, gridfsSeeder);
       System.out.println("Getting Neo4j Seeder");
       this.neo4jConnection = Neo4jDatabaseConnection.getInstance();
       this.neo4jSeeder = new Neo4jSeeder(neo4jConnection);
       mongoConnection.dropDB();
       neo4jConnection.dropDB();
       mongoSeeder.createCollections();
       neo4jSeeder.setupDatabase();
       JSONObject books = XML.toJSONObject(xmlDocument(filename));
       JSONArray bookList = books.getJSONObject("books").getJSONArray("book");
       List<BasicDBObject> seeded = new ArrayList<BasicDBObject>();
       for (int i = 0; i < bookList.length(); i++) {
         BasicDBObject seed = createSeed(bookList.getJSONObject(i));
         if (null != seed) {
            seeded.add(seed);
         }
       }

       System.out.println("Connecting seeds");
       for (BasicDBObject seed : seeded) {
           System.out.println("Connecting");
           cleanSeed(seed);
       }
       System.out.println("Finished connecting seeds");
       createLock();
       neo4jConnection.printDatabase();
     } else {
       System.out.println("Already seeded, seeds.lock exists. Skipping seeds!");
     }
   }

  private BasicDBObject createSeed(JSONObject book) throws JSONException, IOException
  {
    BasicDBObject mongoBookRecord = null;
    String isbn = String.valueOf(book.get("isbn"));
    //System.out.println("\n\n###################################################");
    System.out.println("Seeding ... " + isbn);
    try
    {
        System.out.println("\n\n# MongoDB\n");
        mongoBookRecord = mongoSeeder.createBookRecord(book);
        mongoSeeder.insertBookRecord(mongoBookRecord);

        System.out.println("\n\n# Neo4j\n");
        Long nodeId = neo4jSeeder.enhanceBookRecord(mongoBookRecord);
        mongoBookRecord.put("_node", nodeId);
        mongoBookRecord.remove("stock");
    }
    catch (Exception e)
    {
      System.out.println("Failed to create seed for " + isbn + ", reason " + e.toString());
    }
    finally
    {
      System.out.println("Seeded " + isbn);
      //System.out.println("###################################################\n\n");
      return mongoBookRecord;
    }
  }

  public void cleanSeed(BasicDBObject book)
  {
      if (null != book.get("_id")) {
        neo4jSeeder.createSimilarToRelationships(book);
        mongoSeeder.updateRecord(book);
      }
  }

}

package utils.seeds;

import java.io.*;
import java.util.*;

import java.net.*;

import javax.imageio.*;
import java.awt.image.BufferedImage;

import org.json.*;

import models.*;

import com.mongodb.*;
import utils.mongodb.*;
import com.mongodb.gridfs.*;

public class Seeder {

   private String folder;

   private MongoDatabaseConnection connection = null;
   private GridFSSeeder gridfsSeeder = null;
   private MongoDBSeeder mongoSeeder = null;

   public Seeder(String folder)
   throws UnknownHostException
   {
     this.folder = folder;
     this.connection = MongoDatabaseConnection.getInstance();
     this.gridfsSeeder = new GridFSSeeder(connection, folder);
     this.mongoSeeder = new MongoDBSeeder(connection, gridfsSeeder);
   }

   private String xmlDocument(String filename)
   throws FileNotFoundException, IOException
   {
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

   public void run(String filename)
   throws FileNotFoundException, IOException, JSONException, MalformedURLException
   {
     File seedLock = new File(folder + "seeds.lock");
     if (!seedLock.exists())
     {
       connection.dropDB();
       mongoSeeder.createCollections();
       JSONObject books = XML.toJSONObject(xmlDocument(filename));
       JSONArray bookList = books.getJSONObject("books").getJSONArray("book");
       for (int pass = 0; pass < 2; pass++)
       {
         for (int i = 0; i < bookList.length(); i++)
         {
           if (0 == pass)
           {
             createSeed(bookList.getJSONObject(i));
           }
           else
           {
             cleanSeed(bookList.getJSONObject(i));
           }
         }
       }
//       seedLock.createNewFile();
     }
     else
     {
       System.out.println("Already seeded, seeds.lock exists. Skipping seeds!");
     }
   }

  private void createSeed(JSONObject book)
  throws JSONException, IOException
  {
    String isbn = String.valueOf(book.get("isbn"));
    System.out.println("Seeding ... " + isbn);
    // neo4jSeeder.createBookRecord(book);
    mongoSeeder.createBookRecord(book);
    System.out.println("Seeded " + isbn);
    System.out.println();
    return;
  }

  public void cleanSeed(JSONObject book)
  {

  }

}

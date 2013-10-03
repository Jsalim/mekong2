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

   private Book books;
   private User users;

   public Seeder(String folder)
   {
     books = Book.getModel();
     users = User.getModel();
     if (null == folder)
     {
       folder = "";
     }
     this.folder = folder;
   }

   private String xmlDocument(String filename) throws FileNotFoundException, IOException
   {
     FileReader fr = new FileReader(folder + filename);
     BufferedReader br = new BufferedReader(fr);
     String xmlContent;
     try
     {
       StringBuilder sb = new StringBuilder();
       String line = br.readLine();
       while (line != null)
       {
         sb.append(line);
         sb.append('\n');
         line = br.readLine();
       }
       xmlContent = sb.toString();
     }
     finally
     {
       br.close();
     }
     return xmlContent;
   }

   public void run(String filename) throws FileNotFoundException, IOException, JSONException, MalformedURLException
   {
     File seedLock = new File(folder + "seeds.lock");
     if (!seedLock.exists())
     {
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
             // cleanSeed(bookList.getJSONObject(i));
           }
         }
       }
       seedLock.createNewFile();
     }
     else
     {
       System.out.println("Already seeded, seeds.lock exists. Skipping seeds!");
     }
   }

   private void createSeed(JSONObject book) throws JSONException, MalformedURLException, IOException {
     DBObject bookRecord = (DBObject) com.mongodb.util.JSON.parse(book.toString());
     String isbn = String.valueOf(bookRecord.get("isbn"));
     System.out.println("Seeding ... " + isbn);
     try {
       // Create the Neo4j information.
       // - If it fails abort the transaction

       // Create the MongoDB document.
       // - If it fails abort the transaction, this will roll back the Neo4j.
       // DBObject newBookRecord = MongoDBSeeder.createRecord(book);
       // books.getMongoCollection().insert(newBookRecord);

       // Download, open, and store the cover image in MongoDB.
       // - If it fails don't abort the transaction.
       String coverUrl = String.valueOf(bookRecord.get("cover"));
       GridFSDBFile coverImage = MongoDBSeeder.createGridFSImageRecord(folder, coverUrl, isbn);
     } catch (MalformedURLException malformedUrlException) {
       System.out.println("Failed to load image for " + isbn + ", MalformedURLException");
     } catch (IOException ioException) {
       System.out.println("Failed to load image for " + isbn + ", IOException");
     }
     System.out.println("Seeded " + isbn);
     System.out.println();
     return;
   }
}

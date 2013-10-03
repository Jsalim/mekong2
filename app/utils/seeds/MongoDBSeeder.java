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

public class MongoDBSeeder {

  public static boolean setupMongoDBCollections() {

    return true;
  }

  public static boolean setupMongoDBIndexes() {

    return true;
  }

  public static DBObject createMongoRecord(JSONObject book) {
    return null;
  }

  public static File download(String folder, String requestUrl) throws MalformedURLException, IOException
  {
    BufferedImage image = null;
    URL inputUrl = new URL(requestUrl);
    String outputFileName = folder + requestUrl.substring(requestUrl.lastIndexOf('/') + 1);
    File outputFile = new File(outputFileName);
    if (!outputFile.exists())
    {
        System.out.println("Downloading " + inputUrl + " and writing to " + outputFileName);
        image = ImageIO.read(inputUrl);
        ImageIO.write(image, "GIF", outputFile);
    }
    else
    {
        System.out.println("File " + outputFileName + " already exists, skipping.");
    }
    return outputFile;
  }

  public static GridFSDBFile imageAlreadyOnGridFS(File file) throws UnknownHostException {
    DB db = MongoDatabaseConnection.getInstance().getDB();
    GridFS gfsCovers = new GridFS(db, "covers");
    List<GridFSDBFile> files = gfsCovers.find(file.getName());
    if (files.size() > 0) {
      return files.get(0);
    } else {
      return null;
    }
  }

  public static GridFSInputFile uploadGridFSImage(File file, List<String> aliases) throws UnknownHostException, IOException {
    DB db = MongoDatabaseConnection.getInstance().getDB();
    GridFS gfsCovers = new GridFS(db, "covers");
    GridFSInputFile gfsFile = gfsCovers.createFile(file);
    gfsFile.setFilename(file.getName());
    gfsFile.put("aliases", aliases);
    gfsFile.save();
    System.out.println("Inserted gridfs file " + gfsFile.toString());
    return gfsFile;
  }

  public static GridFSDBFile createGridFSImageRecord(String folder, String url, String isbn) throws MalformedURLException, IOException {
    File coverImage = download(folder, url);
    GridFSDBFile gfsCoverImage = imageAlreadyOnGridFS(coverImage);
    if (null == gfsCoverImage)
    {
      List<String> aliases = Arrays.asList();
      System.out.println("Creating gridfs image for " + coverImage.getName() + " aliases " + aliases.toString());
      uploadGridFSImage(coverImage, aliases);
    }
    else
    {
      List<String> aliases = (List<String>) gfsCoverImage.get("aliases");
      if (!aliases.contains(isbn))
      {
        aliases.add(isbn);
      }
      gfsCoverImage.put("aliases", aliases);
      gfsCoverImage.save();
      System.out.println("File already existed in GridFS added " + isbn + " to alias.");
      System.out.println(gfsCoverImage.toString());
    }
    return gfsCoverImage;
  }

}

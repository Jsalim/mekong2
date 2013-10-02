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

  public static GridFSDBFile createGridFSImageRecord(String url) throws MalformedURLException {
    File coverImage = download(url);
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
  }

}

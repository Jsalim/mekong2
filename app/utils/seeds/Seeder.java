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

  public Seeder(String folder) {
    books = Book.getModel();
    users = User.getModel();
    if (null == folder) {
      folder = "";
    }
    this.folder = folder;
  }

  private String xmlDocument(String filename) throws FileNotFoundException, IOException {
    FileReader fr = new FileReader(folder + filename);
    BufferedReader br = new BufferedReader(fr);
    String xmlContent;
    try {
        StringBuilder sb = new StringBuilder();
        String line = br.readLine();
        while (line != null) {
            sb.append(line);
            sb.append('\n');
            line = br.readLine();
        }
        xmlContent = sb.toString();
    } finally {
        br.close();
    }
    return xmlContent;
  }

  public GridFSDBFile isGridFSImage(File file) throws UnknownHostException {
    DB db = MongoDatabaseConnection.getInstance().getDB();
    GridFS gfsCovers = new GridFS(db, "covers");
    List<GridFSDBFile> files = gfsCovers.find(file.getName());
    if (files.size() > 0) {
      return files.get(0);
    } else {
      return null;
    }
  }

  public GridFSInputFile createGridFsImage(File file, List<String> aliases) throws UnknownHostException, IOException {
    DB db = MongoDatabaseConnection.getInstance().getDB();
    GridFS gfsCovers = new GridFS(db, "covers");
    GridFSInputFile gfsFile = gfsCovers.createFile(file);
    gfsFile.setFilename(file.getName());
    gfsFile.put("aliases", aliases);
    gfsFile.save();
    System.out.println("Inserted gridfs file " + gfsFile.toString());
    return gfsFile;
  }

  public File download(String requestUrl) throws MalformedURLException, IOException {
    BufferedImage image = null;
    URL inputUrl = new URL(requestUrl);
    String outputFileName = folder + requestUrl.substring(requestUrl.lastIndexOf('/') + 1);
    File outputFile = new File(outputFileName);
    if (!outputFile.exists()) {
      System.out.println("Downloading " + inputUrl + " and writing to " + outputFileName);
      image = ImageIO.read(inputUrl);
      ImageIO.write(image, "GIF", outputFile);
    } else {
      System.out.println("File " + outputFileName + " already exists, skipping.");
    }
    return outputFile;
  }

  public void run(String filename) throws FileNotFoundException, IOException, JSONException, MalformedURLException {
    File seedLock = new File(folder + "seeds.lock");
    if (!seedLock.exists()) {
      JSONObject books = XML.toJSONObject(xmlDocument(filename));
      JSONArray bookList = books.getJSONObject("books").getJSONArray("book");
      for (int pass = 0; pass < 2; pass++) {
        for (int i = 0; i < bookList.length(); i++) {
          create(bookList.getJSONObject(i), pass);
        }
      }
      seedLock.createNewFile();
    } else {
      System.out.println("Already seeded, seeds.lock exists. Skipping seeds!");
    }
  }

  private void create(JSONObject book, int pass) throws JSONException, MalformedURLException, IOException {
    DBObject bookRecord = (DBObject) com.mongodb.util.JSON.parse(book.toString());
    String isbn = String.valueOf(bookRecord.get("isbn"));
    System.out.println("Seeding ... " + isbn);
    try {
      File coverImage = download(String.valueOf(bookRecord.get("cover")));
      GridFSDBFile gfsCoverImage = isGridFSImage(coverImage);
      if (null == gfsCoverImage) {
        List<String> aliases = Arrays.asList();
        System.out.println("Creating gridfs image for " + coverImage.getName() + " aliases " + aliases.toString());
        createGridFsImage(coverImage, aliases);
      } else {
        List<String> aliases = (List<String>) gfsCoverImage.get("aliases");
        if (!aliases.contains(isbn)) {
          aliases.add(isbn);
        }
        gfsCoverImage.put("aliases", aliases);
        gfsCoverImage.save();
        System.out.println("File already existed in GridFS added " + isbn + " to alias.");
        System.out.println(gfsCoverImage.toString());
      }
    } catch (MalformedURLException mfue) {
      System.out.println("Failed to load image for " + isbn + ", MalformedURLException");
    } catch (IOException e) {
      System.out.println("Failed to load image for " + isbn + ", IOException");
    }
    books.getMongoCollection().insert(bookRecord);
    System.out.println("Seeded " + isbn);
    System.out.println();
  }

}

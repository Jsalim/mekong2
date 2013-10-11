package utils.seeds;

import com.mongodb.BasicDBList;
import com.mongodb.gridfs.GridFS;
import com.mongodb.gridfs.GridFSDBFile;
import com.mongodb.gridfs.GridFSInputFile;
import utils.mongodb.MongoDatabaseConnection;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.List;

public class GridFSSeeder
{

  private String folder = null;
  private MongoDatabaseConnection connection = null;

  public GridFSSeeder(MongoDatabaseConnection connection, String folder)
  {
    this.folder = folder;
    this.connection = connection;
  }

  public File download(String requestUrl)
  throws IOException
  {
    BufferedImage image;
    URL inputUrl = new URL(requestUrl);
    String filename = requestUrl.substring(requestUrl.lastIndexOf('/') + 1);
    String outputFileName = folder + filename;
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

  public GridFSDBFile imageAlreadyOnGridFS(File file)
  throws UnknownHostException
  {
    GridFS gfsCovers = new GridFS(this.connection.getDB(), "covers");
    List<GridFSDBFile> files = gfsCovers.find(file.getName());
    if (files.size() > 0) {
      return files.get(0);
    } else {
      return null;
    }
  }

  public GridFSInputFile uploadGridFSImage(File file, BasicDBList aliases)
  throws IOException
  {
    GridFS gfsCovers = new GridFS(this.connection.getDB(), "covers");
    GridFSInputFile gfsFile = gfsCovers.createFile(file);
    gfsFile.setFilename(file.getName());
    gfsFile.put("aliases", aliases);
    gfsFile.save();
    //System.out.println("Inserted gridfs file " + gfsFile.toString());
    return gfsFile;
  }

  public GridFSDBFile createGridFSImageRecord(String url, String isbn)
  throws IOException
  {
    File coverImage = download(url);
    GridFSDBFile gfsCoverImage = imageAlreadyOnGridFS(coverImage);
    if (null == gfsCoverImage)
    {
      //System.out.println("Cover " + url + " does not exist, creating.");
      BasicDBList aliases = new BasicDBList();
      aliases.add(isbn);
      System.out.println("Creating gridfs image for " + coverImage.getName() + " aliases " + aliases.toString());
      uploadGridFSImage(coverImage, aliases);
      gfsCoverImage = imageAlreadyOnGridFS(coverImage);
    }
    else
    {
      System.out.println("Cover " + url + " exists creating association");
      BasicDBList aliases = (BasicDBList) gfsCoverImage.get("aliases");
      if (!aliases.contains(isbn))
      {
        aliases.add(isbn);
      }
      gfsCoverImage.put("aliases", aliases);
      gfsCoverImage.save();
      //System.out.println("Added " + isbn + " to " + url + " aliases.");
      //System.out.println(gfsCoverImage.toString());
    }
    return gfsCoverImage;
  }

}

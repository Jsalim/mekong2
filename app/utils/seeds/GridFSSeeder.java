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

/**
 * @author Jack Galilee (430395187)
 *
 * Seeding class that performs the task of downloading and inserting the books
 * covers into the MongoDB GridFS store.
 *
 */
public class GridFSSeeder {

    private String folder = null;
    private MongoDatabaseConnection connection = null;

    /**
     * Constructs the GridFS seeder setting the folder that all of the
     * images are going to be loaded into, and setting the connection
     * for the GridFS system.
     * @param connection
     * @param folder
     */
    public GridFSSeeder(MongoDatabaseConnection connection, String folder) {
        this.folder = folder;
        this.connection = connection;
    }

    /**
     * Downloads the file from the internet, writes it to the file system
     * and returns the result of the write.
     * @param requestUrl The URI to download
     * @return The file after it has ben returned
     * @throws IOException Thrown is there was an issue writing the file out.
     */
    public File download(String requestUrl) throws IOException {
        BufferedImage image;
        URL inputUrl = new URL(requestUrl);
        String filename = requestUrl.substring(requestUrl.lastIndexOf('/') + 1);
        String outputFileName = folder + filename;
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

    /**
     * Check if the file already exists on GridFS
     * @param file File to check.
     * @return The GridFS file if it exists, null otherwise.
     * @throws UnknownHostException If there was an issue in dealing with GridFS
     */
    public GridFSDBFile imageAlreadyOnGridFS(File file) throws UnknownHostException {
        GridFS gfsCovers = new GridFS(this.connection.getDB(), "covers");
        List<GridFSDBFile> files = gfsCovers.find(file.getName());
        if (files.size() > 0) {
            return files.get(0);
        } else {
        return null;
        }
    }

    /**
     *
     * @param file The file to upload to GridFS.
     * @param aliases The aliasses to apply to the GridFS file.
     * @return The resulting GridFS file after being uploaded.
     * @throws IOException If there was an issue in reading the file.
     */
    public GridFSInputFile uploadGridFSImage(File file, BasicDBList aliases) throws IOException {
        GridFS gfsCovers = new GridFS(this.connection.getDB(), "covers");
        GridFSInputFile gfsFile = gfsCovers.createFile(file);
        gfsFile.setFilename(file.getName());
        gfsFile.put("aliases", aliases);
        gfsFile.save();
        System.out.println("Inserted gridfs file " + gfsFile.toString());
        return gfsFile;
    }

    /**
     * Creates or gets the GridFS file and adds the ISBN to the list of aliases
     * so that it can be easily retrieved.
     * @param url URI for the file to download.
     * @param isbn The ISBN to append to the aliases of the file.
     * @return The GridFS representation of the file.
     * @throws IOException If there was an issue in writing the file out to
     * disc.
     */
    public GridFSDBFile createGridFSImageRecord(String url, String isbn) throws IOException {
        File coverImage = download(url);
        GridFSDBFile gfsCoverImage = imageAlreadyOnGridFS(coverImage);
        if (null == gfsCoverImage) {
            System.out.println("Cover " + url + " does not exist, creating.");
            BasicDBList aliases = new BasicDBList();
            aliases.add(isbn);
            System.out.println("Creating gridfs image for " + coverImage.getName() + " aliases " + aliases.toString());
            uploadGridFSImage(coverImage, aliases);
            gfsCoverImage = imageAlreadyOnGridFS(coverImage);
        } else {
            System.out.println("Cover " + url + " exists creating association");
            BasicDBList aliases = (BasicDBList) gfsCoverImage.get("aliases");
            if (!aliases.contains(isbn)) {
                aliases.add(isbn);
            }
            gfsCoverImage.put("aliases", aliases);
            gfsCoverImage.save();
            System.out.println("Added " + isbn + " to " + url + " aliases.");
            System.out.println(gfsCoverImage.toString());
        }
        return gfsCoverImage;
    }

}

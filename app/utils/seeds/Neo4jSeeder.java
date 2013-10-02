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


public class Neo4jSeeder {

  // private boolean setupNeo4jIndexes() {
  //   return true;
  // }

  // private void createNeo4j(JSONObject book) {

  //   //
  //   // -------------------------------------------------------------------------
  //   // # 1. CREATE THE BOOK IF NOT EXISTS OR ABORT OTHERWISE
  //   // -------------------------------------------------------------------------
  //   // CREATE UNIQUE (book {isbn: '1234'})
  //   // -------------------------------------------------------------------------
  //   // # 1.1. For each author create the author nodes if it does not exist
  //   // -------------------------------------------------------------------------
  //   // START author=node:Author(name="{AuthorName}")
  //   // WITH COUNT(*) AS number
  //   // WHERE number = 0
  //   // CREATE (author { name: '{AuthorName}' })
  //   // RETURN author
  //   // -------------------------------------------------------------------------
  //   // # 1.2. Create all of the written by relationships for the book
  //   // -------------------------------------------------------------------------
  //   // START book=node(*), author=node(*)
  //   // WHERE book.isbn! = {isbn} AND author.name! IN {author_list}
  //   // CREATE UNIQUE book-[written_by:WRITTEN_BY]->author
  //   // RETURN book, written_by, author
  //   // -------------------------------------------------------------------------
  //   // # 1.3. For each subject create the subject node if it does not exist
  //   // -------------------------------------------------------------------------
  //   // START author=node:subject(name="{Subjectname}")
  //   // WITH COUNT(*) AS number
  //   // WHERE number = 0
  //   // CREATE (subject { name: '{SubjectName}' })
  //   // RETURN author
  //   // -------------------------------------------------------------------------
  //   // # 1.4. Create all of the about relationships for the book
  //   // -------------------------------------------------------------------------
  //   // START book=node:Book(isbn = {isbn}), subject=node(*)
  //   // WHERE subject.name! IN {subject_list}
  //   // CREATE UNIQUE book-[about:ABOUT]->subject
  //   // RETURN book, about, subject
  //   // -------------------------------------------------------------------------
  //   //

  //   // Create a query to add the node to the database.
  //   String query = "CREATE (book {isbn:{isbn}})";
  //   Map<String, Object> parameters = new HashMap<String, Object>();
  //   parameters.put("isbn", book.get("isbn"));

  //   // Create a connection with the Neo4j database.
  //   GraphDatabaseService db = Neo4jDatabaseConnection.getInstance().getService();
  //   ExecutionEngine engine = new ExecutionEngine(db);
  //   engine.execute(query);

  //   // Start a transaction to index all of the books that have been created.
  //   Transaction tx = db.beginTx();
  //   try
  //   {
  //     Iterable<Node> allNodes = GlobalGraphOperations.at(db).getAllNodes();
  //     for (Node node : allNodes)
  //     {
  //       if (node.hasProperty("isbn"))
  //       {
  //         db.index().forNodes("book").add(node, "isbn", node.getProperty("isbn"));
  //       }
  //     }
  //   }
  //   // If something went wrong with the transactions cancel it, return nothing.
  //   catch (Exception e)
  //   {
  //     return null;
  //   }
  //   // If the transaction was OK return the result and close it.
  //   finally
  //   {
  //     tx.finish();
  //     return tx;
  //   }
  // }

}

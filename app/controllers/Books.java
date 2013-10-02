package controllers;

import play.*;
import play.mvc.*;
import play.data.Form;
import play.data.DynamicForm;

import views.html.*;

import com.mongodb.*;
import utils.mongodb.*;
import com.mongodb.gridfs.*;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;

import java.net.UnknownHostException;
import java.util.*;
import java.util.regex.Pattern;

import models.Book;

@Security.Authenticated(Secured.class)
public class Books extends Controller {

    private static Integer PAGE_SIZE = 3;

    public static Result show(String isbn)
    {
      Book books = Book.getModel();
      BasicDBObject byIsbn = new BasicDBObject("isbn", Long.valueOf(isbn));
      DBObject mgBook = books.getMongoCollection().findOne(byIsbn);
      Book book = books.fromMongoRecord(mgBook);

      if (null == book)
      {
        return notFound();
      }
      else
      {
        Logger.info("Found book ... " + book.toString());
        return ok(views.html.Books.show.render(book));
      }
    }

    public static Result index(Integer page)
    {
      DynamicForm requestData = Form.form().bindFromRequest();
      Book books = Book.getModel();
      DBCollection booksCollection = books.getMongoCollection();
      DBCursor allFoundBooks = booksCollection.find();
      Integer pages = allFoundBooks.count() / PAGE_SIZE;
      allFoundBooks = allFoundBooks.skip(PAGE_SIZE * page).limit(PAGE_SIZE);
      List<Book> result = books.fromMongoRecord(allFoundBooks);
      return ok(views.html.Books.index.render("All Books", result, page, pages, null));
    }

    public static Result searchForm()
    {
      DynamicForm requestData = Form.form().bindFromRequest();
      String query = requestData.get("query");
      Integer page = 1;
      if (null != requestData.get("page"))
      {
        page = Integer.valueOf(requestData.get("page"));
      }
      return search(query, page);
    }

    public static Result search(String query, Integer page)
    {
      List<BasicDBObject> search = new ArrayList<BasicDBObject>();
      if (null != query && 0 < query.length())
      {
        Pattern compiledQuery = Pattern.compile(query, Pattern.CASE_INSENSITIVE);
        BasicDBObject isbnQuery = new BasicDBObject("isbn", compiledQuery);
        BasicDBObject titleQuery = new BasicDBObject("title", compiledQuery);
        search.add(isbnQuery);
        search.add(titleQuery);
      }
      Book books = Book.getModel();
      BasicDBObject searchQuery = new BasicDBObject("$or", search.toArray());
      DBCursor allFoundBooks = books.getMongoCollection().find(searchQuery);
      Integer pages = allFoundBooks.count() / PAGE_SIZE;
      allFoundBooks = allFoundBooks.skip(PAGE_SIZE * page).limit(PAGE_SIZE);
      List<Book> results = books.fromMongoRecord(allFoundBooks);
      return ok(views.html.Books.index.render("Results for '" + query + "'", results, page, pages, query));
    }

    public static Result cover(String isbn)
    {
      try
      {
        BasicDBObject isbnQueryPortion = new BasicDBObject("$in", Arrays.asList(isbn));
        BasicDBObject query = new BasicDBObject("aliases", isbnQueryPortion);
        DB db = MongoDatabaseConnection.getInstance().getDB();
        GridFS gfsCovers = new GridFS(db, "covers");
        List<GridFSDBFile> files = gfsCovers.find(query);
        GridFSDBFile gfsCover = files.get(0);
        response().setContentType("image/gif");
        return ok(gfsCover.getInputStream());
      }
      catch (Exception e)
      {
        return notFound();
      }
    }

    public static Result recommendations()
    {
        Book books = Book.getModel();
        String username = session("username");
        String query =  "START user = node:User(username=\""+username+"\"),\n" +
                    "similar_user = node(*),\n" +
                    "other_book = node(*)\n" +
         "WHERE HAS (similar_user.username)\n" +
             "MATCH (user)-[*]->(book)<-[*]-(similar_user)\n" +
               "AND user <> similar_user\n" +
              "WITH user, similar_user\n" +
             "MATCH (similar_user)-[*]->(other_book)\n" +
         "WHERE NOT ((user)-[:CONDUCTS]->()-[:CONTAIN]->(other_book))\n" +
           "AND HAS (other_book.title)\n" +
            "RETURN distinct(other_book)";

        ExecutionResult results = books.executeNeo4jQuery(query);
        Iterator<Node> bookNodes = results.columnAs("n");
        while (bookNodes.hasNext())
        {
          Node bookNode = bookNodes.next();
          System.out.println("Kind #" + bookNode.getId());
          for (String propertyKey : bookNode.getPropertyKeys())
          {
            System.out.println("\t" + propertyKey + " : " + bookNode.getProperty(propertyKey));
          }
        }

        List<Book> result = new ArrayList<Book>();
        return ok(views.html.Books.index.render("Your recommendations", result, 1, 1, null));
    }

}

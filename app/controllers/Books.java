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

    public static Result show(String isbn) {
      Book book = Book.findByISBN(isbn);
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

    public static Result index(Integer page) {
      Map<String, Object> result = Book.all(PAGE_SIZE, page);
      List<Book> books = (List) result.get("records");
      Integer pages = (Integer) result.get("pages");
        System.out.printf("%s %s %s", result.toString(), books.toString(), pages.toString());
      return ok(views.html.Books.index.render("All Books", books, page, pages, null));
    }

    public static Result searchForm() {
      DynamicForm requestData = Form.form().bindFromRequest();
      String query = requestData.get("query");
      Integer page = 1;
      if (null != requestData.get("page"))
      {
        page = Integer.valueOf(requestData.get("page"));
      }
      return search(query, page);
    }

    public static Result search(String query, Integer page) {
      Map<String, Object> result = Book.searchByTitleAndISBN(query, PAGE_SIZE, page);
      List<Book> books = (List) result.get("records");
      Integer pages = (Integer) result.get("pages");
      return ok(views.html.Books.index.render("Results for '" + query + "'", books, page, pages, query));
    }

    public static Result cover(String isbn) {
      GridFSDBFile cover = Book.findCoverByISBN(isbn);
      response().setContentType("image/gif");
      if (null != cover) {
        return ok(cover.getInputStream());
      } else {
        return notFound();
      }
    }

    public static Result recommendations() {
        // Book books = Book.getModel();
        // String username = session("username");
        // String query =  "START user = node:User(username=\""+username+"\"),\n" +
        //             "similar_user = node(*),\n" +
        //             "other_book = node(*)\n" +
        //  "WHERE HAS (similar_user.username)\n" +
        //      "MATCH (user)-[*]->(book)<-[*]-(similar_user)\n" +
        //        "AND user <> similar_user\n" +
        //       "WITH user, similar_user\n" +
        //      "MATCH (similar_user)-[*]->(other_book)\n" +
        //  "WHERE NOT ((user)-[:CONDUCTS]->()-[:CONTAIN]->(other_book))\n" +
        //    "AND HAS (other_book.title)\n" +
        //     "RETURN distinct(other_book)";

        // ExecutionResult results = books.executeNeo4jQuery(query);
        // Iterator<Node> bookNodes = results.columnAs("n");
        // while (bookNodes.hasNext())
        // {
        //   Node bookNode = bookNodes.next();
        //   System.out.println("Kind #" + bookNode.getId());
        //   for (String propertyKey : bookNode.getPropertyKeys())
        //   {
        //     System.out.println("\t" + propertyKey + " : " + bookNode.getProperty(propertyKey));
        //   }
        // }

        List<Book> result = new ArrayList<Book>();
        return ok(views.html.Books.index.render("Recommended books", result, 1, 1, null));
    }

}

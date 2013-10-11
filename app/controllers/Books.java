package controllers;

import com.mongodb.gridfs.GridFSDBFile;
import models.Book;
import models.User;
import play.data.DynamicForm;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

import java.util.List;
import java.util.Map;

/**
 *
 * @author  Jack Galilee (430395187)
 *
 * Authenticated controller, meaning that the user must be logged in before they
 * are able to access this information.
 *
 * This primarily deals with the MongoDB instances of the books, except for the
 * show instance where it will use the Neo4j database for the fine details
 * that cannot simply be stored and cached within the MongoDB document.
 */
@Security.Authenticated(Secured.class)
public class Books extends Controller {

    private static Integer PAGE_SIZE = 10;

    /**
     * Returns a single book, and also loads the corresponding Neo4j record
     * for handling the stock and relational properties.
     * @param isbn Isbn of the book that is requested
     * @return HttpResponse with the result of the book in a html view.
     */
    public static Result show(String isbn) {
        Book book = Book.findByISBN(isbn);
        book.loadNeo4jRecord();
        List<Book> similarBooks = book.similarBooks();
        if (null == book) {
            return notFound();
        } else {
            return ok(views.html.Books.show.render(book, similarBooks));
        }
    }

    /**
     * This lists all of the books for the page, the default page size is 10.
     * @param page Page from the results to display, the default page size is 10
     * @return List of all of the books for the current page, defaulting to 0.
     */
    public static Result index(Integer page) {
        String username = session("username");
        User user = User.findByUsername(username);
        Map<String, Object> result = Book.all(PAGE_SIZE, page);
        List<Book> books = (List) result.get("records");
        List<Book> recommendations = user.recommendedBooks();
        page = (Integer) result.get("page");
        Integer pages = (Integer) result.get("pages");
        return ok(views.html.Books.index.render("All Books", books, recommendations, page, pages, null));
    }

    /**
     * Prepares and renders the search form, required by the routing framework.
     * @return Rendered form for searching the application.
     */
    public static Result searchForm() {
        DynamicForm requestData = Form.form().bindFromRequest();
        String query = requestData.get("query");
        Integer page = 1;
        if (null != requestData.get("page")) {
            page = Integer.valueOf(requestData.get("page"));
        }
        return search(query, page);
    }

    /**
     * Searches with a case insensitive regex against either the title or
     * the isbn of all the books in the database and returns the matches.
     * Matches are limited to ten per page.
     * @param query String to match against the title or isbn of all books.
     * @param page Page of the search to return, returns the max or min page if
     *             greater or smaller than either respective value.
     * @return Http result rendering the results.
     */
    public static Result search(String query, Integer page) {
        String username = session("username");
        User user = User.findByUsername(username);
        Map<String, Object> result = Book.searchByTitleAndISBN(query, PAGE_SIZE, page);
        List<Book> books = (List) result.get("records");
        List<Book> recommendations = user.recommendedBooks();
        page = (Integer) result.get("page");
        Integer pages = (Integer) result.get("pages");
        String title = "Results for '" + query + "'";
        return ok(views.html.Books.index.render(title, books, recommendations, page, pages, query));
    }

    /**
     * Queries GridFS for the cover image binary and sends it to the user.
     * @param isbn Match against the aliases of all files in the GridFS store.
     * @return Matched file, or nothing if not found.
     */
    public static Result cover(String isbn) {
        GridFSDBFile cover = Book.findCoverByISBN(isbn);
        response().setContentType("image/gif");
        if (null != cover) {
            return ok(cover.getInputStream());
        } else {
            return notFound();
        }
    }

}

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
 */
@Security.Authenticated(Secured.class)
public class Books extends Controller {

    private static Integer PAGE_SIZE = 3;

    /**
     *
     * @param isbn
     * @return
     */
    public static Result show(String isbn) {
        Book book = Book.findByISBN(isbn);
        List<Book> similarBooks = book.similarTo();
        if (null == book) {
            return notFound();
        } else {
            return ok(views.html.Books.show.render(book, similarBooks));
        }
    }

    /**
     *
     * @param page
     * @return
     */
    public static Result index(Integer page) {
        Map<String, Object> result = Book.all(PAGE_SIZE, page);
        List<Book> books = (List) result.get("records");
        page = (Integer) result.get("page");
        Integer pages = (Integer) result.get("pages");
        return ok(views.html.Books.index.render("All Books", books, page, pages, null));
    }

    /**
     *
     * @return
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
     *
     * @param query
     * @param page
     * @return
     */
    public static Result search(String query, Integer page) {
        Map<String, Object> result = Book.searchByTitleAndISBN(query, PAGE_SIZE, page);
        List<Book> books = (List) result.get("records");
        page = (Integer) result.get("page");
        Integer pages = (Integer) result.get("pages");
        String title = "Results for '" + query + "'";
        return ok(views.html.Books.index.render(title, books, page, pages, query));
    }

    /**
     *
     * @param isbn
     * @return
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

    /**
     *
     * @return
     */
    public static Result recommendations() {
        String username = session("username");
        User user = User.findByUsername(username);
//        List<Book> books = user.recommendedBooks();
//        books = Book.synchronize(books);
        return ok(); //(views.html.Books.index.render(title, books, page, pages, query));
    }

}

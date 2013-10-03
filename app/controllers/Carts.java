package controllers;

import play.*;
import play.mvc.*;
import play.data.Form;
import play.data.DynamicForm;

import views.html.*;

import models.*;

import com.mongodb.*;
import utils.mongodb.*;
import com.mongodb.gridfs.*;

public class Carts extends Controller
{

  private static Cart createUserCart(User user)
  {
    // 1. Build the new cart record.
    Cart carts = Cart.getModel();
    DBCollection cartCollection = carts.getMongoCollection();
    BasicDBObject newCart = new BasicDBObject();
    newCart.append("user_id", String.valueOf(user.getMongo("_id")));
    newCart.append("status", "pending");
    newCart.append("date_time", System.currentTimeMillis() / 1000L);

    // 2. Insert the new cart
    Cart resultingCart = null;
    WriteResult recordWritten = cartCollection.insert(newCart);
    CommandResult isRecordWritten = recordWritten.getLastError();
    if (null == isRecordWritten.get("err"))
    {
      resultingCart = carts.fromMongoRecord(newCart);
    }
    return resultingCart;
  }

  private static Cart findUserCart(User user)
  {
    Cart carts = Cart.getModel();
    DBCollection cartCollection = carts.getMongoCollection();
    BasicDBObject cartQuery = new BasicDBObject();
    cartQuery.append("user_id", user.getMongo("_id"));
    cartQuery.append("status", "pending");
    return carts.fromMongoRecord(cartCollection.findOne(cartQuery));
  }

  private static User findByUsername(String username) {
    User users = User.getModel();
    DBCollection usersCollection = users.getMongoCollection();
    BasicDBObject usernameQuery = new BasicDBObject("username", username);
    User user = users.fromMongoRecord(usersCollection.findOne(usernameQuery));
    return user;
  }

  private static Cart findOrCreateUsersCart(String username)
  {
    // 1. Find the user, make sure they exist.
    User user = findByUsername(username);

    // 2. Attempt to find a pending transaction in the database, return if found.
    Cart userCart = findUserCart(user);

    // 3. If no transaction exists create a new pending transaction.
    if (userCart != null)
    {
        userCart = createUserCart(user);
    }
    return userCart;
  }

  public static Result add()
  {
    DynamicForm dynamicForm = Form.form().bindFromRequest();
    String isbn = dynamicForm.get("isbn");
    Integer quantity = Integer.valueOf(dynamicForm.get("quantity"));
    if (quantity == null) {
        quantity = 0;
    }

    // Find or create a cart for the user which is pending checkout.
    User user = findByUsername(session("username"));
    Cart userCart = findOrCreateUsersCart(session("username"));

    // Find the book the user wants to add to the cart.
    Book books = Book.getModel();
    DBCollection booksCollection = books.getMongoCollection();
    BasicDBObject bookQuery = new BasicDBObject("isbn", Long.valueOf(isbn));
    Logger.info("Finding " + isbn + " with query " + bookQuery.toString());
    Book book = books.fromMongoRecord(booksCollection.findOne(bookQuery));

    // Check if the book is in stock at this point in time, and that there is
    // enough stock to be added to the cart.
    if (quantity >= 0)
    {
      // Since the book is in stock at this point in time add it to the cart
      // we don't have to update it because we will not block the stock from
      // another user.
      BasicDBObject newItem = new BasicDBObject();
      newItem.append("user_id", user.getMongo("_id"));
      newItem.append("status", "pending");
      newItem.append("date_time", System.currentTimeMillis() / 1000L);
      Logger.info(book.toString());
      return ok(views.html.Carts.updated.render(true, quantity, String.valueOf(book.getMongo("title")) ));
    }
    else
    {
      // If it is not in stock this means the item ran out during the addition
      // of the item to the cart. This might have happened while the user was
      // reading the information about the book, or writing a review. In this
      // case we must inform them that the book is no longer in stock.
      return notFound(views.html.Carts.updated.render(false, quantity, (String) book.getMongo("title")));
    }
  }

  public static Result remove()
  {
    Cart userCart = findOrCreateUsersCart(session("username"));
    return ok();
  }

  public static Result update()
  {
    return ok();
  }

  public static Result find()
  {
    Cart userCart = findOrCreateUsersCart(session("username"));
    return ok(views.html.Carts.show.render());
  }

  public static Result checkout()
  {
    Cart userCart = findOrCreateUsersCart(session("username"));

    // Establish a write lock on all the neo4j nodes for the books
    // Check the stock for each book and deduct the

    return ok();
  }

}

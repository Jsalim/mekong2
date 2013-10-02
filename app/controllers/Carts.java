package controllers;

import play.*;
import play.mvc.*;

import views.html.*;

public class Carts extends Controller
{

  private static Cart createUserCart(User user)
  {
    // 1. Build the new cart record.
    Cart carts = Cart.getModel();
    DBCollection cartCollection = carts.getMongoCollection();
    BasicDBObject newCart = new BasicDBObject();
    newCart.append("user_id", user.getMongo("_id"));
    newCart.append("status", "pending");
    newCart.append("date_time", System.currentTimeMillis() / 1000L);

    // 2. Insert the new cart
    Cart resultingCart = null;
    WriteResult recordWritten = cartCollection.insert(newCart);
    CommandResult isRecordWritten = recordWritten.getLastError();
    if (null == isRecordWritten.get("err"))
    {
      resultingCart = Cart.fromMongoRecord(newCart);
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
    return Cart.fromMongoRecord(cartCollection.findOne(cartQuery));
  }

  private static Cart findOrCreateUsersCart(String username)
  {
    // 1. Find the user, make sure they exist.
    User users = User.getModel();
    DBCollection usersCollection = user.getMongoCollection();
    BasicDBObject usernameQuery = new BasicDBObject("username", username);
    User user = User.fromMongoRecord(userscollection.findOne(usernameQuery));

    // 2. Attempt to find a pending transaction in the database, return if found.
    Cart userCart = findUserCart(user);

    // 3. If no transaction exists create a new pending transaction.
    if (userCart != null)
    {
      usercart = createUserCart(user);
    }
    return usercart
  }

  public static Result add(String isbn, Integer quantity)
  {
    // Find or create a cart for the user which is pending checkout.
    Cart userCart = findOrCreateUsersCart(session("username"));

    // Find the book the user wants to add to the cart.
    Book books = Book.getModel();
    DBCollection booksCollection = books.getMongoCollection();
    BasicDBObject bookQuery = new BasicDBObject("isbn", isbn);
    Book book = Book.fromMongoRecord(booksCollection.findOne(bookQuery));

    // Check if the book is in stock at this point in time, and that there is
    // enough stock to be added to the cart.
    if (quantity >= (Integer) book.getMongo("in_stock"))
    {
      // Since the book is in stock at this point in time add it to the cart
      // we don't have to update it because we will not block the stock from
      // another user.
      BasicDBObject newItem = new BasicDBObject();
      newItem.append("user_id", user.getMongo("_id"));
      newItem.append("status", "pending");
      newItem.append("date_time", System.currentTimeMillis() / 1000L);
      return ok();
    }
    else
    {
      // If it is not in stock this means the item ran out during the addition
      // of the item to the cart. This might have happened while the user was
      // reading the information about the book, or writing a review. In this
      // case we must inform them that the book is no longer in stock.
      return notFound();
    }
  }

  public static Result remove(String isbn)
  {
    Cart userCart = findOrCreateUsersCart(session("username"));
    return ok();
  }

  public static Result find()
  {
    Cart userCart = findOrCreateUsersCart(session("username"));
    return ok();
  }

  public static Result checkout()
  {
    Cart userCart = findOrCreateUsersCart(session("username"));

    // Establish a write lock on all the neo4j nodes for the books
    // Check the stock for each book and deduct the

    return ok();
  }

}

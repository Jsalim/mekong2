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

import java.util.HashMap;
import java.util.Map;

@Security.Authenticated(Secured.class)
public class Carts extends Controller {

    /**
     *
     * @return
     */
    public static Result find() {
        User user = User.findByUsername(session("username"));
        Cart cart = Cart.findOrCreateUsersCart(user);
        return ok(views.html.Carts.show.render(cart));
    }

    /**
     *
     * @return
     */
    public static Result update() {

        // Find or create a cart for the user which is pending checkout.
        String username = session("username");
        User user = User.findByUsername(username);
        if (null == user) {
            Logger.info("Failed to find user " + username);
            return badRequest();
        } else {
            Logger.info("Found user " + user.toString());
        }

        // Map the ISBN to quantity to update.
        DynamicForm dynamicForm = Form.form().bindFromRequest();
        boolean relativeUpdate = Boolean.valueOf(dynamicForm.get("relative"));
        Map<Book, Integer> isbnToQuantity = new HashMap<Book, Integer>();
        for (Map.Entry<String, String> entry : dynamicForm.data().entrySet()) {
            if (entry.getKey().startsWith("isbn")) {
                Book book = Book.findByISBN(entry.getKey().replace("isbn", ""));
                isbnToQuantity.put(book, Integer.valueOf(entry.getValue()));
            }
        }

        // Update the cart
        Cart cart = Cart.findOrCreateUsersCart(user);
        cart = cart.adjustQuantityOfBook(isbnToQuantity, relativeUpdate);

        // Find the book the user wants to add to the cart.
        // Check if the book is in stock at this point in time, and that there is
        // enough stock to be added to the cart.
        if (null != cart) {
            return ok(views.html.Carts.updated.render(true, cart));

        // If it is not in stock this means the item ran out during the addition
        // of the item to the cart. This might have happened while the user was
        // reading the information about the book, or writing a review. In this
        // case we must inform them that the book is no longer in stock.
        } else {
            return notFound(views.html.Carts.updated.render(false, cart));
        }
    }

    /**
     *
     * @return
     */
    public static Result checkout() {
        User user = User.findByUsername(session("username"));
        Cart userCart = Cart.findOrCreateUsersCart(user);
        if (userCart.checkout()) {
            return ok();
        } else {
            return badRequest();
        }
    }

}

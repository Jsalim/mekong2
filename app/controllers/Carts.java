package controllers;

import play.*;
import play.mvc.*;
import play.data.Form;
import play.data.DynamicForm;

import utils.MessageContainer;
import utils.neo4j.Neo4jDatabaseConnection;
import views.html.*;

import models.*;

import com.mongodb.*;
import utils.mongodb.*;
import com.mongodb.gridfs.*;

import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Authenticated controller, meaning that the user must be logged in before they
 * are able to access this information.
 *
 * This primarily deals with the MongoDB instances of the carts.
 *
 */
@Security.Authenticated(Secured.class)
public class Carts extends Controller {

    /**
     * Finds the cart for the user that is currently logged in, or creates one
     * for them if they don't exist.
     * @return The cart belonging to the user.
     */
    public static Result find() {
        User user = User.findByUsername(session("username"));
        Cart cart = Cart.findOrCreateUsersCart(user);
        return ok(views.html.Carts.show.render(user, cart));
    }

    /**
     * Updates the cart with the new isbn information.
     * @return The updated cart that belongs to the user.
     */
    public static Result update() {

        // Find or create a cart for the user which is pending checkout.
        String username = session("username");
        User user = User.findByUsername(username);
        if (null == user) {
            Logger.info("Failed to find user " + username);
            return badRequest();
        }

        // Map the ISBN to quantity to update.
        DynamicForm dynamicForm = Form.form().bindFromRequest();
        Logger.info(dynamicForm.data().toString());
        boolean relativeUpdate = Boolean.valueOf(dynamicForm.get("relative"));
        Map<Book, Integer> isbnToQuantity = new HashMap<Book, Integer>();
        for (Map.Entry<String, String> entry : dynamicForm.data().entrySet()) {
            if (entry.getKey().startsWith("isbn_")) {
                Book book = Book.findByISBN(entry.getKey().replace("isbn_", ""));
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
            return ok(views.html.Carts.updated.render(user, cart));

        // If it is not in stock this means the item ran out during the addition
        // of the item to the cart. This might have happened while the user was
        // reading the information about the book, or writing a review. In this
        // case we must inform them that the book is no longer in stock.
        } else {
            return notFound(views.html.Carts.updated.render(user, cart));
        }
    }

    /**
     * Find the current cart for the user and attempt to checkout the stock, it
     * is possible that there is not enough stock available, and we must inform
     * the user if that is the case.
     * @return The successful checkout page if the checkout did succeed, otherwise
     * show the cart show page which may or may not include a number of errors.
     */
    public static Result checkout() {
        String username = session("username");
        User user = User.findByUsername(username);
        Cart cart = Cart.findOrCreateUsersCart(user);

        // Checking information parsing.
        // This extracts all of the address and creditcard information
        // from the form that has been passed to the method.
        boolean fieldMissing = false;
        DynamicForm dynamicForm = Form.form().bindFromRequest();
        BasicDBObject creditcard = new BasicDBObject();
        BasicDBObject address = new BasicDBObject();
        for (Map.Entry<String, String> data : dynamicForm.data().entrySet()) {
            String key = data.getKey();
            String value = data.getValue().trim();
            if (!value.equals("")) {
                if (key.startsWith("address_")) {
                    key = key.replaceFirst("address_", "");
                    address.put(key, value);
                } else if (key.startsWith("card_")) {
                    key = key.replaceFirst("card_", "");
                    creditcard.put(key, value);
                }
            }
        }

        // Check no address fields are missing, simple check that everything
        // that should have been entered has been entered.
        Integer addressFieldCount = 4;
        if(address.keySet().size() != addressFieldCount) {
            fieldMissing = true;
            cart.addMessage(MessageContainer.MessageType.ERROR, "Address field missing");
        }

        // Check no credit card fields are missing, simple check that everything
        // that should have been entered has been entered.
        Integer creditcardFieldCount = 5;
        if(creditcard.keySet().size() != creditcardFieldCount) {
            fieldMissing = true;
            cart.addMessage(MessageContainer.MessageType.ERROR, "Creditcard field missing");
        }

        System.out.println("Checking out for user " + username + " with " + address + " and " + creditcard);

        // Conduct the checkout
        boolean checkoutSuccess = false;
        if (!fieldMissing) {
            checkoutSuccess = cart.checkout(username, creditcard, address);
            user.updateCreditcardAndAddress(creditcard, address);
            user.loadMongoRecord();
        }

        // If the checkout succeeded then just show the review of the complete
        // order.
        if (checkoutSuccess) {
            Logger.info("Checkout for " + user.getMongo("username") + " successful!");
            Neo4jDatabaseConnection.getInstance().printDatabase();
            return ok(views.html.Carts.success.render(cart));

        // If the checkout was not a success then show the cart again and it
        // will have populated its own set of of error messages that must
        // be displayed to the user.
        } else {
            Logger.info("Checkout for " + user.getMongo("username") + " failed!");
            Neo4jDatabaseConnection.getInstance().printDatabase();
            return badRequest(views.html.Carts.show.render(user, cart));
        }
    }

}

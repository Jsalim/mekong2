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

@Security.Authenticated(Secured.class)
public class Carts extends Controller {

    /**
     *
     * @return
     */
    public static Result find() {
        User user = User.findByUsername(session("username"));
        Cart cart = Cart.findOrCreateUsersCart(user);
        return ok(views.html.Carts.show.render(user, cart));
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
     *
     * @return
     */
    public static Result checkout() {
        String username = session("username");
        User user = User.findByUsername(username);
        Cart cart = Cart.findOrCreateUsersCart(user);

        // Checking information parsing.
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

        // Check no address fields are missing
        Integer addressFieldCount = 4;
        if(address.keySet().size() != addressFieldCount) {
            fieldMissing = true;
            cart.addMessage(MessageContainer.MessageType.ERROR, "Address field missing");
        }

        // Check no creditcard fields are missing
        Integer creditcardFieldCount = 2;
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

        if (checkoutSuccess) {
            Logger.info("Checkout for " + user.getMongo("username") + " successful!");
            Neo4jDatabaseConnection.getInstance().printDatabase();
            return ok(views.html.Carts.success.render(cart));
        } else {
            Logger.info("Checkout for " + user.getMongo("username") + " failed!");
            Neo4jDatabaseConnection.getInstance().printDatabase();
            return badRequest(views.html.Carts.show.render(user, cart));
        }
    }

}

package controllers;

import models.User;
import play.data.DynamicForm;
import play.data.Form;
import play.mvc.Controller;
import play.mvc.Result;
import play.mvc.Security;

/**
 *
 */
public class Users extends Controller {

    /**
     *
     * @return
     */
    public static Result startRegister() {
        return ok(views.html.Users.register.render(null, null));
    }

    /**
     *
     * @return
     */
    public static Result finishRegister() {

        // Extract the form information
        DynamicForm requestData = Form.form().bindFromRequest();
        String username = requestData.get("username");
        String password = requestData.get("password");

        // Create the new user record and automatically log in the new user if
        // they were registered
        User user = User.registerWith(username, password);
        if (null != user) {
            session("username", String.valueOf(user.getMongo("username")));
            return redirect(routes.Books.index(0));
        } else {
            return ok(views.html.Users.register.render("Unable to register with those details.", username));
        }
    }

    /**
     *
     * @return
     */
    public static Result startLogin() {
        return ok(views.html.Users.login.render(null));
    }

    /**
     *
     * @return
     */
    public static Result finishLogin() {

        // Extract the form information
        DynamicForm requestData = Form.form().bindFromRequest();
        String username = requestData.get("username");
        String password = requestData.get("password");

        // Find the user, redirect to home if unable.
        System.out.println("Starting to find user");
        User user = User.findByUsernameAndPassword(username, password);
        if (null == user) {
            String message = "Unable to login with username or password";
            return ok(views.html.Users.login.render(message));
        } else {
            session("username", String.valueOf(user.getMongo("username")));
            return redirect(routes.Books.index(0));
        }

    }

    /**
     *
     * @return
     */
    public static Result logout() {
        session().clear();
        return redirect("/");
    }

    @Security.Authenticated(Secured.class)
    public static Result profile() {
        String username = session("username");
        User user = User.findByUsername(username);
        if (null != user) {
            return ok(views.html.Users.profile.render(user));
        } else {
            return redirect(controllers.routes.Books.index(0));
        }
    }

}

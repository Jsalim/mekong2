package controllers;

import play.*;
import play.mvc.*;
import play.data.Form;
import play.data.DynamicForm;

import views.html.*;

import com.mongodb.*;

import models.User;

public class Users extends Controller {

    public static Result startRegister() {
      return ok(views.html.Users.register.render(null, null));
    }

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

    public static Result startLogin() {
      return ok(views.html.Users.login.render(null));
    }

    public static Result finishLogin() {

      // Extract the form information
      DynamicForm requestData = Form.form().bindFromRequest();
      String username = requestData.get("username");
      String password = requestData.get("password");

      // Find the user, redirect to home if unable.
      User user = User.findByUsernameAndPassword(username, password);
      if (null == user) {
        String message = "Unable to login with username or password";
        return ok(views.html.Users.login.render(message));
      } else {
        session("username", String.valueOf(user.getMongo("username")));
        return redirect(routes.Books.index(0));
      }
    }

    public static Result logout() {
      session().clear();
      return redirect("/");
    }

}

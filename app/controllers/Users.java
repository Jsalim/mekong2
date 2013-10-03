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

      // Create the new user record.
      User users = User.getModel();
      BasicDBObject newUserRegistration = new BasicDBObject().
              append("username", username).
              append("password", password);
      DBCollection usersCollection = users.getMongoCollection();
      WriteResult recordWritten = usersCollection.insert(newUserRegistration);
      CommandResult isRecordWritten = recordWritten.getLastError();

      // Automatically log in the new user if they were registered
      if (null == isRecordWritten.get("err")) {
          User newUser = users.fromMongoRecord(newUserRegistration);
          session("username", String.valueOf(newUser.getMongo("username")));
           return redirect(routes.Books.index(1));
      } else {
        String message = "Unable to register with those details.";
        return ok(views.html.Users.register.render(message, username));
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
      if (null == username || null == password) {
          return notFound();
      }

      // Create the login json query object.
      BasicDBObject loginQuery = new BasicDBObject().
          append("username", username).
          append("password", password);

      // Find the user, redirect to home if unable.
      User users = User.getModel();
      DBObject foundUser = users.getMongoCollection().findOne(loginQuery);
      if (null == foundUser) {
        String message = "Unable to login with username or password";
        return ok(views.html.Users.login.render(message));
      } else {
        User user = users.fromMongoRecord(foundUser);
        session("username", String.valueOf(user.getMongo("username")));
        return redirect(routes.Books.index(1));
      }
    }

    public static Result logout() {
      session().clear();
      return redirect("/");
    }

}

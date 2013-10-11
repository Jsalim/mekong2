package controllers;

import play.mvc.*;
import play.mvc.Http.*;

import models.*;

/**
 * @author Jack Galilee (430395187)
 *
 * Implemented based on examples found in the play documentation
 *
 * http://www.playframework.com/documentation/2.2.0/JavaGuide4
 *
 */
public class Secured extends Security.Authenticator {

    @Override
    public String getUsername(Context ctx) {
        return ctx.session().get("username");
    }

    @Override
    public Result onUnauthorized(Context ctx) {
        return redirect(routes.Users.startLogin());
    }

}


// import java.net.UnknownHostException;
// import java.util.*;
// import java.util.regex.Pattern;

// import static spark.Spark.*;

// import com.mongodb.*;
// import polyorm.DatabaseType;
// import spark.*;
// import spark.template.velocity.VelocityRoute;

// /**
//  *
//  */
// public class Server {

//     static User dbUser = null;
//     static Book dbBooks = null;

//     public static void main(String[] args) throws UnknownHostException {

//         // Get a polyglot persistence layers for the User and Book models..
//         dbUser = User.getInstance();
//         dbBooks = Book.getInstance();

//         // Declare the location for the static files.
//         staticFileLocation("assets");

//         /**
//          *
//          */
//         before(new Filter() {
//             @Override
//             public void handle(Request request, Response response) {
//                 List<String> unprotectedRoutes = Arrays.asList("/js", "/css", "/img", "/users/register", "/users/login");

//                 // Assume the route is protected, prove otherwise.
//                 boolean protectedRoute = false;
//                 if ("/" != request.pathInfo()) {
//                     protectedRoute = true;
//                     for (String route : unprotectedRoutes) {
//                         if (request.pathInfo().startsWith(route)) {
//                             protectedRoute = false;
//                             break;
//                         }
//                     }
//                 }

//                 // Block the user from accessing the route, if it is protected and they're not logged in.
//                 if (null == request.session(false) && true == protectedRoute) {
//                     System.out.printf("[%s] Access denied to %s. User not logged in.\n", new Date().toString(), request.pathInfo());
//                     response.redirect("/users/login");
//                     return;
//                 }

//                 // Otherwise grant access.
//                 System.out.printf("[%s] Access granted to %s. User logged in.\n", new Date().toString(), request.pathInfo());
//             }
//         });

//         /**
//          *
//          */
//         get(new VelocityRoute("/") {
//             @Override
//             public Object handle(Request request, Response response) {
//                 Map<String, Object> templateInformation = new HashMap<String, Object>();
//                 return modelAndView(templateInformation, "templates\\index.wm");
//             }
//         });


//         /**
//          *
//          */
//         post(new Route("/users/login") {
//             @Override
//             public Object handle(Request request, Response response) {

//                 // Find the user.
//                 String username = request.queryParams("username");
//                 String password = request.queryParams("password");
//                 if (null == username || null == password) {
//                     response.status(500);
//                     return response;
//                 }
//                 BasicDBObject loginQuery = new BasicDBObject().
//                     append("username", username).
//                     append("password", password);

//                 // Find the user, redirect to home if unable.
//                 DBObject foundUser = dbUser.getMongoCollection().findOne(loginQuery);
//                 if (null == foundUser) {
//                     response.redirect("/");
//                     System.out.printf("[%s] Unable to login user\n", new Date().toString());
//                     return response;
//                 }

//                 // Login with the user.
//                 User user = dbUser.fromMongoRecord(foundUser);
//                 request.session(true);
//                 System.out.printf("[%s] %s logged in.\n", new Date().toString(), user.get("username", DatabaseType.MONGODB));
//                 response.redirect("/books");
//                 return response;
//             }
//         });

//         /**
//          *
//          */
//         get(new Route("/logout") {
//             @Override
//             public Object handle(Request request, Response response) {
//                 request.session(false).invalidate();
//                 response.redirect("/");
//                 return response;
//             }
//         });

//         /**
//          *
//          */
//         get(new VelocityRoute("/books") {
//             @Override
//             public Object handle(Request request, Response response) {

//                 // Query the database for all of the books.
//                 DBCursor allFoundBooks = dbBooks.getMongoCollection().find();
//                 List<Book> books = dbBooks.fromMongoRecord(allFoundBooks);

//                 // Return all of the Books to the user.
//                 Map<String, Object> templateInformation = new HashMap<String, Object>();
//                 templateInformation.put("books", books);
//                 return modelAndView(templateInformation, "templates\\books\\index.wm");

//             }
//         });



//         /**
//          *
//          */
//         get(new VelocityRoute("/books/:isbn") {
//             @Override
//             public Object handle(Request request, Response response) {

//
//             }
//         });

//         /**
//          *
//          */
//         get(new VelocityRoute("/books/:isbn/recommendations") {
//             @Override
//             public Object handle(Request request, Response response) {

//                 // Find the book with the given ISBN. Return 404 not found if unable.
//                 BasicDBObject byIsbn = new BasicDBObject("isbn", request.params(":isbn"));
//                 System.out.printf("[%s] Finding book by ISBN %s\n", new Date().toString(), byIsbn.toString());
//                 Book book = dbBooks.fromMongoRecord(dbBooks.getMongoCollection().findOne(byIsbn));
//                 if(null == book) {
//                     response.status(404);
//                     return response;
//                 }

//                 // Add the books to the information to go to the template.
//                 Map<String, Object> templateInformation = new HashMap<String, Object>();
//                 templateInformation.put("original", book);
//                 templateInformation.put("book", book);

//                 // Render the search result to the requesting client.
//                 return modelAndView(templateInformation, "templates\\books\\show.wm");

//             }
//         });

//         /**
//          *
//          */
//         put(new Route("/cart/add/:book_isbn") {
//             @Override
//             public Object handle(Request request, Response response) {

// //                String userId = request.session().attribute("user_id");
// //                Cart usersShoppingCart = dbCarts.findUsersCart(userId);
// //
// //                MongoDBQuery bookQuery = new MongoDBQuery();
// //                bookQuery.putIfNotNull("isbn", request.params(":book_isbn"));
// //                Book selectedBook = dbBooks.find(bookQuery).get(0);
// //
// //                usersShoppingCart.addBook(selectedBook);

//                 return "TODO: Return a JSON document and a SUCCESS or FAILURE status of if the BOOK was or was not purchased.";
//             }
//         });

//         /**
//          *
//          */
//         get(new Route("/cart/checkout") {
//             @Override
//             public Object handle(Request request, Response response) {
//                 return "TODO: Return a JSON document containing a list of recommendations for all of the BOOKS.";
//             }
//         });

//         /**
//          *
//          */
//         post(new Route("/cart/checkout") {
//             @Override
//             public Object handle(Request request, Response response) {
//                 return "TODO: Return a JSON document containing a list of recommendations for all of the BOOKS.";
//             }
//         });

//     }

// }

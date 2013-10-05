package utils.seeds;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import models.Book;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.UniqueFactory;
import utils.neo4j.Neo4jDatabaseConnection;

import java.util.Map;

public class Neo4jSeeder {

  private GraphDatabaseService graphDb = null;
  private Neo4jDatabaseConnection connection = null;

  private Index<Node> usersIndex;
  private Index<Node> booksIndex;
  private Index<Node> authorsIndex;
  private Index<Node> subjectsIndex;

  public Neo4jSeeder(Neo4jDatabaseConnection connection)
  {
    this.connection = connection;
    this.graphDb = this.connection.getService();
  }

  public void setupDatabase()
  {
    usersIndex = connection.getService().index().forNodes("Users");
    booksIndex = connection.getService().index().forNodes("Books");
    authorsIndex = connection.getService().index().forNodes("Authors");
    booksIndex = connection.getService().index().forNodes("Subjects");
    return;
  }

  public UniqueFactory<Node> uniqueNodeFactoryFor(String index)
  {
    UniqueFactory<Node> factory = new UniqueFactory.UniqueNodeFactory(graphDb, index)
    {
      @Override
      protected void initialize(Node created, Map<String, Object> properties)
      {
        created.setProperty("name", properties.get("name"));
      }
    };
    return factory;
  }

  public void enhanceBookRecord(Transaction tx, BasicDBObject book)
  {
    // Book
    System.out.println("Establishing unique Book node factory");
    UniqueFactory<Node> bookFactory = uniqueNodeFactoryFor("Books");
    System.out.println("Creating or finding book" + book.getString("isbn"));
    Node bookNode = bookFactory.getOrCreate("name", book.getString("isbn"));
    System.out.println("Setting node properties");
    bookNode.setProperty("title", book.getString("title"));
    bookNode.setProperty("stock", book.getInt("stock"));
    bookNode.setProperty("price", book.getDouble("price"));

    // Authors
    System.out.println("Creating author nodes");
    UniqueFactory<Node> authorFactory = uniqueNodeFactoryFor("Authors");
    BasicDBList authors = (BasicDBList) book.get("authors");
    for (int i = 0; i < authors.size(); i++)
    {
      BasicDBObject author = (BasicDBObject) authors.get(i);
      System.out.println(author.toString());
      StringBuilder fullnameBuilder = new StringBuilder();
      fullnameBuilder.append(author.getString("firstname"));
      fullnameBuilder.append(" ");
      fullnameBuilder.append(author.getString("lastname"));
      String fullname = fullnameBuilder.toString();
      System.out.println("Creating author node for " + fullname);
      Node authorNode = authorFactory.getOrCreate("name", fullname);
      System.out.println("Setting author additional properties " + authorNode.toString());
      // authorNode.setProperty("firstname", book.getString("firstname"));
      // authorNode.setProperty("lastname", book.getString("lastname"));
      System.out.println("Creating author WRITTEN_BY relationship");
      Relationship writtenBy = bookNode.createRelationshipTo(authorNode,
              Book.RELATIONSHIPS.WRITTEN_BY);
      System.out.println("Created author node");
    }

    // Subjects
    System.out.println("Creating subject nodes");
    UniqueFactory<Node> subjectFactory = uniqueNodeFactoryFor("Subjects");
    BasicDBList subjects = (BasicDBList) book.get("subjects");
    for (int i = 0; i < authors.size(); i++)
    {
      System.out.println("Creating subject node");
      String subject = String.valueOf(subjects.get(i));
      Node subjectNode = subjectFactory.getOrCreate("name", subject);
      System.out.println("Creating author ABOUT relationship");
      Relationship aboutSubject = bookNode.createRelationshipTo(subjectNode,
              Book.RELATIONSHIPS.ABOUT);
      System.out.println("Created subject node");
    }

    System.out.println("Finishing enhancement");
    return;
  }

}

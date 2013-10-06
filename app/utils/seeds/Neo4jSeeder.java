package utils.seeds;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import models.Book;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.UniqueFactory;
import org.neo4j.helpers.collection.IteratorUtil;
import utils.neo4j.Neo4jDatabaseConnection;

import javax.management.relation.Relation;
import java.util.*;

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
    subjectsIndex = connection.getService().index().forNodes("Subjects");
    return;
  }

  public UniqueFactory<Node> uniqueNodeFactoryFor(Index<Node> index, final String indexName)
  {
    UniqueFactory<Node> factory = new UniqueFactory.UniqueNodeFactory(index)
    {
      @Override
      protected void initialize(Node created, Map<String, Object> properties)
      {
        created.setProperty(indexName, properties.get(indexName));
      }
    };
    return factory;
  }

  private List<Node> alreadyConnectedNodes(Node from, RelationshipType type)
  {
      List<Node> connected = new ArrayList<Node>();
      for (Relationship relationship : from.getRelationships(type))
      {
          connected.add(relationship.getOtherNode(from));
      }
      return connected;
  }

  private void createAuthors(BasicDBObject book, Node bookNode)
  {
      System.out.println("Creating author nodes");
      List<Node> connectedAuthors = alreadyConnectedNodes(bookNode, Book.RELATIONSHIPS.WRITTEN_BY);
      UniqueFactory<Node> authorFactory = uniqueNodeFactoryFor(authorsIndex, "name");
      BasicDBList authors = (BasicDBList) book.get("authors");
      for (int i = 0; i < authors.size(); i++)
      {
          // Construct name
          BasicDBObject author = (BasicDBObject) authors.get(i);
          System.out.println(author.toString());
          StringBuilder fullnameBuilder = new StringBuilder();
          fullnameBuilder.append(author.getString("firstname"));
          fullnameBuilder.append(" ");
          fullnameBuilder.append(author.getString("lastname"));
          String fullname = fullnameBuilder.toString();

          // Create node
          System.out.println("Creating author node for " + fullname);
          Node authorNode = authorFactory.getOrCreate("name", fullname);

          // Set properties
          System.out.println("Setting author additional properties " + authorNode.toString());
          authorNode.setProperty("firstname", author.getString("firstname"));
          authorNode.setProperty("lastname", author.getString("lastname"));
          System.out.println("Creating author WRITTEN_BY relationship");

          // Create relationships
          if (!connectedAuthors.contains(bookNode))
          {
              Relationship writtenBy = bookNode.createRelationshipTo(authorNode, Book.RELATIONSHIPS.WRITTEN_BY);
              System.out.println("Created author node");
          }
          else
          {
              System.out.println("Relationship already exists, skipping");
          }

          // Index node
          for (String key : Arrays.asList("name", "firstname", "lastname"))
          {
            authorsIndex.add(authorNode, key, authorNode.getProperty(key));
          }
      }
  }

  private void createSubjects(BasicDBObject book, Node bookNode)
  {
      System.out.println("Creating subject nodes");
      List<Node> connectedSubjects = alreadyConnectedNodes(bookNode, Book.RELATIONSHIPS.ABOUT);
      UniqueFactory<Node> subjectFactory = uniqueNodeFactoryFor(subjectsIndex, "name");
      BasicDBList subjects = (BasicDBList) book.get("subjects");
      for (int i = 0; i < subjects.size(); i++)
      {
          System.out.println("Creating subject node");
          String subject = String.valueOf(subjects.get(i));
          Node subjectNode = subjectFactory.getOrCreate("name", subject);
          System.out.println("Creating author ABOUT relationship");
          if (!connectedSubjects.contains(subjectNode))
          {
              Relationship aboutSubject = bookNode.createRelationshipTo(subjectNode, Book.RELATIONSHIPS.ABOUT);
              System.out.println("Created subject node");
          }
          else
          {
              System.out.println("Relationship already exists, skipping");
          }
      }
  }

  public void enhanceBookRecord(BasicDBObject book)
  {
    Transaction tx = graphDb.beginTx();
    try
    {
        // Book
        System.out.println("Establishing unique Book node factory");
        UniqueFactory<Node> bookFactory = uniqueNodeFactoryFor(booksIndex, "isbn");
        System.out.println("Creating or finding book" + book.getString("isbn"));
        Node bookNode = bookFactory.getOrCreate("isbn", book.getString("isbn"));
        System.out.println("Setting node properties");
        bookNode.setProperty("title", book.getString("title"));
        bookNode.setProperty("stock", book.getInt("stock"));
        bookNode.setProperty("price", book.getDouble("price"));

        // Authors
        createAuthors(book, bookNode);

        // Subjects
        createSubjects(book, bookNode);

        // Complete
        System.out.println("Finishing enhancement");
        tx.success();
    }
    catch (Exception e)
    {
        System.err.println("Failed enhancement " + e.toString());
        e.printStackTrace();
        tx.failure();
    }
    finally
    {
       tx.finish();
       return;
    }
  }

  public void createSimilarToRelationships(BasicDBObject book) {
      Transaction tx = graphDb.beginTx();
      try {
          List<String> similarIsbns = new ArrayList<String>();
          BasicDBList similarities = (BasicDBList) book.get("similar");
          for (int i = 0; i < similarities.size(); i++)
          {
              String similarIsbn = String.valueOf(similarities.get(i));
              similarIsbns.add(similarIsbn);
          }
          System.out.println("Found similar" + similarIsbns);

          String query = "START root=node:Books(isbn={isbn}), book=node(*)\n" +
                  "WHERE book.isbn! IN {similarities}\n" +
                  "CREATE UNIQUE root-[:SIMILAR_TO]->book\n" +
                  "RETURN root, book";

          Map<String, Object> params = new HashMap<String, Object>();
          params.put("isbn", book.getString("isbn"));
          params.put("similarities", similarIsbns);

          ExecutionEngine executionEngine = new ExecutionEngine(graphDb);
          ExecutionResult executionResult = executionEngine.execute(query, params);
          System.out.println("Query results " + executionResult);

          tx.success();
      } catch (Exception e) {
          e.printStackTrace();
          tx.failure();
      } finally {
          tx.finish();
      }
  }

  public void displayDatabase()
  {
    ExecutionEngine engine = new ExecutionEngine(graphDb);
    ExecutionResult result = engine.execute("start n=node(*) return n");
    Iterator<Node> n_column = result.columnAs("n");
    for (Node node : IteratorUtil.asIterable(n_column))
    {
      System.out.println("Node: " + node);
      System.out.println("\tProperties:");
      for (String key : node.getPropertyKeys())
      {
        System.out.println("\t\t" + key + ": " + node.getProperty(key));
      }
      System.out.println("\tRelationships:");
      for (Relationship rel : node.getRelationships())
      {
        System.out.println("\t\t" +
            rel.getStartNode().toString() +
            "<-" + rel.getType().toString() + "->" +
            rel.getEndNode().toString());
            System.out.println("\t\tProperties:");
            for (String key : rel.getPropertyKeys())
            {
              System.out.println("\t\t\t" + key + ": " + rel.getProperty(key));
            }
        }
      }
    }

}

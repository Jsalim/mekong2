package utils.seeds;

import com.mongodb.BasicDBList;
import com.mongodb.BasicDBObject;
import models.Book;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.*;
import org.neo4j.graphdb.index.Index;
import org.neo4j.graphdb.index.UniqueFactory;
import utils.neo4j.Neo4jDatabaseConnection;

import java.util.*;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Used for creating the inital set of Neo4j nodes for the seeds and then
 * in the second pass for going over them and creating the links between the
 * various nodes.
 *
 */
public class Neo4jSeeder {

    private GraphDatabaseService graphDb = null;
    private Neo4jDatabaseConnection connection = null;

    private Index<Node> usersIndex;
    private Index<Node> booksIndex;
    private Index<Node> authorsIndex;
    private Index<Node> subjectsIndex;

    /**
     * Creates the seeder and references the appropriate Neo4j database
     * conection.
     * @param connection Neo4j database connection singleton.
     */
    public Neo4jSeeder(Neo4jDatabaseConnection connection) {
        System.out.println("Neo4j Seeder getting connection and service");
        this.connection = connection;
        this.graphDb = this.connection.getService();
        System.out.println("Neo4j connected and got service");
    }

    /**
     * Setups the database by getting and creating all of the appropriate indexes.
     */
    public void setupDatabase() {
        System.out.println("Setting up neo4j database");
        usersIndex = connection.getService().index().forNodes("Users");
        booksIndex = connection.getService().index().forNodes("Books");
        authorsIndex = connection.getService().index().forNodes("Authors");
        subjectsIndex = connection.getService().index().forNodes("Subjects");
        System.out.println("Setup neo4j database");
        return;
    }

    /**
     * Returns all of the nodes that are connected to the provided node.
     * @param from Node that the connection is going from
     * @param type Type of connection that is being made.
     * @return List of all the nodes connected to the from node.
     */
    private List<Node> alreadyConnectedNodes(Node from, RelationshipType type) {
        List<Node> connected = new ArrayList<Node>();
        for (Relationship relationship : from.getRelationships(type)) {
            connected.add(relationship.getOtherNode(from));
        }
        return connected;
    }

    /**
     * Creates the author nodes and then connects them all to the original
     * book node.
     * @param book Book document that contains all of the information about the
     *             authors.
     * @param bookNode Book node to connect all of the new authors to.
     */
    private void createAuthors(BasicDBObject book, Node bookNode) {
        System.out.println("Creating author nodes");
        List<Node> connectedAuthors = alreadyConnectedNodes(bookNode, Book.RELATIONSHIPS.WRITTEN_BY);
        UniqueFactory<Node> authorFactory = new UniqueFactory.UniqueNodeFactory(authorsIndex) {
            @Override
            protected void initialize(Node created, Map<String, Object> properties) {
                created.setProperty("name", properties.get("name"));
            }
        };
        BasicDBList authors = (BasicDBList) book.get("authors");
        if (null != authors) {
            for (int i = 0; i < authors.size(); i++) {
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
                if (!connectedAuthors.contains(bookNode)) {
                    Relationship writtenBy = bookNode.createRelationshipTo(authorNode, Book.RELATIONSHIPS.WRITTEN_BY);
                    System.out.println("Created author node");
                } else {
                    System.out.println("Relationship already exists, skipping");
                }

                // Index node
                for (String key : Arrays.asList("name", "firstname", "lastname")) {
                    authorsIndex.add(authorNode, key, authorNode.getProperty(key));
                }
            }
        }
    }

    /**
     * Creates all of the subject nodes and the connects them with the book
     * node.
     * @param book Book document that contains the information for the subjects.
     * @param bookNode Book node to connect all of the subject nodes to.
     */
    private void createSubjects(BasicDBObject book, Node bookNode) {
        System.out.println("Creating subject nodes");
        List<Node> connectedSubjects = alreadyConnectedNodes(bookNode, Book.RELATIONSHIPS.ABOUT);
        UniqueFactory<Node> subjectFactory = new UniqueFactory.UniqueNodeFactory(subjectsIndex) {
            @Override
            protected void initialize(Node created, Map<String, Object> properties) {
                created.setProperty("name", properties.get("name"));
            }
        };
        BasicDBList subjects = (BasicDBList) book.get("subjects");
        if (null != subjects) {
            if (subjects != null) {
                for (int i = 0; i < subjects.size(); i++) {
                    System.out.println("Creating subject node");
                    String subject = String.valueOf(subjects.get(i));
                    Node subjectNode = subjectFactory.getOrCreate("name", subject);
                    System.out.println("Creating author ABOUT relationship");
                    if (!connectedSubjects.contains(subjectNode)) {
                        Relationship aboutSubject = bookNode.createRelationshipTo(subjectNode, Book.RELATIONSHIPS.ABOUT);
                        System.out.println("Created subject node");
                    } else {
                        System.out.println("Relationship already exists, skipping");
                    }
                }
            } else {
                System.out.println("Book has no subjects");
            }
        }
    }

    /**
     * Creates the nodes that represent the data for the Book and connects them.
     *
     * There is a limitation where using the direct Java API only bidirectional
     * relationships can be created. This is problematic as the schema does
     * not require this, and instead wants uni-directional relationships.
     *
     * The only way to achieve this would be to do another pass on the database
     * and remove the other direction. However, this would be far to time consuming
     * for the database seeding processes which is already fairly slow.
     *
     * @param book The book to create the nodes and relationships fro.
     */
    public void enhanceBookRecord(BasicDBObject book) {
        Transaction tx = graphDb.beginTx();
        try {
            // Book
            System.out.println("Establishing unique Book node factory");
            UniqueFactory<Node> bookFactory = new UniqueFactory.UniqueNodeFactory(booksIndex) {
                @Override
                protected void initialize(Node created, Map<String, Object> properties) {
                    created.setProperty("isbn", properties.get("isbn"));
                }
            };
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
        } catch (Exception e) {
            System.err.println("Failed enhancement " + e.toString());
            e.printStackTrace();
            tx.failure();
        } finally {
            tx.finish();
            System.out.println("Finishing enhancement");
        }
    }

    /**
     * Constructs the relationships between books and every other book. The
     * relationships is a unique single direction.
     * @param book The book to create all of the similar nodes for.
     */
    public void createSimilarToRelationships(BasicDBObject book) {
        Transaction tx = graphDb.beginTx();
        try {
            List<String> similarIsbns = new ArrayList<String>();
            BasicDBList similarities = (BasicDBList) book.get("similar");
            if (null != similarities) {
                for (int i = 0; i < similarities.size(); i++) {
                    String similarIsbn = String.valueOf(similarities.get(i));
                    similarIsbns.add(similarIsbn);
                }
                System.out.println("Found similar" + similarIsbns);

                // Similar to relation creation query.
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
            }
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            tx.failure();
        } finally {
            tx.finish();
        }
    }

}

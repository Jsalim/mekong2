package utils.neo4j;

import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Relationship;
import org.neo4j.graphdb.Transaction;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import org.neo4j.helpers.collection.IteratorUtil;
import utils.DatabaseConnection;
import utils.DatabaseType;

import java.net.UnknownHostException;
import java.util.Iterator;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Singleton class for handling a single connection variable with the Neo4j
 * embedded database.
 *
 */
public class Neo4jDatabaseConnection extends DatabaseConnection {

    private static Neo4jDatabaseConnection instance = null;
    private String DB_PATH = "mekong";
    private GraphDatabaseService graphDb = null;

    /**
     * Connects to the database and sets the configuration to automatically
     * index both the nodes and the relationships in the interest of speed.
     * @throws java.net.UnknownHostException
     */
    protected Neo4jDatabaseConnection() {
      GraphDatabaseFactory graphDbFactory = new GraphDatabaseFactory();
      this.graphDb = new GraphDatabaseFactory().
      newEmbeddedDatabaseBuilder(DB_PATH).
      setConfig(GraphDatabaseSettings.node_auto_indexing, "true").
      setConfig(GraphDatabaseSettings.relationship_auto_indexing, "true").
      newGraphDatabase();
      registerShutdownHook(graphDb);
    }

    /**
     * Force the graphDB to shutdown, important if the normal shutdown hook
     * is not going to be invoked.
     */
    public void shutdown() {
        this.graphDb.shutdown();
    }

    /**
     * Create a shudown hook, taken from the Neo4j documentation page.
     * http://docs.neo4j.org/chunked/stable/tutorials-java-embedded-setup.html
     * @param graphDb The service to shutdown
     */
    private static void registerShutdownHook( final GraphDatabaseService graphDb ) {

        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook(new Thread() {
            @Override
            public void run() {
                graphDb.shutdown();
            }
        });
    }

    /**
     * Get the graph database service that is associated with the singleton
     * this means that all the application methods are using the same singleton
     * instance.
     * @return Service so that it can be appropriately handled.
     */
    public GraphDatabaseService getService() {
      return this.graphDb;
    }

    /**
     * Creates and executes a CYPHER query to remove all of the nodes and
     * relationships from the database.
     */
    public void dropDB() {
        Transaction tx = graphDb.beginTx();
        try {
            String query = "START n=node(*), r=rel(*) DELETE n, r";
            ExecutionEngine executor = new ExecutionEngine(graphDb);
            ExecutionResult result = executor.execute(query);
            System.out.println("Dropped data in Neo4j " + result);
            tx.success();
        } catch (Exception e) {
            e.printStackTrace();
            tx.failure();
        } finally {
            tx.finish();
        }
    }

    /**
     * Since only one connection to the database is permitted it must be
     * managed through the singleton pattern and this connection class.
     *
     * In order to interact with the Neo4j database this method must be called
     * and then subsequent methods must be used to address the information.
     * @return
     * @throws UnknownHostException
     */
    public static Neo4jDatabaseConnection getInstance() {
        if (null == instance) {
            instance = new Neo4jDatabaseConnection();
        }
        return instance;
    }

    /**
     * Since this is a Neo4j database connection it will return the
     * Neo4j database type.
     * @return Neo4j database type enumerable
     */
    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.NEO4J;
    }

    /**
     * Print out all of the information associated with the current database.
     */
    public void printDatabase() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);
        ExecutionResult result = engine.execute("start n=node(*) return n");
        Iterator<Node> nodes = result.columnAs("n");
        for (Node node : IteratorUtil.asIterable(nodes)) {
            System.out.println("Node: " + node);
            System.out.println("\tProperties:");
            for (String key : node.getPropertyKeys()) {
                System.out.println("\t\t" + key + ": " + node.getProperty(key));
            }
            System.out.println("\tRelationships:");
            for (Relationship rel : node.getRelationships()) {
                System.out.println("\t\t" +
                        rel.getStartNode().toString() +
                        "<-" + rel.getType().toString() + "->" +
                        rel.getEndNode().toString());
                System.out.println("\t\tProperties:");
                for (String key : rel.getPropertyKeys()) {
                    System.out.println("\t\t\t" + key + ": " + rel.getProperty(key));
                }
            }
        }
        return;
    }

}

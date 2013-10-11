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
 * Created with IntelliJ IDEA.
 * User: jgalilee
 * Date: 9/3/13
 * Time: 9:09 PM
 * To change this template use File | Settings | File Templates.
 */
public class Neo4jDatabaseConnection extends DatabaseConnection {

    private static Neo4jDatabaseConnection instance = null;
    private String DB_PATH = "mekong";
    private GraphDatabaseService graphDb = null;

    /**
     *
     * @throws java.net.UnknownHostException
     */
    protected Neo4jDatabaseConnection() {
      GraphDatabaseFactory graphDbFactory = new GraphDatabaseFactory();
      this.graphDb = new GraphDatabaseFactory().
      newEmbeddedDatabaseBuilder(DB_PATH).
      setConfig( GraphDatabaseSettings.node_auto_indexing, "true" ).
      setConfig( GraphDatabaseSettings.relationship_auto_indexing, "true" ).
      newGraphDatabase();
      registerShutdownHook(graphDb);
    }

    public void shutdown() {
        this.graphDb.shutdown();
    }

    private static void registerShutdownHook( final GraphDatabaseService graphDb )
    {
        // Registers a shutdown hook for the Neo4j instance so that it
        // shuts down nicely when the VM exits (even if you "Ctrl-C" the
        // running application).
        Runtime.getRuntime().addShutdownHook( new Thread()
        {
            @Override
            public void run()
            {
                graphDb.shutdown();
            }
        } );
    }

    /**
     *
     * @return
     */
    public GraphDatabaseService getService() {
      return this.graphDb;
    }

    /**
     *
     */
    public void dropDB()
    {
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
     *
     * @return
     * @throws UnknownHostException
     */
    public static Neo4jDatabaseConnection getInstance() {
        if (null == instance) {
            instance = new Neo4jDatabaseConnection();
        }
        return instance;
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.NEO4J;
    }

    public void printDatabase() {
        ExecutionEngine engine = new ExecutionEngine(graphDb);
        ExecutionResult result = engine.execute("start n=node(*) return n");
        Iterator<Node> nodes = result.columnAs("n");
        for (Node node : IteratorUtil.asIterable(nodes))
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
        return;
    }

}

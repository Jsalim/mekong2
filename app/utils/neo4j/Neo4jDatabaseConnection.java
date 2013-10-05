package utils.neo4j;

import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.factory.GraphDatabaseFactory;
import org.neo4j.graphdb.factory.GraphDatabaseSettings;
import utils.DatabaseConnection;
import utils.DatabaseType;

import java.net.UnknownHostException;

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

    public GraphDatabaseService getService() {
      return this.graphDb;
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

}

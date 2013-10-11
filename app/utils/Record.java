package utils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.bson.types.ObjectId;
import org.neo4j.cypher.javacompat.ExecutionEngine;
import org.neo4j.cypher.javacompat.ExecutionResult;
import org.neo4j.graphdb.GraphDatabaseService;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;
import org.neo4j.helpers.collection.IteratorUtil;
import utils.neo4j.Neo4jDatabaseConnection;

import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * User: jgalilee
 * Date: 9/3/13
 * Time: 8:31 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Record<R extends Record> extends MessageContainer {

    private Node neo4jRecord;
    private DBObject mongoRecord;

    protected Record() {
        this.mongoRecord = new BasicDBObject();
    }

    protected Record(DBObject record) {
        this.mongoRecord = record;
    }

    protected Record(Node record) {
        this.neo4jRecord = record;
    }

    public abstract DBCollection getMongoCollection();

    public List<R> fromMongoRecord(DBCursor cursor) {
        List<R> result = new ArrayList<R>();
        while (cursor.hasNext()) {
            result.add(fromMongoRecord(cursor.next()));
        }
        cursor.close();
        return result;
    }

    /**
     *
     * @param type
     * @return
     */
    protected boolean loadRecord(DatabaseType type) {
        if (type.equals(DatabaseType.MONGODB) && null != this.mongoRecord) {
            ObjectId oid = (ObjectId) this.mongoRecord.get("_id");
            if (null != oid) {
                BasicDBObject query = new BasicDBObject("_id", oid);
                this.mongoRecord = this.getMongoCollection().findOne(query);
                return true;
            } else {
                return false;
            }
        } else if (type.equals(DatabaseType.NEO4J) && null != this.mongoRecord) {
            try {
                List<R> records = queryNeo4jRecord();
                if (null != records && records.size() > 0) {
                  R record = records.get(0);
                  this.neo4jRecord = record.getNeo4jRecord();
                  if (neo4jRecord != null) {
                      return true;
                  }
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                return false;
            }
        } else {
            return false;
        }
    }

    public Object get(String key, DatabaseType type) {
        if (DatabaseType.MONGODB.equals(type)) {
            return getMongoRecord().get(key);
        } else if (DatabaseType.NEO4J.equals(type)) {
            try {
                return getNeo4jRecord().getProperty(key);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        } else {
            return null;
        }
    }

    public Object put(String key, Object value, DatabaseType type) {
        if (DatabaseType.MONGODB.equals(type)) {
            return getMongoRecord().put(key, value);
        } else if (DatabaseType.NEO4J.equals(type)) {
            getNeo4jRecord().setProperty(key, value);
            return getNeo4jRecord().getProperty(key);
        } else {
            return null;
        }
    }

    public Node getNeo4jRecord() {
        return this.neo4jRecord;
    }

    public Object getNeo4j(String key) {
        return get(key, DatabaseType.NEO4J);
    }

    public Object putNeo4j(String key, Object value) {
        return put(key, value, DatabaseType.NEO4J);
    }

    public boolean loadNeo4jRecord() {
        return loadRecord(DatabaseType.NEO4J);
    }

    public List<R> queryNeo4jRecord() {
      return new ArrayList<R>();
    }

    public boolean neo4jRecordLoaded() {
        if (this.neo4jRecord == null) {
            return false;
        } else {
            return true;
        }
    }

    public abstract R fromMongoRecord(DBObject record);

    public DBObject getMongoRecord() {
        return this.mongoRecord;
    }

    public Object getMongo(String key) {
        return get(key, DatabaseType.MONGODB);
    }

    public Object putMongo(String key, Object value) {
        return put(key, value, DatabaseType.MONGODB);
    }

    public boolean mongoRecordLoaded() {
        if (null != mongoRecord) {
            if (mongoRecord.get("_id") != null) {
                return true;
            }
        }
        return false;
    }

    public boolean loadMongoRecord() {
        return loadRecord(DatabaseType.MONGODB);
    }

    public String toString() {
        return getMongoRecord().toString();
    }

}

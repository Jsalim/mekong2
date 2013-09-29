package utils;

import com.mongodb.BasicDBObject;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import org.neo4j.graphdb.Node;

import java.util.ArrayList;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: jgalilee
 * Date: 9/3/13
 * Time: 8:31 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class Record<R extends Record> {

    private Node neo4jRecord;
    private DBObject mongoRecord;

    protected Record() {

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

    public abstract R fromMongoRecord(DBObject record);

    private DBObject getMongoRecord() {
        return this.mongoRecord;
    }

    private Node getNeo4jRecord() {
        return this.neo4jRecord;
    }

    public Object get(String key, DatabaseType type) {
        if (DatabaseType.MONGODB.equals(type)) {
            return getMongoRecord().get(key);
        } else if (DatabaseType.NEO4J.equals(type)) {
            return getNeo4jRecord().getProperty(key);
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

    public Object getNeo4j(String key) {
        return get(key, DatabaseType.NEO4J);
    }

    public Object getMongo(String key) {
        return get(key, DatabaseType.MONGODB);
    }

    public Object putNeo4j(String key, Object value) {
        return put(key, value, DatabaseType.NEO4J);
    }

    public Object putMongo(String key, Object value) {
        return put(key, value, DatabaseType.MONGODB);
    }

    public String toString() {
        return getMongoRecord().toString();
    }

}

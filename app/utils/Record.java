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
 *
 * @author Jack Galilee (430395187)
 *
 * In order to keep only one connection to the databases open the record
 * type mixes a range of static and non-static functionality in with a single
 * class for the type of record it will be handling.
 *
 * If time had permitted this record model would be much cleaner.
 *
 * @param <R> Self reference to record type to use with the record
 */
public abstract class Record<R extends Record> extends MessageContainer {

    private Node neo4jRecord;
    private DBObject mongoRecord;

    /**
     *  Constructs an instance of R and provides it with a blank
     *  MongoDB document which has not been persisted.
     */
    protected Record() {
        this.mongoRecord = new BasicDBObject();
    }

    /**
     * Constructs an instance of R from a MongoDB document.
     * @param record
     */
    protected Record(DBObject record) {
        this.mongoRecord = record;
    }

    /**
     * Constructs an instance of R from a Neo4j node.
     * @param record
     */
    protected Record(Node record) {
        this.neo4jRecord = record;
    }

    /**
     *
     * @return Collection of items for R
     */
    public abstract DBCollection getMongoCollection();

    /**
     * Creates a record of type R from each of the items in the given
     * DBCursor.
     * @param cursor Cursor containing all of the items.
     * @return List of R record items from the cursor.
     */
    public List<R> fromMongoRecord(DBCursor cursor) {
        List<R> result = new ArrayList<R>();
        while (cursor.hasNext()) {
            result.add(fromMongoRecord(cursor.next()));
        }
        cursor.close();
        return result;
    }

    /**
     * Loads a record of the appropriate type using information that is stored
     * in the MongoDB document associated with the object.
     * @param type What kind of database is it going to be handling.
     * @return True if the record was loaded for the required database, false
     * otherwise.
     */
    protected boolean loadRecord(DatabaseType type) {

        // Is it a MongoDB document that is attempting to load?
        if (type.equals(DatabaseType.MONGODB) && null != this.mongoRecord) {
            ObjectId oid = (ObjectId) this.mongoRecord.get("_id");
            if (null != oid) {
                BasicDBObject query = new BasicDBObject("_id", oid);
                this.mongoRecord = this.getMongoCollection().findOne(query);
                return true;
            } else {
                return false;
            }

        // Is it a Neo4j node that is attempting to load?
        } else if (type.equals(DatabaseType.NEO4J) && null != this.mongoRecord) {
            try {
                R record = queryNeo4jRecord();
                this.neo4jRecord = record.getNeo4jRecord();
                if (neo4jRecord != null) {
                    return true;
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                return false;
            }

        // It is not a database type that we understand, we can't load it.
        } else {
            return false;
        }
    }

    /**
     * Get the key value information from the field or property of the record
     * that is associated from the appropriate type.
     * @param key Key of the field or property.
     * @param type Type of record we're looking for, document or node.
     * @return The value as a generic object type since we can't guarantee what
     * it should  be.
     */
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

    /**
     * Places the value into the field or property of the appropriate key.
     * If it is a Neo4j record this is not handled within a transactional blog
     * and must be done with the method call.
     * @param key Key for the field or property to identify it with the record.
     * @param value Value to associate with the field or property.
     * @param type The type of record we're identifying, is it a node or a
     *             document.
     * @return Returns the object representation that was added, it can't be
     * guaranteed that the API did not mutate the value and as a result the
     * return value is provided.
     */
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

    /**
     * Return the Neo4j node, if it has been set or not.
     * @return Null if the node is not set, the node otherwise.
     */
    public Node getNeo4jRecord() {
        return this.neo4jRecord;
    }

    /**
     * Returns the value of a Neo4j property, it is the result of calling
     * the get method with the DatabaseType of Neo4j.
     * @param key The key for addressing the property.
     * @return The value in-case it was mutated, that resulted from the get
     * call.
     */
    public Object getNeo4j(String key) {
        return get(key, DatabaseType.NEO4J);
    }

    /**
     * Sets the value of a proprety for a node, this does not run itself inside
     * of a transactional block and must be done from the calling method.
     * @param key Key of the property to be addressed
     * @param value The value to set as the property for the key
     * @return
     */
    public Object putNeo4j(String key, Object value) {
        return put(key, value, DatabaseType.NEO4J);
    }

    /**
     * Attempt to load the Neo4j node from the database using the load record
     * method. This will result in a call to the queryNeo4j record and then
     * subsequent return will be handled as a single value.
     * @return
     */
    public boolean loadNeo4jRecord() {
        return loadRecord(DatabaseType.NEO4J);
    }

    /**
     * Not necessary for all of the classes to implement this method, as it is
     * possible that they can't be addressed in Neo4j from MongoDB as they share
     * no primary key.
     * @return List of nodes from the query where the first one will be
     * handled as the actual value.
     */
    public R queryNeo4jRecord() {
      return null;
    }

    /**
     * Check if the Neo4j record has been loaded, if it has then return true
     * otherwise return false and the Node has not been loaded.
     * @return True if the node is loaded, false otherwise.
     */
    public boolean neo4jRecordLoaded() {
        if (this.neo4jRecord == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * Since it is not possible to construct a generic type directly it has
     * been mandated that all implementing classes will provide methods to
     * construct themselves with the provided MongoDB record.
     * @param record
     * @return
     */
    public abstract R fromMongoRecord(DBObject record);

    /**
     * Returns the MongoDB document that is assocated with the document, if there
     * is not a MongoDB document then it will be null.
     * @return Null if the variable is not set, the MongoDB document otherwise.
     */
    public DBObject getMongoRecord() {
        return this.mongoRecord;
    }

    /**
     * Returns a MongoDB documents field value with the provided key.
     * @param key to address the MongoDB document with.
     * @return Result of addressing the MongoDB document with the key.
     */
    public Object getMongo(String key) {
        return get(key, DatabaseType.MONGODB);
    }

    /**
     * Places the key value pair into the fields for the MongoDB document using
     * the generic put command.
     * @param key Key for identifying the field to place information into.
     * @param value Informaiton to place into the field identified by the key
     * @return The result of the placement, assuming to be the actual placement
     * object in-case there was a mutation on the data.
     */
    public Object putMongo(String key, Object value) {
        return put(key, value, DatabaseType.MONGODB);
    }

    /**
     * Checks if there is a MongoDB document associated with the current record,
     * and that it has a _id key. This can't guarantee that the key has not been
     * arbitrarily set and does not exists in the actual MongoDB collection.
     * @return
     */
    public boolean mongoRecordLoaded() {
        if (null != mongoRecord) {
            if (mongoRecord.get("_id") != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Calls the load record method with the MongoDB type. This effectively
     * attempts to load / reload the mongoDB document if the _id field and the
     * appropriate OID is specified as the value for that _id key.
     * @return True if the record loaded, false otherwise.
     */
    public boolean loadMongoRecord() {
        return loadRecord(DatabaseType.MONGODB);
    }

    /**
     * Creates a string representation from the combined Neo4j and MongoDB record.
     * @return Returns the construction of this string.
     */
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Neo4j: ");
        if (neo4jRecordLoaded()) {
            sb.append(getNeo4jRecord().toString());
            sb.append("\n");
        }
        if (mongoRecordLoaded()) {
            sb.append("MongoDB: ");
            sb.append(getMongoRecord().toString());
        }
        return sb.toString();
    }

}

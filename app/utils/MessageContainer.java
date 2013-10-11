package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 *
 * @author Jack Galilee (430395187)
 *
 * Simplistic class for adding non-persistent information to extending classes.
 */
public abstract class MessageContainer {

    /**
     * Types of information that can be added as messages.
     */
    public static enum MessageType {
        SUCCESS, WARNING, ERROR
    }

    /**
     * Mapping of message types to lists of messages. It is not possible
     * to address a particular message directly.
     */
    private Map<MessageType, List<String>> messages = null;

    public Map<MessageType, List<String>> getMessages() {
        return messages;
    }

    /**
     * Check if there are any messages available.
     * @return True if there are messages, false otherwise.
     */
    public boolean hasMessages() {
        return !(null == messages || messages.keySet().size() == 0);
    }

    /**
     * Check if a message of a particular type exists.
     * @param type The type of message that it could be.
     * @return Returns true if it does, false otherwise.
     */
    public boolean hasMessagesOfType(MessageType type) {
        if (null == messages) {
            return false;
        } else {
            return messages.keySet().contains(type);
        }
    }

    /**
     * Returns all of the messages that have been bound against the provided
     * messages type.
     * @param type Message type to get the messages for.
     * @return Returns the list of string messages for the message type.
     */
    public List<String> getMessagesOfType(MessageType type) {
        if (null == messages || !messages.keySet().contains(type)) {
            return new ArrayList<String>();
        } else {
            return messages.get(type);
        }
    }

    /**
     * Adds the message to the list of messages for the appropriate type.
     * @param type The type of message to add the message string to.
     * @param message The message to add to the list of message types.
     * @return Returns true if the collection was able to add the string to
     * the list of message collections.
     */
    public boolean addMessage(MessageType type, String message) {
        if (null == messages) {
            messages = new HashMap<MessageType, List<String>>();
        }
        List<String> container = messages.get(type);
        if (null == container) {
            container = new ArrayList<String>();
            messages.put(type, container);
        }
        return container.add(message);
    }

}

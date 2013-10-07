package utils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * User: jgalilee
 * Date: 10/7/13
 * Time: 5:50 PM
 * To change this template use File | Settings | File Templates.
 */
public abstract class MessageContainer {

    public static enum MessageType {
        SUCCESS, WARNING, ERROR
    }

    private Map<MessageType, List<String>> messages = null;

    public Map<MessageType, List<String>> getMessages() {
        return messages;
    }

    /**
     *
     * @return
     */
    public boolean hasMessages() {
        return !(null == messages || messages.keySet().size() == 0);
    }

    /**
     *
     * @param type
     * @return
     */
    public boolean hasMessagesOfType(MessageType type) {
        if (null == messages) {
            return false;
        } else {
            return messages.keySet().contains(type);
        }
    }

    /**
     *
     * @param type
     * @return
     */
    public List<String> getMessagesOfType(MessageType type) {
        if (null == messages || !messages.keySet().contains(type)) {
            return new ArrayList<String>();
        } else {
            return messages.get(type);
        }
    }

    /**
     *
     * @param type
     * @param message
     * @return
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

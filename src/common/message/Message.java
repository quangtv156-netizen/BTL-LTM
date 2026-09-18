package common.message;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Message dung chung, truyen qua ObjectOutputStream/ObjectInputStream.
 * Dung mot Map<String,Object> lam payload de khong phai tao rieng
 * mot class cho tung loai message.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;

    public final MessageType type;
    public final Map<String, Object> data = new HashMap<>();

    public Message(MessageType type) {
        this.type = type;
    }

    public Message put(String key, Object value) {
        data.put(key, value);
        return this;
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key) {
        return (T) data.get(key);
    }

    public String getString(String key) {
        Object o = data.get(key);
        return o == null ? null : o.toString();
    }

    public int getInt(String key) {
        Object o = data.get(key);
        return o == null ? -1 : ((Number) o).intValue();
    }

    public boolean getBool(String key) {
        Object o = data.get(key);
        return o != null && (Boolean) o;
    }

    @Override
    public String toString() {
        return "Message{" + type + ", " + data + "}";
    }
}

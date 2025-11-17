package myframework;

import java.util.HashMap;

public class ModelView {
    private String view;
    private HashMap<String, Object> attributes = new HashMap<>();

    public ModelView() {
    }
    
    public ModelView(String view) {
        this.view = view;
   }

    public String getView() {
        return view;
    }

    public void setView(String view) {
        this.view = view;
    }

    public HashMap<String, Object> getAttributes() {
        return attributes;
    }

    public void addAttributes(String key, Object value) {
        this.attributes.put(key, value);
    }

    public Object getAttributes(String key) {
        return this.attributes.get(key);
    }
}
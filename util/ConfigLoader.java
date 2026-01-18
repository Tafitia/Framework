package myframework.util;

import java.io.InputStream;
import java.util.Properties;

public class ConfigLoader {
    
    private Properties properties;
    private String file;

    public ConfigLoader() {
        this.properties = new Properties();
        this.file = "app.properties";
        load();
    }

    private void load() {
        try (InputStream input = Thread.currentThread().getContextClassLoader().getResourceAsStream(file)) {
            if (input != null) {
                properties.load(input);
                System.out.println("Configuration chargée depuis " + file);
            } else {
                System.out.println("Fichier " + file + " introuvable. Utilisation des valeurs par défaut.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public String get(String key) {
        return properties.getProperty(key);
    }
    
    public String get(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
}
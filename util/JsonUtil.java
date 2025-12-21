package myframework.util;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.text.SimpleDateFormat;

public class JsonUtil {

    private static final ObjectMapper mapper = new ObjectMapper();

    static {
        // Configuration de Jackson pour formater les dates proprement
        mapper.setDateFormat(new SimpleDateFormat("yyyy-MM-dd"));
        // Pour avoir un JSON "joli" (indenté), optionnel
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    public static String toJson(Object obj) {
        try {
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            e.printStackTrace();
            return "{\"error\": \"Erreur de sérialisation JSON\"}";
        }
    }
}

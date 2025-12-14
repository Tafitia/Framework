package myframework.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.sql.Date;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DataBinder {

    /**
     * Remplit un objet à partir d'une map de paramètres.
     * @param object L'instance vide à remplir.
     * @param prefix Le préfixe (ex: "e" pour "e.nom", ou "emp[0]" pour un tableau).
     * @param allParams La Map contenant toutes les données (clé -> tableau de valeurs).
     */
    public static void bind(Object object, String prefix, Map<String, String[]> allParams) {
        for (String paramName : allParams.keySet()) {
            // Filtrage par préfixe
            // Si prefix="e", on cherche "e.nom", "e.age", etc.
            if (prefix != null && !prefix.isEmpty()) {
                if (!paramName.startsWith(prefix + ".")) {
                    continue; 
                }
            }

            // Récupération de la valeur (on prend la première du tableau)
            String[] values = allParams.get(paramName);
            if (values == null || values.length == 0) continue;
            String value = values[0];
            
            // Nettoyage du nom pour avoir l'attribut pur
            // Ex: "e.departement.nom" devient "departement.nom"
            String attributeName = (prefix != null && !prefix.isEmpty()) 
                                   ? paramName.substring(prefix.length() + 1) 
                                   : paramName;

            try {
                injectValue(object, attributeName, value);
            } catch (Exception e) {
                // On ignore les champs qui ne matchent pas
            }
        }
    }

    // Méthode récursive pour naviguer dans les objets (.) et listes ([])
    private static void injectValue(Object currentObj, String fieldName, String value) throws Exception {
        // Cas d'arrêt : champ simple
        if (!fieldName.contains(".") && !fieldName.contains("[")) {
            setField(currentObj, fieldName, value);
            return;
        }

        String currentPart = fieldName;
        String remainingPart = null;

        // Découpage au premier point
        if (fieldName.contains(".")) {
            int dotIndex = fieldName.indexOf(".");
            currentPart = fieldName.substring(0, dotIndex);
            remainingPart = fieldName.substring(dotIndex + 1);
        }

        // --- CAS LISTE : employes[0] ---
        if (currentPart.contains("[")) {
            String listName = currentPart.substring(0, currentPart.indexOf("["));
            int index = Integer.parseInt(currentPart.substring(currentPart.indexOf("[") + 1, currentPart.indexOf("]")));
            
            Field listField = getField(currentObj.getClass(), listName);
            listField.setAccessible(true);
            
            List list = (List) listField.get(currentObj);
            if (list == null) {
                list = new ArrayList<>();
                listField.set(currentObj, list);
            }
            
            // Agrandir la liste si nécessaire
            while (list.size() <= index) {
                list.add(null);
            }
            
            // Récupérer ou créer l'élément
            Object element = list.get(index);
            if (element == null) {
                ParameterizedType pt = (ParameterizedType) listField.getGenericType();
                Class<?> elementClass = (Class<?>) pt.getActualTypeArguments()[0];
                element = elementClass.getDeclaredConstructor().newInstance();
                list.set(index, element);
            }
            
            if (remainingPart != null) {
                injectValue(element, remainingPart, value);
            }
        } 
        // --- CAS OBJET IMBRIQUÉ : departement ---
        else {
            Field field = getField(currentObj.getClass(), currentPart);
            field.setAccessible(true);
            Object subObj = field.get(currentObj);
            
            if (subObj == null) {
                subObj = field.getType().getDeclaredConstructor().newInstance();
                field.set(currentObj, subObj);
            }
            
            if (remainingPart != null) {
                injectValue(subObj, remainingPart, value);
            }
        }
    }

    private static void setField(Object obj, String fieldName, String value) throws Exception {
        Field field = getField(obj.getClass(), fieldName);
        field.setAccessible(true);
        field.set(obj, convert(value, field.getType()));
    }

    private static Field getField(Class<?> clazz, String name) throws NoSuchFieldException {
        try {
            return clazz.getDeclaredField(name);
        } catch (NoSuchFieldException e) {
            if (clazz.getSuperclass() != null) return getField(clazz.getSuperclass(), name);
            throw e;
        }
    }

    private static Object convert(String value, Class<?> type) {
        if (type == String.class) return value;
        if (type == int.class || type == Integer.class) return Integer.parseInt(value);
        if (type == double.class || type == Double.class) return Double.parseDouble(value);
        if (type == long.class || type == Long.class) return Long.parseLong(value);
        if (type == boolean.class || type == Boolean.class) return Boolean.parseBoolean(value);
        if (type == Date.class) return Date.valueOf(value);
        return value;
    }
}
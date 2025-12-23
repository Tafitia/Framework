package myframework;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.servlet.RequestDispatcher;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;            
import java.sql.Date;

import myframework.util.AnnotationScanner;
import myframework.util.Mapping;
import myframework.util.JsonUtil;
import myframework.util.DataBinder;

@MultipartConfig // Indispensable pour recevoir des fichiers
public class FrontServlet extends HttpServlet {
    
    // Changement ici : On stocke une liste de Mapping par URL
    private HashMap<String, List<Mapping>> urlMappings;

    @Override
    public void init() throws ServletException {
        super.init();
        urlMappings = AnnotationScanner.scanControllers();
    }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        findResource(req, resp);
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        findResource(req, resp);
    }
  
    private void findResource(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        
        // On récupère une liste de candidats potentiels
        List<Mapping> possibleMappings = null;
        HashMap<String, String> pathVariables = new HashMap<>();
        
        // Recherche exacte d'abord
        if(urlMappings.containsKey(path)) {
            possibleMappings = urlMappings.get(path);
        } else {
            // Recherche avec paramètres dynamiques
            for (String pattern : urlMappings.keySet()) {
                HashMap<String, String> extractedVars = extractPathVariables(pattern, path);
                if (extractedVars != null) {
                    possibleMappings = urlMappings.get(pattern);
                    pathVariables = extractedVars;
                    break;
                }
            }
        }
        
        // Si on a trouvé l'URL (donc une liste de mappings)
        if(possibleMappings != null) {
            Mapping mapp = null;
            String httpMethod = req.getMethod(); // ex: GET, POST
            
            // On cherche le bon mapping correspondant à la méthode HTTP
            for (Mapping m : possibleMappings) {
                Method methodToCheck = m.getMethod();
                
                // Vérification de l'annotation RequestMapping
                if (methodToCheck.isAnnotationPresent(RequestMapping.class)) {
                    String annotationMethod = methodToCheck.getAnnotation(RequestMapping.class).value();
                    if (annotationMethod.equalsIgnoreCase(httpMethod)) {
                        mapp = m;
                        break;
                    }
                } else {
                    // Si pas d'annotation, c'est un candidat par défaut (si on n'a rien trouvé de mieux)
                    if (mapp == null) {
                        mapp = m;
                    }
                }
            }

            // Si on a trouvé une méthode compatible
            if(mapp != null) {
                Method method = mapp.getMethod();

                if (method.isAnnotationPresent(Json.class)) {
                    // On prépare la réponse JSON
                    resp.setContentType("application/json;charset=UTF-8");
                    PrintWriter out = resp.getWriter();
                    
                    try {
                        Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                        Object[] args = prepareMethodArguments(method, req, pathVariables);
                        Object returnValue = method.invoke(controllerInstance, args);

                        // Structure globale de la réponse
                        // LinkedHashMap pour garder l'ordre : status, code, data
                        Map<String, Object> finalResponse = new java.util.LinkedHashMap<>();
                        finalResponse.put("status", "success");
                        finalResponse.put("code", 200);

                        // Gestion du contenu de "data"
                        if (returnValue instanceof Collection || (returnValue != null && returnValue.getClass().isArray())) {
                            // CAS 1 : C'est une Liste ou un Tableau
                            // Structure : data: { count: X, items: [...] }
                            int size = 0;
                            if (returnValue instanceof Collection) {
                                size = ((Collection<?>) returnValue).size();
                            } else {
                                size = java.lang.reflect.Array.getLength(returnValue);
                            }

                            Map<String, Object> dataContent = new java.util.LinkedHashMap<>();
                            dataContent.put("count", size);
                            dataContent.put("items", returnValue);
                            
                            finalResponse.put("data", dataContent);

                        } else {
                            // CAS 2 : C'est un objet unique (String, Employe, etc.)
                            finalResponse.put("data", returnValue);
                        }
                        
                        // Transformation en JSON via Jackson
                        out.println(JsonUtil.toJson(finalResponse));
                        
                        return; // On arrête ici

                    } catch (Exception e) {
                        e.printStackTrace();
                        resp.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
                        
                        // Gestion propre de l'erreur en JSON
                        Map<String, Object> errorResponse = new java.util.LinkedHashMap<>();
                        errorResponse.put("status", "error");
                        errorResponse.put("code", 500);
                        errorResponse.put("message", e.getCause() != null ? e.getCause().getMessage() : e.getMessage());
                        
                        out.println(JsonUtil.toJson(errorResponse));
                        return;
                    }
                }
                if(method.getReturnType().equals(String.class)) {
                    try {
                        Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                        Object[] args = prepareMethodArguments(method, req, pathVariables);
                        String result = (String) method.invoke(controllerInstance, args);
                        resp.getWriter().println(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error");
                    }
                } else if(method.getReturnType().equals(ModelView.class)) {
                    try {
                        Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                        Object[] args = prepareMethodArguments(method, req, pathVariables);
                        ModelView mv = (ModelView) method.invoke(controllerInstance, args);
                        String view = mv.getView();
                        for (String key : mv.getAttributes().keySet()) {
                            req.setAttribute(key, mv.getAttributes().get(key));
                        }
                        RequestDispatcher dispatcher = req.getRequestDispatcher(view);
                        dispatcher.forward(req, resp);
                    } catch (Exception e) {
                        e.printStackTrace();
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error");
                    }
                } else {
                    resp.getWriter().println(path + " existe mais n'est pas supporté pour le moment");
                }
            } else {
                // L'URL existe, mais pas pour cette méthode HTTP (ex: POST vs GET)
                resp.setStatus(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
                resp.getWriter().println("Erreur 405 : Methode " + httpMethod + " non supportee pour " + path);
            }
        } else {
            resp.getWriter().println(path + " n'est pas là'");
        }
    }
    
    private HashMap<String, String> extractPathVariables(String pattern, String url) {
        HashMap<String, String> pathVariables = new HashMap<>();
        
        // Liste pour stocker les noms des paramètres dans l'ordre
        java.util.ArrayList<String> paramNames = new java.util.ArrayList<>();
        
        // Construire la regex en remplaçant {param} par des groupes de capture
        String regex = pattern;
        java.util.regex.Pattern paramPattern = java.util.regex.Pattern.compile("\\{([^/]+)\\}");
        java.util.regex.Matcher paramMatcher = paramPattern.matcher(pattern);
        
        // Extraire les noms des paramètres
        while (paramMatcher.find()) {
            paramNames.add(paramMatcher.group(1));
        }
        
        // Transformer le pattern en regex avec groupes de capture
        regex = regex.replaceAll("\\{[^/]+\\}", "([^/]+)");
        
        // Matcher l'URL avec la regex
        java.util.regex.Pattern urlPattern = java.util.regex.Pattern.compile(regex);
        java.util.regex.Matcher urlMatcher = urlPattern.matcher(url);
        
        if (urlMatcher.matches()) {
            // Extraire les valeurs des groupes de capture
            for (int i = 0; i < paramNames.size(); i++) {
                String paramName = paramNames.get(i);
                String paramValue = urlMatcher.group(i + 1);
                pathVariables.put(paramName, paramValue);
            }
            return pathVariables;
        }
        
        return null;
    }
    
    private Object[] prepareMethodArguments(Method method, HttpServletRequest req, HashMap<String, String> pathVariables) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        Map<String, String[]> parameterMap = req.getParameterMap();

        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramType = parameter.getType();
            String paramName = parameter.getName();
            
            // 1. GESTION DU NOM DU PARAMETRE
            if (parameter.isAnnotationPresent(RequestParam.class)) {
                String annVal = parameter.getAnnotation(RequestParam.class).value();
                if(annVal != null && !annVal.isEmpty()) paramName = annVal;
            }
            //  2. GESTION DES MAPS (Parameters ou Uploads)
            if (Map.class.isAssignableFrom(paramType)) {
                
                Type genericType = parameter.getParameterizedType();
                Type valueType = null;
                if (genericType instanceof ParameterizedType) {
                    valueType = ((ParameterizedType) genericType).getActualTypeArguments()[1];
                }

                boolean handled = false;

                // --- CAS UNIQUE : TOUT UPLOAD DEVIENT Map<String, byte[]> ---
                // "Astuce Suprême" : Nom du fichier concaténé dans la clé
                if (valueType == byte[].class) {
                    Map<String, byte[]> fileMap = new HashMap<>();
                    
                    if (isMultipart(req)) {
                        try {
                            for (Part part : req.getParts()) {
                                if (isPartFile(part)) {
                                    try (InputStream is = part.getInputStream()) {
                                        java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                                        int nRead;
                                        byte[] data = new byte[1024];
                                        while ((nRead = is.read(data, 0, data.length)) != -1) {
                                            buffer.write(data, 0, nRead);
                                        }
                                        
                                        // Concaténation : nomInput_nomFichierOriginal
                                        String originalName = part.getSubmittedFileName();
                                        String uniqueKey = part.getName() + "_" + originalName;
                                        
                                        // Gestion des doublons
                                        if (fileMap.containsKey(uniqueKey)) {
                                            uniqueKey = part.getName() + "_" + System.currentTimeMillis() + "_" + originalName;
                                        }

                                        fileMap.put(uniqueKey, buffer.toByteArray());
                                    }
                                }
                            }
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                    args[i] = fileMap;
                    handled = true;
                }

                // --- CAS 3 : Paramètres classiques (Map<String, Object[]>) ---
                if (!handled) {
                    Map<String, Object[]> paramMap = new HashMap<>();
                    
                    Map<String, Class<?>> expectedTypes = new HashMap<>();
                    for (Parameter p : parameters) {
                        if (!Map.class.isAssignableFrom(p.getType())) {
                            String name = p.getName();
                            if (p.isAnnotationPresent(RequestParam.class)) {
                                String val = p.getAnnotation(RequestParam.class).value();
                                if(val != null && !val.isEmpty()) name = val;
                            }
                            expectedTypes.put(name, p.getType());
                        }
                    }

                    for (String key : parameterMap.keySet()) {
                        String[] values = parameterMap.get(key);
                        Object[] convertedValues = new Object[values.length];
                        
                        if (expectedTypes.containsKey(key)) {
                            convertedValues = castArrayToType(values, expectedTypes.get(key));
                        } else {
                            for (int k = 0; k < values.length; k++) {
                                convertedValues[k] = inferType(values[k]);
                            }
                        }
                        paramMap.put(key, convertedValues);
                    }
                    
                    for (String key : pathVariables.keySet()) {
                        paramMap.putIfAbsent(key, new Object[]{ inferType(pathVariables.get(key)) });
                    }
                    args[i] = paramMap;
                }
                continue;
            }

            // 3. GESTION TABLEAUX D'OBJETS
            if (paramType.isArray()) {
                try {
                    Class<?> componentType = paramType.getComponentType();
                    int maxIndex = -1;
                    String arraySearch = paramName + "[";
                    
                    for(String key : parameterMap.keySet()){
                        if(key.startsWith(arraySearch)){
                            try {
                                int end = key.indexOf("]", arraySearch.length());
                                int idx = Integer.parseInt(key.substring(arraySearch.length(), end));
                                if(idx > maxIndex) maxIndex = idx;
                            } catch(Exception e){}
                        }
                    }
                    
                    if(maxIndex >= 0) {
                        Object arrayInstance = java.lang.reflect.Array.newInstance(componentType, maxIndex + 1);
                        for(int idx=0; idx<=maxIndex; idx++){
                            Object element = componentType.getDeclaredConstructor().newInstance();
                            DataBinder.bind(element, paramName + "[" + idx + "]", parameterMap);
                            java.lang.reflect.Array.set(arrayInstance, idx, element);
                        }
                        args[i] = arrayInstance;
                    } else {
                        args[i] = java.lang.reflect.Array.newInstance(componentType, 0);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    args[i] = null;
                }
                continue;
            }

            // 4. GESTION OBJETS COMPLEXES
            if (!isSimpleType(paramType) && !paramType.equals(ModelView.class)) {
                try {
                    Object dataObject = paramType.getDeclaredConstructor().newInstance();
                    DataBinder.bind(dataObject, paramName, parameterMap);
                    args[i] = dataObject;
                    continue; 
                } catch (Exception e) {
                    e.printStackTrace();
                    args[i] = null;
                    continue;
                }
            }

            // 5. GESTION TYPES SIMPLES
            String paramValue = pathVariables.get(paramName);
            if (paramValue == null && parameterMap.containsKey(paramName)) {
                String[] vals = parameterMap.get(paramName);
                if(vals.length > 0) paramValue = vals[0];
            }
            
            if (paramValue != null) {
                args[i] = convertParameter(paramValue, paramType);
            } else {
                args[i] = getDefaultValue(paramType);
            }
        }
        
        return args;
    }

    private boolean isSimpleType(Class<?> type) {
        return type.isPrimitive() || 
               type.equals(String.class) || 
               type.equals(Integer.class) || 
               type.equals(Long.class) || 
               type.equals(Double.class) || 
               type.equals(Boolean.class) || 
               type.equals(Date.class);
    }

    private Object inferType(String value) {
        if (value == null) return null;
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try { return Date.valueOf(value); } catch (Exception ignored) {}
        }
        try { return Integer.parseInt(value); } catch (NumberFormatException ignored) {}
        try { return Double.parseDouble(value); } catch (NumberFormatException ignored) {}
        if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        return value;
    }

    private Object[] castArrayToType(String[] values, Class<?> type) {
        Object[] result = new Object[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = convertParameter(values[i], type);
        }
        return result;
    }
    private Object convertParameter(String value, Class<?> type) {
        if (type.equals(String.class)) return value;
        try {
            if (type.equals(int.class) || type.equals(Integer.class)) return Integer.parseInt(value);
            if (type.equals(long.class) || type.equals(Long.class)) return Long.parseLong(value);
            if (type.equals(double.class) || type.equals(Double.class)) return Double.parseDouble(value);
            if (type.equals(boolean.class) || type.equals(Boolean.class)) return Boolean.parseBoolean(value);
            if (type.equals(Date.class)) return Date.valueOf(value);
        } catch (Exception e) {
            // En cas d'erreur de conversion, on retourne null ou valeur par defaut
        }
        return null;
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type.equals(int.class)) return 0;
        if (type.equals(long.class)) return 0L;
        if (type.equals(double.class)) return 0.0;
        if (type.equals(boolean.class)) return false;
        return null;
    }

    private boolean isMultipart(HttpServletRequest req) {
        String contentType = req.getContentType();
        return contentType != null && contentType.startsWith("multipart/form-data");
    }

    private boolean isPartFile(Part part) {
        return part.getSubmittedFileName() != null && !part.getSubmittedFileName().isEmpty();
    }
}
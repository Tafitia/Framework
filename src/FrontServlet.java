package myframework;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.sql.Date;

import myframework.util.AnnotationScanner;
import myframework.util.Mapping;

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
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramType = parameter.getType();
            String paramName;
            
           if (Map.class.isAssignableFrom(paramType)) {
                Map<String, Object[]> paramMap = new HashMap<>();
                Map<String, String[]> rawMap = req.getParameterMap();

                // Récupération des types attendus via les autres arguments de la méthode
                Map<String, Class<?>> expectedTypes = new HashMap<>();
                for (Parameter p : parameters) {
                    if (!Map.class.isAssignableFrom(p.getType())) {
                        String name = p.getName();
                        if (p.isAnnotationPresent(RequestParam.class)) {
                            String annVal = p.getAnnotation(RequestParam.class).value();
                            if(annVal != null && !annVal.isEmpty()) name = annVal;
                        }
                        expectedTypes.put(name, p.getType());
                    }
                }

                for (String key : rawMap.keySet()) {
                    String[] values = rawMap.get(key);
                    Object[] convertedValues = new Object[values.length];

                    // Si on connait le type (car il est présent dans les arguments de la méthode)
                    if (expectedTypes.containsKey(key)) {
                        convertedValues = castArrayToType(values, expectedTypes.get(key));
                    } else {
                        // SINON : Inférence de type automatique
                        for (int k = 0; k < values.length; k++) {
                            convertedValues[k] = inferType(values[k]);
                        }
                    }
                    paramMap.put(key, convertedValues);
                }
                // Ajout des path variables avec inférence
                for (String key : pathVariables.keySet()) {
                    paramMap.putIfAbsent(key, new Object[]{ inferType(pathVariables.get(key)) });
                }
                args[i] = paramMap;
                continue;
            }
            // Vérifier si le paramètre a l'annotation @RequestParam
            if (parameter.isAnnotationPresent(RequestParam.class)) {
                RequestParam requestParam = parameter.getAnnotation(RequestParam.class);
                paramName = requestParam.value();
                // Si value() est vide, utiliser le nom du paramètre
                if (paramName == null || paramName.isEmpty()) {
                    paramName = parameter.getName();
                }
            } else {
                // Pas d'annotation, utiliser le nom du paramètre
                paramName = parameter.getName();
            }
            
            // Chercher d'abord dans les path variables
            String paramValue = pathVariables.get(paramName);
            
            // Si pas trouvé, chercher dans les query parameters
            if (paramValue == null) {
                paramValue = req.getParameter(paramName);
            }
            
            if (paramValue != null) {
                // Convertir la valeur selon le type
                args[i] = convertParameter(paramValue, paramType);
            } else {
                // Si pas de valeur, mettre null (ou 0 pour les primitifs)
                args[i] = getDefaultValue(paramType);
            }
        }
        
        return args;
    }
    
    private Object inferType(String value) {
        if (value == null) return null;
        // 1. Date (YYYY-MM-DD)
        if (value.matches("\\d{4}-\\d{2}-\\d{2}")) {
            try {
                return Date.valueOf(value);
            } catch (Exception ignored) {}
        }
        // 2. Integer
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException ignored) {}
        // 3. Double
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException ignored) {}
        // 4. Boolean 
        if(value.equalsIgnoreCase("true") || value.equalsIgnoreCase("false")) {
            return Boolean.parseBoolean(value);
        }
        // 5. String
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
}
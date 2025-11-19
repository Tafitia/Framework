package myframework;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import myframework.util.AnnotationScanner;
import myframework.util.Mapping;

public class FrontServlet extends HttpServlet {
    
    private HashMap<String, Mapping> urlMappings;

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
        
        Mapping mapp = null;
        
        // Recherche exacte d'abord
        if(urlMappings.containsKey(path)) {
            mapp = urlMappings.get(path);
        } else {
            // Recherche avec paramètres dynamiques
            for (String pattern : urlMappings.keySet()) {
                if (matchesUrlPattern(pattern, path)) {
                    mapp = urlMappings.get(pattern);
                    break;
                }
            }
        }
        
        if(mapp != null) {
            Method method = mapp.getMethod();
            if(method.getReturnType().equals(String.class)) {
                try {
                    Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                    Object[] args = prepareMethodArguments(method, req);
                    String result = (String) method.invoke(controllerInstance, args);
                    resp.getWriter().println(result);
                } catch (Exception e) {
                    e.printStackTrace();
                    resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error");
                }
            } else if(method.getReturnType().equals(ModelView.class)) {
                try {
                    Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                    Object[] args = prepareMethodArguments(method, req);
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
            resp.getWriter().println(path + " n'est pas là'");
        }
    }
    
    private boolean matchesUrlPattern(String pattern, String url) {
        // Transforme /tests/{id} en regex /tests/[^/]+
        String regex = pattern.replaceAll("\\{[^/]+\\}", "[^/]+");
        return url.matches(regex);
    }
    
    private Object[] prepareMethodArguments(Method method, HttpServletRequest req) {
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Class<?> paramType = parameter.getType();
            String paramName;
            
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
            
            String paramValue = req.getParameter(paramName);
            
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
    
    private Object convertParameter(String value, Class<?> type) {
        if (type.equals(String.class)) {
            return value;
        } else if (type.equals(int.class) || type.equals(Integer.class)) {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return type.equals(int.class) ? 0 : null;
            }
        } else if (type.equals(long.class) || type.equals(Long.class)) {
            try {
                return Long.parseLong(value);
            } catch (NumberFormatException e) {
                return type.equals(long.class) ? 0L : null;
            }
        } else if (type.equals(double.class) || type.equals(Double.class)) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                return type.equals(double.class) ? 0.0 : null;
            }
        } else if (type.equals(boolean.class) || type.equals(Boolean.class)) {
            return Boolean.parseBoolean(value);
        }
        return null;
    }
    
    private Object getDefaultValue(Class<?> type) {
        if (type.equals(int.class)) {
            return 0;
        } else if (type.equals(long.class)) {
            return 0L;
        } else if (type.equals(double.class)) {
            return 0.0;
        } else if (type.equals(boolean.class)) {
            return false;
        }
        return null;
    }

}

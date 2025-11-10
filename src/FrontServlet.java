package myframework;

import java.io.IOException;
import java.util.HashMap;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;
import java.lang.reflect.Method;
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
        
        if(urlMappings.containsKey(path)) {
                Mapping mapp = urlMappings.get(path);
                Method method = mapp.getMethod();

                if(method.getReturnType().equals(String.class)) {
                    try {
                        Object controllerInstance = mapp.getClazz().getDeclaredConstructor().newInstance();
                        String result = (String) method.invoke(controllerInstance);
                        resp.getWriter().println(result);
                    } catch (Exception e) {
                        e.printStackTrace();
                        resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Error");
                    }
                } else {
                    resp.getWriter().println(path + " existe mais n'est pas supporté pour le moment");
                }
            }
            else {
            resp.getWriter().println(path + " n'est pas là'");
            }
    }

}

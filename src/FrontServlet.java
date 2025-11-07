package myframework;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.RequestDispatcher;
import myframework.util.AnnotationScanner;

public class FrontServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        findResource(req, resp);
    }

    private void findResource(HttpServletRequest req, HttpServletResponse resp) throws ServletException, IOException {
        String path = req.getRequestURI().substring(req.getContextPath().length());
        if (path == null || path.isEmpty()) {
            path = "/";
        }
        
        if ("/".equals(path)) {
            resp.setContentType("text/html; charset=UTF-8");
            AnnotationScanner.scanAndDisplay(resp.getWriter());
            return;
        }
        
        boolean resourceExists = getServletContext().getResource(path) != null;
        
        if (resourceExists) {
            RequestDispatcher defaultDispatcher = getServletContext().getNamedDispatcher("default");
            defaultDispatcher.forward(req, resp);
        } else {
            resp.getWriter().println(path + " non existant");
        }
    }
}

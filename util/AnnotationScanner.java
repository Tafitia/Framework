package myframework.util;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Set;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ClasspathHelper;
import org.reflections.util.ConfigurationBuilder;
import myframework.Controller;
import myframework.UrlAnnotation;

public class AnnotationScanner {
    
    public static HashMap<String, Mapping> scanControllers() {
        HashMap<String, Mapping> urlMappings = new HashMap<>();
        
        try {
            Reflections reflections = new Reflections(new ConfigurationBuilder()
                .forPackages("")
                .setScanners(Scanners.TypesAnnotated, Scanners.MethodsAnnotated, Scanners.SubTypes));
            
            Set<Class<?>> controllers = reflections.getTypesAnnotatedWith(Controller.class);
            
            for (Class<?> controllerClass : controllers) {
                System.out.println("Controller: " + controllerClass.getName());
                
                Method[] methods = controllerClass.getDeclaredMethods();
                for (Method method : methods) {
                    if (method.isAnnotationPresent(UrlAnnotation.class)) {
                        UrlAnnotation urlAnnotation = method.getAnnotation(UrlAnnotation.class);
                        String url = urlAnnotation.url();
                        Mapping mapping = new Mapping(controllerClass, method);
                        urlMappings.put(url, mapping);
                        System.out.println("  " + url + " -> " + method.getName() + "()");
                    }
                }
            }
            
        } catch (Exception e) {
            e.printStackTrace();
        }
        
        return urlMappings;
    }
}

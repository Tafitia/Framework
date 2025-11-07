 package myframework.util;

import myframework.Controller;
import myframework.UrlAnnotation;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

public class AnnotationScanner {
    
    // Scanner simple : affiche directement les classes @Controller et leurs méthodes @UrlAnnotation
    public static void scanAndDisplay(PrintWriter out) {
        try {
            out.println("<html><head><title>Controllers & Urls</title></head><body>");
            out.println("<h2>Liste des Controllers et URLs</h2>");
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            Enumeration<URL> resources = cl.getResources("");
            while (resources.hasMoreElements()) {
                URL resource = resources.nextElement();
                String protocol = resource.getProtocol();
                if ("file".equals(protocol)) {
                    scanDirSimple(new File(URLDecoder.decode(resource.getFile(), "UTF-8")), "", out);
                } else if ("jar".equals(protocol)) {
                    String jarPath = resource.getPath().substring(5, resource.getPath().indexOf("!"));
                    scanJarSimple(jarPath, out);
                }
            }
            out.println("</body></html>");
        } catch (Exception e) {
            out.println("Erreur: " + e.getMessage());
        }
    }
    
    // Scanner simple d'un répertoire
    private static void scanDirSimple(File dir, String packageName, PrintWriter out) {
        if (!dir.exists()) return;
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File file : files) {
            if (file.isDirectory()) {
                String newPackage = packageName.isEmpty() ? file.getName() : packageName + "." + file.getName();
                scanDirSimple(file, newPackage, out);
            } else if (file.getName().endsWith(".class")) {
                String className = packageName + "." + file.getName().substring(0, file.getName().length() - 6);
                try {
                    Class<?> clazz = Class.forName(className);
                    if (clazz.isAnnotationPresent(Controller.class)) {
                        out.println("<div>" + clazz.getName() + "<ul>");
                        for (Method m : clazz.getDeclaredMethods()) {
                            if (m.isAnnotationPresent(UrlAnnotation.class)) {
                                UrlAnnotation ann = m.getAnnotation(UrlAnnotation.class);
                                out.println("<li>" + m.getName() + " &rarr; <b>" + ann.url() + "</b></li>");
                            }
                        }
                        out.println("</ul></div>");
                    }
                } catch (Throwable e) {
                    // ignorer
                }
            }
        }
    }
    
    // Scanner simple d'un JAR
    private static void scanJarSimple(String jarPath, PrintWriter out) {
        JarFile jarFile = null;
        try {
            jarFile = new JarFile(URLDecoder.decode(jarPath, "UTF-8"));
            Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                JarEntry entry = entries.nextElement();
                String entryName = entry.getName();
                if (entryName.endsWith(".class")) {
                    String className = entryName.replace("/", ".").substring(0, entryName.length() - 6);
                    try {
                        Class<?> clazz = Class.forName(className);
                        if (clazz.isAnnotationPresent(Controller.class)) {
                            out.println("<div></b> " + clazz.getName() + "<ul>");
                            for (Method m : clazz.getDeclaredMethods()) {
                                if (m.isAnnotationPresent(UrlAnnotation.class)) {
                                    UrlAnnotation ann = m.getAnnotation(UrlAnnotation.class);
                                    out.println("<li>" + m.getName() + " &rarr; <b>" + ann.url() + "</b></li>");
                                }
                            }
                            out.println("</ul></div>");
                        }
                    } catch (Throwable e) {
                        // ignorer
                    }
                }
            }
        } catch (Exception e) {
            // ignorer
        } finally {
            if (jarFile != null) {
                try { jarFile.close(); } catch (IOException e) { }
            }
        }
    }
}

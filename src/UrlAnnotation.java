package myframework;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) 
@Target(ElementType.METHOD)
public @interface UrlAnnotation {
    String url();
}

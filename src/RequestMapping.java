package myframework;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) 
@Target(ElementType.METHOD)
public @interface RequestMapping {
    String value() default "GET";
}

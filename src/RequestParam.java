package myframework;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME) 
@Target(ElementType.PARAMETER)
public @interface RequestParam {
    String value() default "";
}

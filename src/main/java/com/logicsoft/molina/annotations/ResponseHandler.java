package com.logicsoft.molina.annotations;

import java.lang.annotation.*;

@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface ResponseHandler {
    int status() default 200;
    boolean result() default true;
}
package com.seektop.common.mvc.aop;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
@Documented
public @interface IgnoreOperationLog {
    String name() default "";
}

package com.seektop.common.annotation;

import java.lang.annotation.*;

@Documented
@Inherited
@Target({ ElementType.FIELD, ElementType.METHOD,ElementType.PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
public @interface STRequestPath {

}

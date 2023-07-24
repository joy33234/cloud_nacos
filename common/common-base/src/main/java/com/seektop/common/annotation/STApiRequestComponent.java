package com.seektop.common.annotation;

import java.lang.annotation.*;


@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface STApiRequestComponent {

    /**
     * @return
     */
    String value() default "";

    /**
     * 是否要将标识此注解的类注册为Spring的Bean
     *
     * @return
     */
    boolean registerBean() default true;
    
}

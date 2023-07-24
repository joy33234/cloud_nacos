package com.seektop.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @author Ace
 * @date 2020/2/15 17:29
 * @Desc
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface FrequencyLimit {

    /**
     *  最多second()秒请求一次
     */
    int second() default 2;
}

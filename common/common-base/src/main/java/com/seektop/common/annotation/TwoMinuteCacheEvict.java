package com.seektop.common.annotation;

import org.springframework.cache.annotation.CacheEvict;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD})
@CacheEvict(cacheNames = "lt-cfg-2min", cacheManager = "cacheManager")
public @interface TwoMinuteCacheEvict {
    String key() default "";

    boolean allEntries() default false;
}

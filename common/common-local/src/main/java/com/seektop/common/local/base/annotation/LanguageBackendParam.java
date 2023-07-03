package com.seektop.common.local.base.annotation;

import java.lang.annotation.*;

/**
 * 仅仅适用于 application/x-www-form-urlencoded
 */
@Documented
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface LanguageBackendParam {

    String name();

}

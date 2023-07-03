package com.seektop.common.encrypt.annotation;




import com.seektop.common.encrypt.parse.DefaultResultParse;
import com.seektop.common.encrypt.parse.ResultParse;

import java.lang.annotation.*;


/**
 * 注解的字段
 */
@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface Encrypt {
    EncryptField[] values();

    Class<? extends ResultParse> resultParse() default DefaultResultParse.class;
}

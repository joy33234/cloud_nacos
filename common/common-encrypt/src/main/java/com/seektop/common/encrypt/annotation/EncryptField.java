package com.seektop.common.encrypt.annotation;


import com.seektop.common.encrypt.enums.EncryptTypeEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.Inherited;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
public @interface EncryptField {
    /**
     * 脱敏字段
     */
     String fieldName() default "";

    /**
     * 脱敏类型
     */
    EncryptTypeEnum[] typeEnums();

}

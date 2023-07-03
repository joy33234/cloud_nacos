package com.seektop.common.encrypt.annotation;


import com.seektop.common.encrypt.EncryptHelper;
import com.seektop.common.encrypt.aspect.EncryptAspect;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@Import({EncryptHelper.class, EncryptAspect.class})
public @interface EnableEncrypt {
}

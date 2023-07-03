package com.seektop.common.annotation;

import com.seektop.common.http.enums.STHttpContentType;
import com.seektop.common.http.enums.STHttpMethod;
import com.seektop.common.http.enums.STRResultParser;
import com.seektop.common.http.enums.STRequestJsonType;

import java.lang.annotation.*;

@Documented
@Inherited
@Target({ ElementType.FIELD, ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
public @interface STRequest {

    /**
     * 请求方式
     * @return
     */
    STHttpMethod method();

    /**
     * 请求路径
     * @return
     */
    String url() default "";

    /**
     * 是否支持重定向
     * @return
     */
    boolean redirect() default false;

    /**
     * 超时时间：默认是-1 表示跟随设定
     * @return
     */
    int timeout() default -1;

    STHttpContentType ContentType() default STHttpContentType.FORM;

    STRequestJsonType jsonType() default STRequestJsonType.DEFAULT;

    STRResultParser resultParser() default STRResultParser.DEFAULT;

    boolean showResponse() default false;

}

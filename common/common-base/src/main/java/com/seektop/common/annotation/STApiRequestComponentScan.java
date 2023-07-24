package com.seektop.common.annotation;

import com.seektop.common.http.component.STApiRequestBeanDefinitionRegistrar;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;


@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Import(STApiRequestBeanDefinitionRegistrar.class)
public @interface STApiRequestComponentScan {

    /**
     * @return
     */
    String[] value() default {};

    /**
     * 扫描包
     *
     * @return
     */
    String[] basePackages() default {};

    /**
     * 扫描的基类
     *
     * @return
     */
    Class<?>[] basePackageClasses() default {};

    /**
     * 包含过滤器
     *
     * @return
     */
    Filter[] includeFilters() default {};

    /**
     * 排斥过滤器
     *
     * @return
     */
    Filter[] excludeFilters() default {};
    
}

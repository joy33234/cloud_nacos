package com.seektop.common.local.base.annotation;

import com.seektop.common.local.context.LanguageDataSourceContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Documented
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Configuration
@EnableDatasource
public @interface LanguageDataSourceConfiguration {
}

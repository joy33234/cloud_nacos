package com.seektop.common.freemarker.annotation;


import com.seektop.common.freemarker.configuration.FreemarkerConfiguration;
import com.seektop.common.freemarker.runner.InitRunner;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.context.annotation.Import;

import java.lang.annotation.*;

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Inherited
@ImportAutoConfiguration(classes = {FreemarkerConfiguration.class})
@Import({InitRunner.class})
public @interface EnableFreemarker {
}

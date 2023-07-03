package com.seektop.common.local.mvc;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurationSupport;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Configuration
public class LanguageWebmvcConfiguration implements WebMvcConfigurer {

    @Resource
    private LanguageParamResolver languageParamResolver;

    @Override
    public void addArgumentResolvers(List<HandlerMethodArgumentResolver> argumentResolvers) {
        log.info("参数解析器加载成功");
        argumentResolvers.add(languageParamResolver);
    }
}

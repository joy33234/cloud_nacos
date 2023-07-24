package com.seektop.common.freemarker.runner;


import com.seektop.common.freemarker.service.FreemarkerHandler;
import freemarker.cache.StringTemplateLoader;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;

import javax.annotation.Resource;

public class InitRunner implements ApplicationRunner {
    @Resource
    private StringTemplateLoader stringTemplateLoader;

    @Resource
    private FreemarkerHandler freemarkerHandler;


    @Override
    public void run(ApplicationArguments args) throws Exception {
        freemarkerHandler.init(stringTemplateLoader);
    }
}

package com.seektop.common.freemarker.service;


import com.seektop.exception.GlobalException;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Resource;
import java.io.IOException;
import java.io.StringWriter;

@Slf4j
public abstract class FreemarkerHandler {

    @Resource(name = "ftlConfiguration")
    public freemarker.template.Configuration configuration;

    public abstract void init(StringTemplateLoader stringTemplateLoader);

    public String parse(String key,Object content) throws GlobalException {
        log.info("当前的key:{}",key);
        Template template = null;
        StringWriter writer = new StringWriter();
        try {
            template = configuration.getTemplate(key,"utf-8");
            template.process(content,writer);
            return writer.toString();
        } catch (IOException e) {
            log.error("模板解析出现了异常",e);
            throw new GlobalException("");
        } catch (TemplateException e) {
            log.error("模板解析出现了异常",e);
            throw new GlobalException("模板获取出现异常",e);
        }
    }
}

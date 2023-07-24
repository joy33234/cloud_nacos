package com.seektop.common.freemarker;

import com.alibaba.fastjson.JSON;
import freemarker.cache.StringTemplateLoader;
import freemarker.template.Configuration;
import freemarker.template.Template;
import freemarker.template.TemplateException;
import lombok.extern.slf4j.Slf4j;
import java.io.IOException;
import java.io.StringWriter;


@Slf4j
public class ParserUtils {

    /**
     * 不需要一次性加载所有的模板
     * @param content
     * @param paramMap
     * @return
     * @throws IOException
     */
    public static String parse(String content, Object paramMap) throws IOException {
        StringWriter writer = new StringWriter();
        Configuration configuration = new Configuration(Configuration.VERSION_2_3_0);
        StringTemplateLoader stringLoader = new StringTemplateLoader();
        stringLoader.putTemplate("temp", content);
        configuration.setTemplateLoader(stringLoader);
        Template template = configuration.getTemplate("temp","utf-8");
        try {
            template.process(JSON.toJSON(paramMap), writer);
            return writer.toString();
        } catch (TemplateException | IOException e) {
            log.error("ftl解析失败",e);
        }
        return content;
    }
}

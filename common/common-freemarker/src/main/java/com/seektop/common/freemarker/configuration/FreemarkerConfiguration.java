package com.seektop.common.freemarker.configuration;


import freemarker.cache.StringTemplateLoader;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

public class FreemarkerConfiguration {

    @Bean(name = "ftlConfiguration")
    public freemarker.template.Configuration configuration( StringTemplateLoader stringLoader){
        freemarker.template.Configuration configuration = new freemarker.template.Configuration(freemarker.template.Configuration.VERSION_2_3_0);
        configuration.setTemplateLoader(stringLoader);
        return configuration;
    }

    @Bean
    public StringTemplateLoader stringTemplateLoader(){
        StringTemplateLoader stringLoader = new StringTemplateLoader();
        return stringLoader;
    }


}

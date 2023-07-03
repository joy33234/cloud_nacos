package com.seektop.common.gateway.filter;

import lombok.Getter;
import lombok.Setter;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.PropertySourcesPlaceholderConfigurer;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

@Getter
@Setter
@Component
@Configuration
@ConfigurationProperties(prefix = "exclude")
public class FilterConfig {

    /**
     * 签名需要排除的url
     */
    private List<String> sign;

    /**
     * token需要排除的url
     */
    private List<String> token;

    /**
     * 应用密钥需要排除的url
     */
    private List<String> secret;

    @Bean
    public static PropertySourcesPlaceholderConfigurer properties() {
        PropertySourcesPlaceholderConfigurer configurer = new PropertySourcesPlaceholderConfigurer();
        YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
        yaml.setResources(new ClassPathResource("filter/exclude.yml"));
        configurer.setProperties(yaml.getObject());
        return configurer;
    }

}
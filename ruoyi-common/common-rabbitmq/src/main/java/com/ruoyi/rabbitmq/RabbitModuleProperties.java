package com.ruoyi.rabbitmq;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * 绑定配置基础类
 * @version 1.0
 * @date 2023年04月11日 14:58
 */
@Data
@Configuration
@ConfigurationProperties("spring.rabbitmq")
public class RabbitModuleProperties {

    /**
     * 模块配置
     */
    List<ModuleProperties> modules;

}


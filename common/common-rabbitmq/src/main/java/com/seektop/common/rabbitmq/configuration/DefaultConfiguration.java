package com.seektop.common.rabbitmq.configuration;

import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration("rabbitmqDefaultConfiguration")
@DependsOn(value = {"rabbitmqConnectionFactoryConfiguration"})
public class DefaultConfiguration {

    @Bean(name = "defaultRabbitAdmin")
    public RabbitAdmin defaultRabbitAdmin(@Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        return rabbitAdmin;
    }

    @Bean(name = "defaultRabbitTemplate")
    public RabbitTemplate defaultRabbitTemplate(@Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate hospSyncRabbitTemplate = new RabbitTemplate(connectionFactory);
        return hospSyncRabbitTemplate;
    }

    @Bean(name = "defaultRabbitListener")
    public SimpleRabbitListenerContainerFactory defaultRabbitListener(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Value("${global.default.customer.prefetch.size}") Integer prefetchSize,
            @Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setPrefetchCount(prefetchSize);
        configurer.configure(factory, connectionFactory);
        return factory;
    }

}
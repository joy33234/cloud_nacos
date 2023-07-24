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

@Configuration("rabbitmqUserConfiguration")
@DependsOn(value = {"rabbitmqConnectionFactoryConfiguration"})
public class UserConfiguration {

    @Bean(name = "userRabbitAdmin")
    public RabbitAdmin userRabbitAdmin(@Qualifier("userConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        return rabbitAdmin;
    }

    @Bean(name = "userRabbitTemplate")
    public RabbitTemplate userRabbitTemplate(@Qualifier("userConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate hospSyncRabbitTemplate = new RabbitTemplate(connectionFactory);
        return hospSyncRabbitTemplate;
    }

    @Bean(name = "userRabbitListener")
    public SimpleRabbitListenerContainerFactory userRabbitListener(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Value("${global.user.save.to.elasticsearch.customer.prefetch.size}") Integer prefetchSize,
            @Qualifier("userConnectionFactory") ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setPrefetchCount(prefetchSize);
        configurer.configure(factory, connectionFactory);
        return factory;
    }

}
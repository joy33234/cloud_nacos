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

@Configuration("rabbitmqReportConfiguration")
@DependsOn(value = {"rabbitmqConnectionFactoryConfiguration"})
public class ReportConfiguration {

    @Bean(name = "reportRabbitAdmin")
    public RabbitAdmin reportRabbitAdmin(@Qualifier("reportConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        return rabbitAdmin;
    }

    @Bean(name = "reportRabbitTemplate")
    public RabbitTemplate reportRabbitTemplate(@Qualifier("reportConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitTemplate hospSyncRabbitTemplate = new RabbitTemplate(connectionFactory);
        return hospSyncRabbitTemplate;
    }

    @Bean(name = "reportRabbitListener")
    public SimpleRabbitListenerContainerFactory reportRabbitListener(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Value("${global.event.save.to.elasticsearch.customer.prefetch.size}") Integer prefetchSize,
            @Qualifier("reportConnectionFactory") ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setPrefetchCount(prefetchSize);
        configurer.configure(factory, connectionFactory);
        return factory;
    }

}
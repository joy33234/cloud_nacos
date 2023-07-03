package com.seektop.fund.customer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.amqp.SimpleRabbitListenerContainerFactoryConfigurer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Slf4j
@Configuration
public class WithdrawMqConfig {

    @Value("${withdraw.queue}")
    private String queue;
    @Value("${withdraw.exchange}")
    private String exchange;
    @Value("${withdraw.routekey}")
    private String routeKey;

    @Resource(name = "withdrawRabbitAdmin")
    private RabbitAdmin rabbitAdmin;

    @Bean("withdrawMessageConverter")
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return new Jackson2JsonMessageConverter(mapper);
    }

    @Bean(name = "withdrawRabbitTemplate")
    public RabbitTemplate rabbitTemplate(@Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory,
                                         @Qualifier("withdrawMessageConverter") MessageConverter converter) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setEncoding("UTF-8");
        rabbitTemplate.setConfirmCallback(confirmCallback);
        rabbitTemplate.setReturnCallback(returnCallback);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }

    @Bean(name = "withdrawRabbitAdmin")
    public RabbitAdmin rabbitAdmin(@Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory) {
        RabbitAdmin rabbitAdmin = new RabbitAdmin(connectionFactory);
        return rabbitAdmin;
    }

    @Bean(name = "withdrawRabbitListener")
    public SimpleRabbitListenerContainerFactory rabbitListener(
            SimpleRabbitListenerContainerFactoryConfigurer configurer,
            @Value("${withdraw.spring.rabbitmq.listener.simple.prefetch}") Integer prefetchSize,
            @Qualifier("defaultConnectionFactory") ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setPrefetchCount(prefetchSize);
        configurer.configure(factory, connectionFactory);
        return factory;
    }

    @PostConstruct
    public void initQueue() {
        // 通知相关Exchange、Queue、Bind
        Exchange exchange = ExchangeBuilder.directExchange(this.exchange).durable(true).delayed().build();
        rabbitAdmin.declareExchange(exchange);
        Queue queue = QueueBuilder.durable(this.queue).build();
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(queue).to(exchange).with(this.routeKey).noargs();
        rabbitAdmin.declareBinding(binding);
        log.debug("WithdrawMQ初始化完成");
    }

    public final RabbitTemplate.ConfirmCallback confirmCallback = (correlationData, ack, cause) -> {
        if (ack) {
            log.info("发送自动出款信息成功！");
        }
        else {
            log.error("发送自动出款信息失败，cause:{}, correlationData:{}", cause, correlationData.toString());
        }
    };

    public final RabbitTemplate.ReturnCallback returnCallback = (message, replyCode, replyText, exchange, routingKey) ->
            log.warn("ReturnCallback:{},{},{},{},{},{}", message, replyCode, replyText, exchange, routingKey);
}

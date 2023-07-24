package com.seektop.fund.customer;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ConfirmCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ReturnCallback;
import org.springframework.amqp.support.converter.ClassMapper;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration("activityMQConfig")
public class ActivityMQConfig implements ConfirmCallback, ReturnCallback {

    @Value("${activity.queue}")
    private String queue;

    @Value("${activity.exchange}")
    private String exchange;

    @Value("${activity.routekey}")
    private String routeKey;

    @Value("${activity.dead-queue}")
    private String deadQueue;

    @Value("${activity.dead-exchange}")
    private String deadExchange;

    @Value("${activity.dead-routekey}")
    private String deadRouteKey;

    @Resource(name = "ActivityReportRabbitAdmin")
    private RabbitAdmin rabbitAdmin;

    /*@Bean(name="ActivityReportMessageConverter")
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        return new Jackson2JsonMessageConverter(mapper);
    }*/

    //note: 上面的序列化不能与ml老系统兼容，消息能发到mq，但不能被消费
    @Bean(name="ActivityReportMessageConverter")
    public MessageConverter messageConverter() {
        ObjectMapper mapper = new ObjectMapper();
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter(mapper);
        converter.setClassMapper(new ClassMapper() {
            @Override
            public Class<?> toClass(MessageProperties properties) {
                throw new UnsupportedOperationException("this mapper is only for outbound, do not use for receive message");
            }
            @Override
            public void fromClass(Class<?> clazz, MessageProperties properties) {
                properties.setHeader("__TypeId__", "com.betball.report.model.BankRechargeReport");
            }
        });
        return converter;
    }

    @Bean(name="ActivityReportConnectionFactory")
    @ConfigurationProperties(prefix="activity.rabbitmq")
    public ConnectionFactory connectionFactory() {
        CachingConnectionFactory connectionFactory = new CachingConnectionFactory();
        connectionFactory.setPublisherReturns(true);
        connectionFactory.setPublisherConfirms(true);
        return connectionFactory;
    }

    @Bean(name="bankActrabbitListenerContainerFactory")
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            @Qualifier("ActivityReportConnectionFactory") ConnectionFactory connectionFactory,
            @Qualifier("ActivityReportMessageConverter") MessageConverter messageConverter
    ) {
        SimpleRabbitListenerContainerFactory simpleRabbitListenerContainerFactory = new SimpleRabbitListenerContainerFactory();
        simpleRabbitListenerContainerFactory.setConnectionFactory(connectionFactory);
        simpleRabbitListenerContainerFactory.setMessageConverter(messageConverter);
        simpleRabbitListenerContainerFactory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return simpleRabbitListenerContainerFactory;
    }

    @Bean(name="ActivityReportRabbitTemplate")
    public RabbitTemplate rabbitTemplate(
            @Qualifier("ActivityReportMessageConverter") MessageConverter converter,
            @Qualifier("ActivityReportConnectionFactory") ConnectionFactory connectionFactory
    ) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMandatory(true);
        rabbitTemplate.setEncoding("UTF-8");
        rabbitTemplate.setReturnCallback(this);
        rabbitTemplate.setConfirmCallback(this);
        rabbitTemplate.setMessageConverter(converter);
        return rabbitTemplate;
    }

    @Bean(name="ActivityReportRabbitAdmin")
    public RabbitAdmin rabbitAdmin(@Qualifier("ActivityReportConnectionFactory") ConnectionFactory connectionFactory) {
        return new RabbitAdmin(connectionFactory);
    }

    @PostConstruct
    public void initQueue() {
        // 通知相关Exchange、Queue、Bind
        Exchange exchange = activityReportExchange();
        rabbitAdmin.declareExchange(exchange);
        Queue queue = activityReportQueue();
        rabbitAdmin.declareQueue(queue);
        Binding binding = BindingBuilder.bind(activityReportQueue()).to(activityReportExchange()).with(this.routeKey);
        rabbitAdmin.declareBinding(binding);


        Exchange deadExchange = activityReportDeadExchange();
        rabbitAdmin.declareExchange(deadExchange);
        Queue deadQueue = activityReportDeadQueue();
        rabbitAdmin.declareQueue(deadQueue);
        Binding deadBinding = BindingBuilder.bind(activityReportDeadQueue()).to(activityReportDeadExchange()).with(this.deadRouteKey);
        rabbitAdmin.declareBinding(deadBinding);
        log.debug("activity初始化完成");
    }

    public Queue activityReportQueue() {
        log.info("create queue:{}", this.queue);
        Map<String, Object> args = new HashMap<>();
        args.put("x-dead-letter-exchange", this.deadExchange);
        args.put("x-dead-letter-routing-key", this.deadRouteKey);
        return new Queue(this.queue, true, false, false, args);
    }

    public DirectExchange activityReportExchange() {
        log.info("create exchange:{}", this.exchange);
        return new DirectExchange(this.exchange, true, false);
    }

    // dead letter
    public Queue activityReportDeadQueue() {
        log.info("create dead queue:{}", this.deadQueue);
        return new Queue(this.deadQueue);
    }

    public DirectExchange activityReportDeadExchange() {
        log.info("create dead exchange:{}", this.deadExchange);
        return new DirectExchange(this.deadExchange);
    }

    /**
     * producer->broker->exchange->queue->consumer
     * 从 producer->broker 触发 confirmCallback
     */
    @Override
    public void confirm(CorrelationData correlationData, boolean ack, String cause) {
        if (!ack) {
            log.error("confirm error,原因: {}", cause);
        }
    }

    /**
     * producer->broker->exchange->queue->consumer
     * 从 exchange->queue 投递失败则会触发 returnCallback
     */
    @Override
    public void returnedMessage(Message message, int replyCode, String replyText, String exchange, String routingKey) {
        log.warn("ReturnCallback:{},{},{},{},{},{}", message, replyCode, replyText, exchange, routingKey);
    }
}
package com.seektop.common.rabbitmq.producer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seektop.report.user.UserBaseReport;
import com.seektop.report.user.UserSynch;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Component
public class UserProducer {

    @Value("${global.user.exchange}")
    private String exchange;
    @Value("${global.user.routekey}")
    private String routeKey;
    @Value("${global.user.other.routekey}")
    private String otherRouteKey;

    @Resource(name = "userRabbitTemplate")
    private RabbitTemplate userRabbitTemplate;
    @Autowired
    private CustomMessagePostProcessor customMessagePostProcessor;
    @Autowired
    private CustomCallback customCallback;

    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        userRabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
        userRabbitTemplate.setMandatory(true);
        userRabbitTemplate.setEncoding("UTF-8");
        userRabbitTemplate.setReturnCallback(customCallback);
        userRabbitTemplate.setConfirmCallback(customCallback);
        userRabbitTemplate.setExchange(exchange);
        userRabbitTemplate.setRoutingKey(routeKey);
    }

    public void sendMessage(UserSynch userSynch) throws AmqpException {
        try {
            userRabbitTemplate.convertAndSend(userSynch, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public <T extends UserBaseReport> void sendMessage(T report) throws AmqpException {
        try {
            userRabbitTemplate.convertAndSend(otherRouteKey, report, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

}
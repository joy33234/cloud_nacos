package com.seektop.common.rabbitmq.producer;

import com.seektop.common.rabbitmq.configuration.JpushNotificationConfiguration;
import com.seektop.report.system.JpushMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

@Slf4j
@Component
@ConditionalOnBean(JpushNotificationConfiguration.class)
public class JpushSender {

    @Resource(name = "jpushRabbitTemplate")
    private RabbitTemplate rabbitTemplate;
    @Autowired
    private CustomCallback customCallback;
    @Value("${jpush.fanout.exchange}")
    private String exchange;

    /**
     * 发布消息
     * @param message
     */
    public void send(JpushMessage message){
        log.info("发送MQ推送的消息：{}", message);
        //设置确认回调
        rabbitTemplate.setConfirmCallback(customCallback);
        rabbitTemplate.setReturnCallback(customCallback);
        rabbitTemplate.convertAndSend(exchange, "", message);
    }
}

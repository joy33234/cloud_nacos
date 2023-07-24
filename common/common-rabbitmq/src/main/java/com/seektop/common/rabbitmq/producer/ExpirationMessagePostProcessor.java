package com.seektop.common.rabbitmq.producer;

import com.seektop.constant.ContentType;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;

import java.util.Date;
import java.util.UUID;

public class ExpirationMessagePostProcessor implements MessagePostProcessor {

    private Long ttl;

    public ExpirationMessagePostProcessor(Long ttl) {
        this.ttl = ttl;
    }

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        message.getMessageProperties().setTimestamp(new Date());
        message.getMessageProperties().setContentType(ContentType.JSON);
        message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
        message.getMessageProperties().setDeliveryMode(MessageDeliveryMode.PERSISTENT);
        // 设置过期时间
        message.getMessageProperties().setExpiration(ttl.toString());
        return message;
    }

}
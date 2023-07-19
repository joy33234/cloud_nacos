package com.ruoyi.rabbitmq;

import org.springframework.amqp.rabbit.core.ChannelAwareMessageListener;

/**
 * 消费者接口
 * @version 1.0
 * @date 2023年04月11日 13:52
 */
public interface ConsumerService extends ChannelAwareMessageListener {

}

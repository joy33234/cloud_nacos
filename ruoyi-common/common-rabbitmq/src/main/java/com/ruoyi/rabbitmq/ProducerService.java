package com.ruoyi.rabbitmq;

public interface ProducerService {

    /**
     * 发送消息
     * @param message
     */
    void send(Object message);


    void send(Object message, String routingKey);
}

package com.ruoyi.rabbitmq;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.reflect.TypeToken;
import com.rabbitmq.client.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;

import java.io.IOException;

/**
 * @version 1.0
 * @date 2023年04月18日 17:53
 */
@Slf4j
public abstract class AbsConsumerService<T> implements ConsumerService {

    private Class<T> clazz = (Class<T>) new TypeToken<T>(getClass()) {}.getRawType();

    /**
     * 消息
     */
    private Message message;

    /**
     * 通道
     */
    private Channel channel;


    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        this.message = message;
        this.channel = channel;
        String body = new String(message.getBody());
        onConsumer(genObject(body));
    }

    /**
     * 根据反射获取泛型
     * @param body
     * @return
     */
    private T genObject(String body) throws JsonProcessingException, IllegalAccessException, InstantiationException {
        try {
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(body, clazz);
        }catch (Exception e) {
            log.error("MQ转发层错误，请检查泛型是否与实际类型匹配, 指定的泛型是: {}", clazz.getName(), e);
        }
        return clazz.newInstance();
    }

    /**
     * 扩展消费方法，对消息进行封装
     * @param data
     * @throws IOException
     */
    public void  onConsumer(T data) throws IOException {
        log.error("未对此方法进行实现: {}", data);
    }

    /**
     * 确认消息
     */
    protected void ack() throws IOException {
        ack(Boolean.FALSE);
    }

    /**
     * 拒绝消息
     */
    protected void nack() throws IOException {
        nack(Boolean.FALSE, Boolean.FALSE);
    }

    /**
     * 拒绝消息
     */
    protected void basicReject() throws IOException {
        basicReject(Boolean.FALSE);
    }

    /**
     * 拒绝消息
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    protected void basicReject(Boolean multiple) throws IOException {
        this.channel.basicReject(this.message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 是否自动确认
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     */
    protected void ack(Boolean multiple) throws IOException {
        this.channel.basicAck(this.message.getMessageProperties().getDeliveryTag(), multiple);
    }

    /**
     * 拒绝消息
     * @param multiple 当前 DeliveryTag 的消息是否确认所有 true 是， false 否
     * @param requeue 当前 DeliveryTag 消息是否重回队列 true 是 false 否
     */
    protected void nack(Boolean multiple, Boolean requeue) throws IOException {
        this.channel.basicNack(this.message.getMessageProperties().getDeliveryTag(), multiple, requeue);
    }
}

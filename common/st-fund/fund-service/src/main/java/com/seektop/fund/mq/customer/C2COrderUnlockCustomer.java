package com.seektop.fund.mq.customer;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.constant.mq.QueueConstant;
import com.seektop.fund.handler.C2COrderHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
@DependsOn(value = {"fundQueueConfiguration"})
public class C2COrderUnlockCustomer {

    @Resource
    private C2COrderHandler c2COrderHandler;

    @RabbitListener(queues = QueueConstant.C2C_ORDER_UNLOCK_CHECK_PROGRESS, containerFactory = "reportRabbitListener")
    public void receiveNotice(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        JSONObject dataObj = JSONObject.parseObject(new String(data));
        if (dataObj.containsKey("withdrawOrderId") == false) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        String withdrawOrderId = dataObj.getString("withdrawOrderId");
        if (StringUtils.isEmpty(withdrawOrderId)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            c2COrderHandler.submitUnlock(withdrawOrderId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("C2C充提撮合订单{}延时自动解锁时发生异常", withdrawOrderId, ex);
        }
    }

}
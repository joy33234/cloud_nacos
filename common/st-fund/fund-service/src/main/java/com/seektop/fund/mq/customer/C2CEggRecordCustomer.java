package com.seektop.fund.mq.customer;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.constant.mq.QueueConstant;
import com.seektop.fund.handler.C2CEggRecordHandler;
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
public class C2CEggRecordCustomer {

    @Resource
    private C2CEggRecordHandler c2CEggRecordHandler;

    @RabbitListener(queues = QueueConstant.C2C_EGG_RECORD_DELAY, containerFactory = "reportRabbitListener")
    public void receiveNotice(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        JSONObject dataObj = JSONObject.parseObject(new String(data));
        if (dataObj.containsKey("recordId") == false) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        Integer recordId = dataObj.getInteger("recordId");
        if (StringUtils.isEmpty(recordId)) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            c2CEggRecordHandler.submitStop(recordId);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("C2C彩蛋倒计时{}时发生异常", recordId, ex);
        }
    }

}
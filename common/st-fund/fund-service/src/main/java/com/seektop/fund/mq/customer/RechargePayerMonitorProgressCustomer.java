package com.seektop.fund.mq.customer;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.constant.mq.QueueConstant;
import com.seektop.fund.handler.RechargePayerMonitorHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

@Slf4j
@Component
@DependsOn(value = {"fundQueueConfiguration"})
public class RechargePayerMonitorProgressCustomer {

    @Resource
    private RechargePayerMonitorHandler rechargePayerMonitorHandler;

    @RabbitListener(queues = QueueConstant.RECHARGE_PAYER_MONITOR_PROGRESS, containerFactory = "reportRabbitListener")
    public void receiveNotice(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            JSONObject dataObj = JSONObject.parseObject(new String(data));
            if (dataObj.containsKey("event") == false) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            if (dataObj.getIntValue("event") != 1000) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            JSONObject rechargeDataObj = dataObj.getJSONObject("1000");
            if (rechargeDataObj.containsKey("status") == false) {
                channel.basicAck(deliveryTag, false);
                return;
            }
            rechargePayerMonitorHandler.monitorFromUserId(dataObj.getInteger("uid"), getPayerName(rechargeDataObj));
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("[充值付款人姓名监控]处理时发生异常", ex);
        }
    }

    protected String getPayerName(JSONObject rechargeDataObj) {
        if (rechargeDataObj.containsKey("keyword") == false) {
            return null;
        }
        String keyword = rechargeDataObj.getString("keyword");
        if (keyword.indexOf("||") > -1) {
            return keyword.split("\\|\\|")[0];
        } else {
            return null;
        }
    }

}
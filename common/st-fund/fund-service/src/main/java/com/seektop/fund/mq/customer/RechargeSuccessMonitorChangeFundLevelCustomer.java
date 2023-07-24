package com.seektop.fund.mq.customer;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.constant.mq.QueueConstant;
import com.seektop.fund.handler.RechargeSuccessMonitorHandler;
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
public class RechargeSuccessMonitorChangeFundLevelCustomer {

    @Resource
    private RechargeSuccessMonitorHandler rechargeSuccessMonitorHandler;

    @RabbitListener(queues = QueueConstant.RECHARGE_SUCCESS_MONITOR_CHANGE_FUND_LEVEL, containerFactory = "reportRabbitListener")
    public void receiveNotice(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        JSONObject dataObj = JSONObject.parseObject(new String(data));
        if (dataObj.containsKey("event") == false) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        if (dataObj.getIntValue("event") != 1000) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        log.info("收到的充值数据是{}", dataObj.toJSONString());
        try {
            rechargeSuccessMonitorHandler.checkChangeFundLevel(dataObj);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("处理充值成功监控更改用户财务层级时发生异常", ex);
        }
    }

}
package com.seektop.fund.mq.customer;

import com.alibaba.fastjson.JSONObject;
import com.rabbitmq.client.Channel;
import com.seektop.constant.mq.QueueConstant;
import com.seektop.fund.handler.RechargeEffectBetHandler;
import com.seektop.report.fund.RechargeEffectBetReport;
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
public class RechargeEffectBetCustomer {

    @Resource
    private RechargeEffectBetHandler rechargeEffectBetHandler;

    @RabbitListener(queues = QueueConstant.DIGITAL_RECHARGE_EFFECT_BET_PROGRESS, containerFactory = "reportRabbitListener")
    public void receiveNotice(Channel channel, Message message) throws IOException {
        long deliveryTag = message.getMessageProperties().getDeliveryTag();
        byte[] data = message.getBody();
        if (data == null || data.length <= 0) {
            channel.basicAck(deliveryTag, false);
            return;
        }
        try {
            JSONObject dataObj = JSONObject.parseObject(new String(data));
            RechargeEffectBetReport report = dataObj.toJavaObject(RechargeEffectBetReport.class);
            rechargeEffectBetHandler.rechargeEffectBet(report);
            channel.basicAck(deliveryTag, false);
        } catch (Exception ex) {
            log.error("充值成功处理提现所需流水时发生异常", ex);
        }
    }

}
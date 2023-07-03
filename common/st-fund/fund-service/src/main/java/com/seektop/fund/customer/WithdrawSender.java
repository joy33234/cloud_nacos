package com.seektop.fund.customer;

import com.alibaba.fastjson.JSON;
import com.seektop.report.fund.WithdrawMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.UUID;

/**
 * 消息生产者例子
 */
@Slf4j
@Service
public class WithdrawSender {
    @Resource(name = "withdrawRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    @Value("${withdraw.exchange}")
    private String exchange;
    @Value("${withdraw.routekey}")
    private String routeKey;

    public void sendWithdrawMsg(WithdrawMessage message) throws Exception {
        log.info("发送自动出款信息中 ----- >{}", JSON.toJSONString(message));
        CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());

        rabbitTemplate.convertAndSend(exchange, routeKey, message, processor -> {
            processor.getMessageProperties().setDelay(1000);
            return processor;
        }, correlationData);
    }
}

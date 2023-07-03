package com.seektop.common.rabbitmq.producer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seektop.enumerate.mq.QueueEnum;
import com.seektop.enumerate.mq.RoutingKeyEnum;
import com.seektop.report.fund.FundBaseReport;
import com.seektop.report.fund.WithdrawMessage;
import com.seektop.report.live.LivePlazaInformationGrabReport;
import com.seektop.report.system.BlackListReport;
import com.seektop.report.system.GlSystemOperationLogEvent;
import com.seektop.report.system.IpProcessReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.UUID;

@Slf4j
@Component
public class DefaultProducer {

    @Value("${global.notice.exchange}")
    private String noticeExchange;
    @Value("${global.notice.routekey}")
    private String noticeRouteKey;

    @Value("${global.fund.exchange}")
    private String fundExchange;
    @Value("${global.fund.routekey}")
    private String fundRouteKey;

    @Value("${global.live.exchange}")
    private String liveExchange;
    @Value("${global.live.fundrecord.delay.routekey}")
    private String liveFundRecordDelayRouteKey;
    @Value("${global.live.plazainformation.grab.routekey}")
    private String livePlazaInformationGrabRouteKey;

    @Value("${st.activity.exchange}")
    private String activityExchange;
    @Value("${st.activity.routekey}")
    private String activityRouteKey;

    @Value("${global.blacklist.exchange}")
    private String blackListExchange;
    @Value("${global.blacklist.setting.delay.routekey}")
    private String blacklistSettingDelayRouteKey;

    @Value("${global.system.exchange}")
    private String systemExchange;
    @Value("${global.system.operation.log.routekey}")
    private String systemOperationLogRouteKey;

    @Resource(name = "defaultRabbitTemplate")
    private RabbitTemplate defaultRabbitTemplate;
    @Autowired
    private CustomMessagePostProcessor customMessagePostProcessor;
    @Autowired
    private CustomCallback customCallback;

    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        defaultRabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
        defaultRabbitTemplate.setMandatory(true);
        defaultRabbitTemplate.setEncoding("UTF-8");
        defaultRabbitTemplate.setReturnCallback(customCallback);
        defaultRabbitTemplate.setConfirmCallback(customCallback);
    }

    @Async
    public void sendAliyunIpParsingReport(IpProcessReport report) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(QueueEnum.aliyun_ip_parsing.exchangeName(), QueueEnum.aliyun_ip_parsing.routingKeyName(), report, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void sendBlacklistSettingReport(BlackListReport report) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(blackListExchange, blacklistSettingDelayRouteKey, report, new ExpirationMessagePostProcessor(report.getTtl()));
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void sendFundReport(FundBaseReport obj) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(fundExchange, fundRouteKey, obj, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
            log.error("fund 投递失败",ex);
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
            log.error("fund 投递异常",e);
        }
    }

    public void sendNotice(Object obj) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(noticeExchange, noticeRouteKey, obj, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void sendLive(Object obj) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(liveExchange, liveFundRecordDelayRouteKey, obj, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void sendActivity(Object obj) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(activityExchange, activityRouteKey, obj, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void systemOperationReport(GlSystemOperationLogEvent logEvent) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(systemExchange, systemOperationLogRouteKey, logEvent, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    public void livePlazaInformationGrab(LivePlazaInformationGrabReport plazaInformationGrabReport) throws AmqpException {
        try {
            defaultRabbitTemplate.convertAndSend(liveExchange, livePlazaInformationGrabRouteKey, plazaInformationGrabReport, customMessagePostProcessor);
        } catch (AmqpException ex) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        } catch (Exception e) {
            // 向MQ发送消息时异常,次数优化采用redis queue实现
            // TODO
        }
    }

    @Async
    public void withdrawPayout(WithdrawMessage withdrawMessage) {
        try {
            defaultRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.withdraw_routing_key.exchangeName(),
                    RoutingKeyEnum.withdraw_routing_key.routingKeyName(),
                    withdrawMessage,
                    processor -> {
                        processor.getMessageProperties().setDelay(1000);
                        return processor;
                    },
                    new CorrelationData(UUID.randomUUID().toString())
            );
        } catch (Exception ex) {
            log.error("发送自动出款信息发生异常", ex);
        }
    }

}
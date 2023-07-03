package com.seektop.common.rabbitmq.producer;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seektop.enumerate.ReportEvent;
import com.seektop.enumerate.mq.RoutingKeyEnum;
import com.seektop.report.BaseReport;
import com.seektop.report.common.BalanceRecordReport;
import com.seektop.report.digital.DigitalRechargeOrderTimeoutDelayReport;
import com.seektop.report.digital.UserDigitalAccountCreateReport;
import com.seektop.report.fund.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.lang.reflect.Field;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
public class ReportProducer {

    @Value("${report.project}")
    private String project;
    @Value("${global.event.exchange}")
    private String exchange;
    @Value("${global.event.routekey}")
    private String routeKey;

    @Resource(name = "reportRabbitTemplate")
    private RabbitTemplate reportRabbitTemplate;
    @Autowired
    private CustomMessagePostProcessor customMessagePostProcessor;
    @Autowired
    private CustomCallback customCallback;

    @PostConstruct
    public void init() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.setSerializationInclusion(JsonInclude.Include.NON_NULL);
        reportRabbitTemplate.setMessageConverter(new Jackson2JsonMessageConverter(mapper));
        reportRabbitTemplate.setMandatory(true);
        reportRabbitTemplate.setEncoding("UTF-8");
        reportRabbitTemplate.setReturnCallback(customCallback);
        reportRabbitTemplate.setConfirmCallback(customCallback);
        reportRabbitTemplate.setExchange(exchange);
        reportRabbitTemplate.setRoutingKey(routeKey);
    }

    public void rechargeEffectBetReport(RechargeEffectBetReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.digital_recharge_effect_bet_progress_routing_key.exchangeName(),
                    RoutingKeyEnum.digital_recharge_effect_bet_progress_routing_key.routingKeyName(),
                    report,
                    customMessagePostProcessor);
        } catch (Exception ex) {
            log.error("充值成功提现流水处理上报时发生异常", ex);
        }
    }

    public void userDigitalAccountCreateReport(UserDigitalAccountCreateReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.digital_user_account_create_progress_routing_key.exchangeName(),
                    RoutingKeyEnum.digital_user_account_create_progress_routing_key.routingKeyName(),
                    report,
                    customMessagePostProcessor);
        } catch (Exception ex) {
            log.error("用户数字货币账户链上创建上报时发生异常", ex);
        }
    }

    public void digitalRechargeOrderTimeoutDelayReport(DigitalRechargeOrderTimeoutDelayReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.digital_recharge_order_timeout_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.digital_recharge_order_timeout_delay_routing_key.routingKeyName(),
                    report,
                    new CustomDelayedMessagePostProcessor(report.getTtl()));
        } catch (Exception ex) {
            log.error("充值记录支付超时延时上报时发生异常", ex);
        }
    }

    public void c2cEggRecordReport(C2CEggRecordReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_egg_record_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_egg_record_delay_routing_key.routingKeyName(),
                    report,
                    new CustomDelayedMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("C2C彩蛋倒计时上报时发生异常", ex);
        }
    }

    public void c2cWithdrawReceiveTimeoutReport(C2CWithdrawReceiveTimeoutReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_order_receive_timeout_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_order_receive_timeout_delay_routing_key.routingKeyName(),
                    report,
                    new ExpirationMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("C2C提现订单收款超时延时上报时发生异常", ex);
        }
    }

    public void c2cWithdrawReceiveAlertReport(C2CWithdrawReceiveAlertReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_order_receive_alert_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_order_receive_alert_delay_routing_key.routingKeyName(),
                    report,
                    new ExpirationMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("C2C提现订单收款提醒延时上报时发生异常", ex);
        }
    }

    public void c2cRechargePaymentTimeoutReport(C2CRechargePaymentTimeoutReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_order_payment_timeout_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_order_payment_timeout_delay_routing_key.routingKeyName(),
                    report,
                    new ExpirationMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("C2C充值订单付款超时延时上报时发生异常", ex);
        }
    }

    public void c2cRechargePaymentAlertReport(C2CRechargePaymentAlertReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_order_payment_alert_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_order_payment_alert_delay_routing_key.routingKeyName(),
                    report,
                    new ExpirationMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("C2C充值订单付款提醒延时上报时发生异常", ex);
        }
    }

    public void c2cOrderUnlockReport(C2COrderUnlockReport report) {
        try {
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.c2c_order_unlock_check_delay_routing_key.exchangeName(),
                    RoutingKeyEnum.c2c_order_unlock_check_delay_routing_key.routingKeyName(),
                    report,
                    new ExpirationMessagePostProcessor(report.getTtl())
            );
        } catch (Exception ex) {
            log.error("撮合订单解锁延时上报时发生异常", ex);
        }
    }

    public void sendBalanceRecord(BalanceRecordReport report) {
        try {
            this.packMessage(ReportEvent.BalanceRecord, report);
            reportRabbitTemplate.convertAndSend(
                    RoutingKeyEnum.global_balance_record_routing_key.exchangeName(),
                    RoutingKeyEnum.global_balance_record_routing_key.routingKeyName(),
                    report.getMsg(),
                    customMessagePostProcessor
            );
        } catch (Exception ex) {
            log.error("资金明细数据上报时发生异常", ex);
        }
    }

    public void sendMessage(ReportEvent event, BaseReport report) {
        try {
            this.packMessage(event, report);
            reportRabbitTemplate.convertAndSend(report.getMsg(), customMessagePostProcessor);
        } catch (Exception ex) {
            log.error("上报事件数据发生异常", ex);
        }
    }

    /**
     * 打包待上传的数据
     *
     * @param event
     * @param report
     * @throws IllegalAccessException
     */
    private void packMessage(ReportEvent event, BaseReport report) throws IllegalAccessException {
        int eventId = event.value();
        report.set("event", eventId);
        report.set("project", project);
        // 时间戳参数不能为空
        if (null == report.get("timestamp")) {
            report.set("timestamp", new Date());
        }
        // index shard
        if (null == report.get("index")) {
            Date ts = (Date) report.get("timestamp");
            report.set("index", DateFormatUtils.format(ts, "yyyy.MM.dd"));
        }
        // 执行更新增量更新时作为主键
        if (null == report.get("uuid")) {
            report.set("uuid", UUID.randomUUID().toString().replace("-", ""));
        }
        // 事件参数
        Class<?> clazz = report.getClass();
        Field[] fields = clazz.getDeclaredFields();
        if (fields.length == 0) {
            return;
        }
        String dataKey = String.valueOf(eventId);
        Map<String, Object> data = new HashMap<>();
        for (Field field : fields) {
            field.setAccessible(true);
            String fieldKey = field.getName();
            Object fieldValue = field.get(report);
            if (null == fieldValue) {
                continue;
            }
            data.put(fieldKey, fieldValue);
        }
        report.set(dataKey, data);
    }
}
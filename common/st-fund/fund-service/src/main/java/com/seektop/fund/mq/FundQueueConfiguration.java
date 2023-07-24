package com.seektop.fund.mq;

import com.google.common.collect.Maps;
import com.seektop.enumerate.mq.ExchangeEnum;
import com.seektop.enumerate.mq.QueueEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Map;

@Slf4j
@Configuration("fundQueueConfiguration")
@DependsOn(value = {"rabbitmqUserConfiguration", "rabbitmqReportConfiguration", "rabbitmqDefaultConfiguration"})
public class FundQueueConfiguration {

    @Resource(name = "reportRabbitAdmin")
    private RabbitAdmin reportRabbitAdmin;

    @PostConstruct
    public void initQueue() {
        FanoutExchange commonFanoutExchange = new FanoutExchange(ExchangeEnum.event_fanout.exchangeName(), true, false);
        reportRabbitAdmin.declareExchange(commonFanoutExchange);

        DirectExchange c2cOrderExchange = new DirectExchange(ExchangeEnum.c2c_order.exchangeName(), true, false);
        reportRabbitAdmin.declareExchange(c2cOrderExchange);

        DirectExchange digitalExchange = new DirectExchange(ExchangeEnum.digital.exchangeName(), true, false);
        reportRabbitAdmin.declareExchange(digitalExchange);

        // 自定义延时队列
        Map<String, Object> delayedCustomExchangeParamMap = Maps.newHashMap();
        delayedCustomExchangeParamMap.put("x-delayed-type", "direct");
        CustomExchange delayedCustomExchange = new CustomExchange(ExchangeEnum.delayed_custom.exchangeName(), "x-delayed-message", true, false, delayedCustomExchangeParamMap);
        reportRabbitAdmin.declareExchange(delayedCustomExchange);

        // 充值失败次数检测自动变更财务层级处理
        Queue rechargeFailureMonitorChangeFundLevelQueue = new Queue(QueueEnum.recharge_failure_monitor_change_fund_level.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(rechargeFailureMonitorChangeFundLevelQueue);
        Binding rechargeFailureMonitorChangeFundLevelQueueBinding = BindingBuilder.bind(rechargeFailureMonitorChangeFundLevelQueue).to(commonFanoutExchange);
        reportRabbitAdmin.declareBinding(rechargeFailureMonitorChangeFundLevelQueueBinding);

        // 首存成功自动变更财务层级处理
        Queue firstRechargeMonitorChangeFundLevelQueue = new Queue(QueueEnum.first_recharge_monitor_change_fund_level.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(firstRechargeMonitorChangeFundLevelQueue);
        Binding firstRechargeMonitorChangeFundLevelQueueBinding = BindingBuilder.bind(firstRechargeMonitorChangeFundLevelQueue).to(commonFanoutExchange);
        reportRabbitAdmin.declareBinding(firstRechargeMonitorChangeFundLevelQueueBinding);

        // C2C充提订单解锁队列
        Queue c2cOrderUnlockCheckDelayQueue = new Queue(QueueEnum.c2c_order_unlock_check_delay.queueName(), true, false, false);
        c2cOrderUnlockCheckDelayQueue.addArgument("x-dead-letter-exchange", QueueEnum.c2c_order_unlock_check_progress.exchangeName());
        c2cOrderUnlockCheckDelayQueue.addArgument("x-dead-letter-routing-key", QueueEnum.c2c_order_unlock_check_progress.routingKeyName());
        reportRabbitAdmin.declareQueue(c2cOrderUnlockCheckDelayQueue);
        Binding c2cOrderUnlockCheckDelayQueueBinding = BindingBuilder.bind(c2cOrderUnlockCheckDelayQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_unlock_check_delay.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cOrderUnlockCheckDelayQueueBinding);
        Queue c2cOrderUnlockCheckProgressQueue = new Queue(QueueEnum.c2c_order_unlock_check_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cOrderUnlockCheckProgressQueue);
        Binding c2cOrderUnlockCheckProgressQueueBinding = BindingBuilder.bind(c2cOrderUnlockCheckProgressQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_unlock_check_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cOrderUnlockCheckProgressQueueBinding);

        // C2C充值订单付款提醒队列
        Queue c2cRechargePaymentAlertDelayQueue = new Queue(QueueEnum.c2c_order_payment_alert_delay.queueName(), true, false, false);
        c2cRechargePaymentAlertDelayQueue.addArgument("x-dead-letter-exchange", QueueEnum.c2c_order_payment_alert_progress.exchangeName());
        c2cRechargePaymentAlertDelayQueue.addArgument("x-dead-letter-routing-key", QueueEnum.c2c_order_payment_alert_progress.routingKeyName());
        reportRabbitAdmin.declareQueue(c2cRechargePaymentAlertDelayQueue);
        Binding c2cRechargePaymentAlertDelayQueueBinding = BindingBuilder.bind(c2cRechargePaymentAlertDelayQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_payment_alert_delay.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cRechargePaymentAlertDelayQueueBinding);
        Queue c2cRechargePaymentAlertProgressQueue = new Queue(QueueEnum.c2c_order_payment_alert_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cRechargePaymentAlertProgressQueue);
        Binding c2cRechargePaymentAlertProgressQueueBinding = BindingBuilder.bind(c2cRechargePaymentAlertProgressQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_payment_alert_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cRechargePaymentAlertProgressQueueBinding);

        // C2C充值订单付款超时队列
        Queue c2cRechargePaymentTimeoutDelayQueue = new Queue(QueueEnum.c2c_order_payment_timeout_delay.queueName(), true, false, false);
        c2cRechargePaymentTimeoutDelayQueue.addArgument("x-dead-letter-exchange", QueueEnum.c2c_order_payment_timeout_progress.exchangeName());
        c2cRechargePaymentTimeoutDelayQueue.addArgument("x-dead-letter-routing-key", QueueEnum.c2c_order_payment_timeout_progress.routingKeyName());
        reportRabbitAdmin.declareQueue(c2cRechargePaymentTimeoutDelayQueue);
        Binding c2cRechargePaymentTimeoutDelayQueueBinding = BindingBuilder.bind(c2cRechargePaymentTimeoutDelayQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_payment_timeout_delay.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cRechargePaymentTimeoutDelayQueueBinding);
        Queue c2cRechargePaymentTimeoutProgressQueue = new Queue(QueueEnum.c2c_order_payment_timeout_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cRechargePaymentTimeoutProgressQueue);
        Binding c2cRechargePaymentTimeoutProgressQueueBinding = BindingBuilder.bind(c2cRechargePaymentTimeoutProgressQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_payment_timeout_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cRechargePaymentTimeoutProgressQueueBinding);

        // C2C提现订单收款提醒队列
        Queue c2cWithdrawReceiveAlertDelayQueue = new Queue(QueueEnum.c2c_order_receive_alert_delay.queueName(), true, false, false);
        c2cWithdrawReceiveAlertDelayQueue.addArgument("x-dead-letter-exchange", QueueEnum.c2c_order_receive_alert_progress.exchangeName());
        c2cWithdrawReceiveAlertDelayQueue.addArgument("x-dead-letter-routing-key", QueueEnum.c2c_order_receive_alert_progress.routingKeyName());
        reportRabbitAdmin.declareQueue(c2cWithdrawReceiveAlertDelayQueue);
        Binding c2cWithdrawReceiveAlertDelayQueueBinding = BindingBuilder.bind(c2cWithdrawReceiveAlertDelayQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_receive_alert_delay.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cWithdrawReceiveAlertDelayQueueBinding);
        Queue c2cWithdrawReceiveAlertProgressQueue = new Queue(QueueEnum.c2c_order_receive_alert_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cWithdrawReceiveAlertProgressQueue);
        Binding c2cWithdrawReceiveAlertProgressQueueBinding = BindingBuilder.bind(c2cWithdrawReceiveAlertProgressQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_receive_alert_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cWithdrawReceiveAlertProgressQueueBinding);

        // C2C提现订单收款超时队列
        Queue c2cWithdrawReceiveTimeoutDelayQueue = new Queue(QueueEnum.c2c_order_receive_timeout_delay.queueName(), true, false, false);
        c2cWithdrawReceiveTimeoutDelayQueue.addArgument("x-dead-letter-exchange", QueueEnum.c2c_order_receive_timeout_progress.exchangeName());
        c2cWithdrawReceiveTimeoutDelayQueue.addArgument("x-dead-letter-routing-key", QueueEnum.c2c_order_receive_timeout_progress.routingKeyName());
        reportRabbitAdmin.declareQueue(c2cWithdrawReceiveTimeoutDelayQueue);
        Binding c2cWithdrawReceiveTimeoutDelayQueueBinding = BindingBuilder.bind(c2cWithdrawReceiveTimeoutDelayQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_receive_timeout_delay.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cWithdrawReceiveTimeoutDelayQueueBinding);
        Queue c2cWithdrawReceiveTimeoutProgressQueue = new Queue(QueueEnum.c2c_order_receive_timeout_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cWithdrawReceiveTimeoutProgressQueue);
        Binding c2cWithdrawReceiveTimeoutProgressQueueBinding = BindingBuilder.bind(c2cWithdrawReceiveTimeoutProgressQueue).to(c2cOrderExchange).with(QueueEnum.c2c_order_receive_timeout_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(c2cWithdrawReceiveTimeoutProgressQueueBinding);

        // 充值成功次数检测自动变更财务层级处理
        Queue rechargeSuccessMonitorChangeFundLevelQueue = new Queue(QueueEnum.recharge_success_monitor_change_fund_level.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(rechargeSuccessMonitorChangeFundLevelQueue);
        Binding rechargeSuccessMonitorChangeFundLevelQueueBinding = BindingBuilder.bind(rechargeSuccessMonitorChangeFundLevelQueue).to(commonFanoutExchange);
        reportRabbitAdmin.declareBinding(rechargeSuccessMonitorChangeFundLevelQueueBinding);

        // C2C彩蛋倒计时队列
        Queue c2cEggRecordDelayQueue = new Queue(QueueEnum.c2c_egg_record_delay.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(c2cEggRecordDelayQueue);
        Binding c2cEggRecordDelayQueueBinding = BindingBuilder.bind(c2cEggRecordDelayQueue).to(delayedCustomExchange).with(QueueEnum.c2c_egg_record_delay.routingKeyName()).noargs();
        reportRabbitAdmin.declareBinding(c2cEggRecordDelayQueueBinding);

        // 数字货币充值成功流水处理Queue
        Queue digitalRechargeEffectBetProgressQueue = new Queue(QueueEnum.digital_recharge_effect_bet_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(digitalRechargeEffectBetProgressQueue);
        Binding digitalRechargeEffectBetProgressQueueBinding = BindingBuilder.bind(digitalRechargeEffectBetProgressQueue).to(digitalExchange).with(QueueEnum.digital_recharge_effect_bet_progress.routingKeyName());
        reportRabbitAdmin.declareBinding(digitalRechargeEffectBetProgressQueueBinding);

        // 充值付款人姓名监控处理
        Queue rechargePayerMonitorProgressQueue = new Queue(QueueEnum.recharge_payer_monitor_progress.queueName(), true, false, false);
        reportRabbitAdmin.declareQueue(rechargePayerMonitorProgressQueue);
        Binding rechargePayerMonitorProgressQueueBinding = BindingBuilder.bind(rechargePayerMonitorProgressQueue).to(commonFanoutExchange);
        reportRabbitAdmin.declareBinding(rechargePayerMonitorProgressQueueBinding);
    }

}
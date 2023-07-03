package com.seektop.common.rabbitmq.configuration;

import com.seektop.enumerate.mq.ExchangeEnum;
import com.seektop.enumerate.mq.QueueEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

@Slf4j
@Configuration("rabbitmqQueueConfiguration")
@DependsOn(value = {"rabbitmqUserConfiguration", "rabbitmqReportConfiguration", "rabbitmqDefaultConfiguration"})
public class QueueConfiguration {

    // 通知相关
    @Value("${global.notice.exchange}")
    private String noticeExchange;
    @Value("${global.notice.queue}")
    private String noticeQueue;
    @Value("${global.notice.routekey}")
    private String noticeRouteKey;

    // 财务相关
    @Value("${global.fund.exchange}")
    private String fundExchange;
    @Value("${global.fund.queue}")
    private String fundQueue;
    @Value("${global.fund.routekey}")
    private String fundRouteKey;

    // 直播相关
    @Value("${global.live.exchange}")
    private String liveExchange;
    @Value("${global.live.fundrecord.delay.routekey}")
    private String liveFundRecordDelayRouteKey;
    @Value("${global.live.fundrecord.progress.routekey}")
    private String liveFundRecordProgressRouteKey;
    @Value("${global.live.fundrecord.delay.queue}")
    private String liveFundRecordDelayQueue;
    @Value("${global.live.fundrecord.progress.queue}")
    private String liveFundRecordProgressQueue;
    @Value("${global.live.plazainformation.grab.routekey}")
    private String livePlazaInformationGrabRouteKey;
    @Value("${global.live.plazainformation.grab.queue}")
    private String livePlazaInformationGrabQueue;

    // 活動相關
    @Value("${st.activity.exchange}")
    private String activityExchange;
    @Value("${st.activity.routekey}")
    private String activityRouteKey;
    @Value("${st.activity.queue}")
    private String activityQueue;

    // 黑名单相关
    @Value("${global.blacklist.exchange}")
    private String blackListExchange;
    @Value("${global.blacklist.event.routeKey}")
    private String blackListRouteKey;
    @Value("${global.blacklist.event.queue}")
    private String blackListQueue;
    @Value("${global.blacklist.setting.delay.routekey}")
    private String blacklistSettingDelayRouteKey;
    @Value("${global.blacklist.setting.progress.routekey}")
    private String blacklistSettingProgressRouteKey;
    @Value("${global.blacklist.setting.delay.queue}")
    private String blacklistSettingDelayQueue;
    @Value("${global.blacklist.setting.progress.queue}")
    private String blacklistSettingProgressQueue;

    // 活动监听公共事件队列
    @Value("${global.report.event-activity-queue}")
    private String eventActivityQueue;

    // 公共事件，登录，注册，充值，加币事件的广播交换机，目前用于活动
    @Value("${global.event.fanout.exchange}")
    private String eventFanoutExchange;

    // 系统操作路由哦配置
    @Value("${global.system.exchange}")
    private String systemExchange;
    @Value("${global.system.operation.log.routekey}")
    private String systemOperationLogRouteKey;
    @Value("${global.system.operation.log.queue}")
    private String systemOperationLogQueue;

    @Resource(name = "reportRabbitAdmin")
    private RabbitAdmin reportRabbitAdmin;
    @Resource(name = "userRabbitAdmin")
    private RabbitAdmin userRabbitAdmin;
    @Resource(name = "defaultRabbitAdmin")
    private RabbitAdmin defaultRabbitAdmin;

    @PostConstruct
    public void initQueue() {
        log.debug("准备初始化Rabbitmq中的对象");

        // 通知相关Exchange、Queue、Bind
        DirectExchange noticeExchange = new DirectExchange(this.noticeExchange, true, false);
        defaultRabbitAdmin.declareExchange(noticeExchange);
        Queue noticeQueue = new Queue(this.noticeQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(noticeQueue);
        Binding noticeBinding = BindingBuilder.bind(noticeQueue).to(noticeExchange).with(this.noticeRouteKey);
        defaultRabbitAdmin.declareBinding(noticeBinding);
        log.debug("通知的Queue,Exchange,Binding初始化完成");

        // 财务相关Exchange、Queue、Bind
        DirectExchange fundExchange = new DirectExchange(this.fundExchange, true, false);
        defaultRabbitAdmin.declareExchange(fundExchange);
        Queue fundQueue = new Queue(this.fundQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(fundQueue);
        Binding fundBinding = BindingBuilder.bind(fundQueue).to(fundExchange).with(this.fundRouteKey);
        defaultRabbitAdmin.declareBinding(fundBinding);
        log.debug("财务的Queue,Exchange,Binding初始化完成");

        DirectExchange liveExchange = new DirectExchange(this.liveExchange, true, false);
        defaultRabbitAdmin.declareExchange(liveExchange);
        // 延迟Queue
        Queue liveFundRecordDelayQueue = new Queue(this.liveFundRecordDelayQueue, true, false, false);
        liveFundRecordDelayQueue.addArgument("x-dead-letter-exchange", this.liveExchange);
        liveFundRecordDelayQueue.addArgument("x-dead-letter-routing-key", this.liveFundRecordProgressRouteKey);
        liveFundRecordDelayQueue.addArgument("x-message-ttl", 60000);
        defaultRabbitAdmin.declareQueue(liveFundRecordDelayQueue);
        Binding liveFundRecordDelayQueueBinding = BindingBuilder.bind(liveFundRecordDelayQueue).to(liveExchange).with(this.liveFundRecordDelayRouteKey);
        defaultRabbitAdmin.declareBinding(liveFundRecordDelayQueueBinding);
        // 处理Queue
        Queue liveFundRecordProgressQueue = new Queue(this.liveFundRecordProgressQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(liveFundRecordProgressQueue);
        Binding liveFundRecordProgressQueueBinding = BindingBuilder.bind(liveFundRecordProgressQueue).to(liveExchange).with(this.liveFundRecordProgressRouteKey);
        defaultRabbitAdmin.declareBinding(liveFundRecordProgressQueueBinding);

        Queue livePlazaInformationGrabQueue = new Queue(this.livePlazaInformationGrabQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(livePlazaInformationGrabQueue);
        Binding livePlazaInformationGrabBinding = BindingBuilder.bind(livePlazaInformationGrabQueue).to(liveExchange).with(this.livePlazaInformationGrabRouteKey);
        defaultRabbitAdmin.declareBinding(livePlazaInformationGrabBinding);

        log.debug("直播的Queue,Exchange,Binding初始化完成");

        // 活動相关Exchange、Queue、Bind
        DirectExchange activityExchange = new DirectExchange(this.activityExchange, true, false);
        defaultRabbitAdmin.declareExchange(activityExchange);
        Queue activityQueue = new Queue(this.activityQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(activityQueue);
        Binding activityBinding = BindingBuilder.bind(activityQueue).to(activityExchange).with(this.activityRouteKey);
        defaultRabbitAdmin.declareBinding(activityBinding);
        log.debug("活動的Queue,Exchange,Binding初始化完成");

        // vhost"report"下，添加活动模块用的队列
        FanoutExchange commonFanoutExchange = new FanoutExchange(this.eventFanoutExchange, true, false);
        reportRabbitAdmin.declareExchange(commonFanoutExchange);
        Queue event4ActivityQueue = new Queue(this.eventActivityQueue, true, false, false);
        reportRabbitAdmin.declareQueue(event4ActivityQueue);
        Binding event4ActivityQueueBinding = BindingBuilder.bind(event4ActivityQueue).to(commonFanoutExchange);
        reportRabbitAdmin.declareBinding(event4ActivityQueueBinding);

        // vhost"user"下，添加活动模块用的队列
        DirectExchange globalUserExchange = new DirectExchange(ExchangeEnum.globalUser.exchangeName(), true, false);
        userRabbitAdmin.declareExchange(globalUserExchange);
        Queue globalUserDataActivityQueue = new Queue(QueueEnum.global_user_data_activity.queueName(), true, false, false);
        userRabbitAdmin.declareQueue(globalUserDataActivityQueue);
        Binding globalUserDataSaveToElasticsearchQueueBinding = BindingBuilder.bind(globalUserDataActivityQueue).to(globalUserExchange).with(QueueEnum.global_user_data_activity.routingKeyName());
        userRabbitAdmin.declareBinding(globalUserDataSaveToElasticsearchQueueBinding);

        DirectExchange blackListExchange = new DirectExchange(this.blackListExchange, true, false);
        defaultRabbitAdmin.declareExchange(blackListExchange);
        // 延迟Queue
        Queue blacklistSettingDelayQueue = new Queue(this.blacklistSettingDelayQueue, true, false, false);
        blacklistSettingDelayQueue.addArgument("x-dead-letter-exchange", this.blackListExchange);
        blacklistSettingDelayQueue.addArgument("x-dead-letter-routing-key", this.blacklistSettingProgressRouteKey);
        defaultRabbitAdmin.declareQueue(blacklistSettingDelayQueue);
        Binding blacklistSettingDelayQueueBinding = BindingBuilder.bind(blacklistSettingDelayQueue).to(blackListExchange).with(this.blacklistSettingDelayRouteKey);
        defaultRabbitAdmin.declareBinding(blacklistSettingDelayQueueBinding);
        // 处理Queue
        Queue blacklistSettingProgressQueue = new Queue(this.blacklistSettingProgressQueue, true, false, false);
        defaultRabbitAdmin.declareQueue(blacklistSettingProgressQueue);
        Binding blacklistSettingProgressQueueBinding = BindingBuilder.bind(blacklistSettingProgressQueue).to(blackListExchange).with(this.blacklistSettingProgressRouteKey);
        defaultRabbitAdmin.declareBinding(blacklistSettingProgressQueueBinding);
        log.debug("黑名单的Queue,Exchange,Binding初始化完成");
        //Flink 统计后需要计入黑名单的数据
        Queue blackListQueue = new Queue(this.blackListQueue,true,false,false);
        defaultRabbitAdmin.declareQueue(blackListQueue);
        Binding blackListQueueBinding = BindingBuilder.bind(blackListQueue).to(blackListExchange).with(this.blackListRouteKey);
        defaultRabbitAdmin.declareBinding(blackListQueueBinding);
        log.debug("黑名单事件的的Queue,Exchange,Binding初始化完成");

        /******* 系统操作日志 *****/
        DirectExchange systemExchange = new DirectExchange(this.systemExchange, true, false);
        defaultRabbitAdmin.declareExchange(systemExchange);

        Queue systemOperationQuery = new Queue(this.systemOperationLogQueue,true,false,false);
        defaultRabbitAdmin.declareQueue(systemOperationQuery);

        Binding systemOperationBinding = BindingBuilder.bind(systemOperationQuery).to(systemExchange).with(this.systemOperationLogRouteKey);
        defaultRabbitAdmin.declareBinding(systemOperationBinding);

        log.debug("系统操作日志 Queue,Exchange,Binding初始化完成");
        /******* 系统操作日志 *****/


    }

}
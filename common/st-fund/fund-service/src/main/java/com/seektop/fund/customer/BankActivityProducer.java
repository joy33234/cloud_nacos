package com.seektop.fund.customer;

import com.seektop.report.fund.BankRechargeReport;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.atomic.AtomicInteger;
/**
 *
 * forhead
 # Activity MQ
 activity.pool-size=1
 activity.max-retry=30
 activity.queue=global-activity-queue
 activity.exchange=global-activity-exchange
 activity.routekey=global-activity-routing
 activity.dead-queue=global-activity-dead-queue
 activity.dead-exchange=global-activity-dead-exchange
 activity.dead-routekey=global-activity-dead-routing
 activity.rabbitmq.addresses=mq.global.com:5672
 activity.rabbitmq.username=global
 activity.rabbitmq.password=global2018**#!
 activity.rabbitmq.virtual-host=activity
 activity.rabbitmq.connection-timeout=600000
 activity.rabbitmq.publisher-confirms=true
 activity.rabbitmq.publisher-returns=true
 activity.rabbitmq.listener.simple.acknowledge-mode=manual
 activity.rabbitmq.listener.simple.concurrency=1
 activity.rabbitmq.listener.simple.max-concurrency=1
 activity.rabbitmq.listener.simple.retry.enabled=true
 */

/**
 * 活动上报MQ工具类
 */
@Slf4j
@Component("ActivityProducer")
public class BankActivityProducer implements MessagePostProcessor {

    @Value("${activity.pool-size:2}")
    private int poolSize;

    @Value("${activity.max-retry:10}")
    private int maxRetry;

    @Value("${activity.exchange}")
    private String exchange;

    @Value("${activity.routekey}")
    private String routeKey;

    private String contentType = "application/json";

    // rabbitmq
    @Resource(name="ActivityReportRabbitTemplate")
    private RabbitTemplate rabbitTemplate;

    // sender pool
    private ExecutorService senderPool;

    // events queue
    private BlockingQueue<Event> events;

    // delivery mode
    private MessageDeliveryMode deliveryMode = MessageDeliveryMode.PERSISTENT;

    // retry timer
    private final Timer retryTimer = new Timer("global-user-retry", true);

    @PostConstruct
    private void init() {
        log.info(" init activity Producer...");
        // init event query
        this.events = createEventQueue();
        this.senderPool = Executors.newCachedThreadPool();
        for (int i = 0; i < this.poolSize; i++) {
            this.senderPool.submit(new EventSender());
        }
        this.registerShutdown();
        log.info("!!!new producer!!!");
        log.info("producer pool size:{}", this.poolSize);
        log.info("producer max retry:{}", this.maxRetry);
    }

    /**
     * shutdown
     */
    protected void registerShutdown() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("user queue shutdown...");
            BankActivityProducer.this.senderPool.shutdown();
        }));
    }

    /**
     * finalize
     */
    protected void finalize() {
        if (null != this.senderPool) {
            this.senderPool.shutdown();
            this.senderPool = null;
        }
        log.info("finalize");
        this.retryTimer.cancel();
    }

    /**
     * create event queue
     */
    private BlockingQueue<Event> createEventQueue() {
        return new LinkedBlockingDeque<>();
    }

    /**
     * report
     *
     * @param data BankRechargeReport
     */
    public void report(BankRechargeReport data) {
        log.info("get activity data {}", data);
        try {
            this.rabbitTemplate.convertAndSend(this.exchange, this.routeKey, data);
        } catch (AmqpException e) {
            this.events.add(new Event(data));
            log.error(e.getMessage(), e);
        } catch (Exception e) {
            log.warn("user sync failed,retry:{}", e);
            log.error(e.getMessage(), e);
        }
    }

    @Override
    public Message postProcessMessage(Message message) throws AmqpException {
        message.getMessageProperties().setTimestamp(new Date());
        message.getMessageProperties().setContentType(BankActivityProducer.this.contentType);
        message.getMessageProperties().setMessageId(UUID.randomUUID().toString());
        message.getMessageProperties().setDeliveryMode(BankActivityProducer.this.deliveryMode);
        return message;
    }

    /**
     * event sender async
     */
    private class EventSender implements Runnable {

        @Override
        public void run() {
            try {
                while (true) {
                    final Event event = BankActivityProducer.this.events.take();
                    Object message = event.getEvent();
                    try {
                        // try send message
                        log.info("重试用户同步:msg:{}", message);
                        String exchange = BankActivityProducer.this.exchange;
                        String routingKey = BankActivityProducer.this.routeKey;
                        BankActivityProducer.this.rabbitTemplate.convertAndSend(exchange, routingKey, message, BankActivityProducer.this);
                    } catch (AmqpException e) {
                        int retries = event.incrementRetries();
                        if (retries < BankActivityProducer.this.maxRetry) {
                            log.warn("retry:{}", retries);
                            // add to schedule queue
                            BankActivityProducer.this.retryTimer.schedule(new TimerTask() {
                                @Override
                                public void run() {
                                    BankActivityProducer.this.events.add(event);
                                }
                            }, (long) (Math.pow(retries, Math.log(retries)) * 1000));
                        } else {
                            log.error("send message failed:{},msg:{},retry:{}", e, message.toString(), BankActivityProducer.this.maxRetry);
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * event declare
     */
    private static class Event {

        private final Object event;

        private final AtomicInteger retries = new AtomicInteger(0);

        private Event(Object event) {
            this.event = event;
        }

        private Object getEvent() {
            return this.event;
        }

        private int incrementRetries() {
            return this.retries.incrementAndGet();
        }
    }
}
package com.seektop.fund.handler.event;

import org.springframework.context.ApplicationEvent;

/**
 * 代客充值回调通知事件
 */
public class OrderNotifyEvent extends ApplicationEvent {

    public OrderNotifyEvent(String orderId) {
        super(orderId);
    }
}

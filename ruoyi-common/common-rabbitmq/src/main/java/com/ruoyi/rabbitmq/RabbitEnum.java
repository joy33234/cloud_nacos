package com.ruoyi.rabbitmq;

import lombok.Getter;

/**
 * 队列，交换机。路由 常量枚举
 * @author FJW
 * @version 1.0
 * @date 2023年04月18日 16:39
 */
public enum  RabbitEnum {

    QUEUE("jl.{}.queue", "队列名称"),

    EXCHANGE("jl.{}.exchange", "交换机名称"),

    ROUTER_KEY("jl.{}.key", "路由名称"),
    ;

    RabbitEnum(String value, String desc) {
        this.value = value;
        this.desc = desc;
    }

    @Getter
    private String value;

    @Getter
    private String desc;

}

package com.ruoyi.rabbitmq;


import com.google.common.collect.Maps;
import java.util.Map;

/**
 * 交换机类型枚举
 * @version 1.0
 * @date 2023年04月11日 15:19
 */
public enum  RabbitExchangeTypeEnum {



    /**
     * 直连交换机
     * <p>
     * 根据routing-key精准匹配队列(最常使用)
     */
    DIRECT(0, "direct"),

    /**
     * 主题交换机
     * <p>
     * 根据routing-key模糊匹配队列，*匹配任意一个字符，#匹配0个或多个字符
     */
    TOPIC(1,"topic"),
    /**
     * 扇形交换机
     * <p>
     * 直接分发给所有绑定的队列，忽略routing-key,用于广播消息
     */
    FANOUT(2,"fanout"),
    /**
     * 头交换机
     * <p>
     * 类似直连交换机，不同于直连交换机的路由规则建立在头属性上而不是routing-key(使用较少)
     */
    HEADERS(3,"headers");

    private static Map<Integer, RabbitExchangeTypeEnum> typeEnumMap;

    static {
        typeEnumMap = Maps.newHashMap();
        for (RabbitExchangeTypeEnum statusEnum : values())
            typeEnumMap.put(statusEnum.getType(), statusEnum);
    }

    RabbitExchangeTypeEnum(int type, String desc) {
        this.type = type;
        this.desc = desc;
    }

    private int type;

    private String desc;

    public int getType() {
        return this.type;
    }

    public String getDesc() {
        return this.desc;
    }

    public static RabbitExchangeTypeEnum getByType(Integer value) {
        return typeEnumMap.get(value);
    }
}


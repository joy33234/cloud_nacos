package com.ruoyi.rabbitmq;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.date.StopWatch;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.extra.spring.SpringUtil;
import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.text.Convert;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * RabbitMQ 全局配置，SpringBoot 启动后会回调此类
 * @version 1.0
 * @date 2023年04月11日 13:55
 */
@Slf4j
@Component
public class RabbitMqConfig implements SmartInitializingSingleton {

    /**
     * MQ链接工厂
     */
    private ConnectionFactory connectionFactory;

    /**
     * MQ操作管理器
     */
    private AmqpAdmin amqpAdmin;

    /**
     * YML配置
     */
    @Resource
    private RabbitModuleProperties rabbitModuleProperties;

    @Autowired
    public RabbitMqConfig(AmqpAdmin amqpAdmin, RabbitModuleProperties rabbitModuleProperties, ConnectionFactory connectionFactory) {
        this.amqpAdmin = amqpAdmin;
        this.rabbitModuleProperties = rabbitModuleProperties;
        this.connectionFactory = connectionFactory;
    }

    @Override
    public void afterSingletonsInstantiated() {
        StopWatch stopWatch = StopWatch.create("MQ");
        stopWatch.start();
        log.info("初始化MQ配置");
        List<ModuleProperties> modules = rabbitModuleProperties.getModules();
        if (CollUtil.isEmpty(modules)) {
            log.warn("未配置MQ");
            return;
        }
        for (ModuleProperties module : modules) {
            try {
                log.info("module:{}", JSON.toJSONString(module));
                Queue queue = genQueue(module);
                Exchange exchange = genQueueExchange(module);
                queueBindExchange(queue, exchange, module);
                bindProducer(module);
                bindConsumer(queue, exchange, module);
            } catch (Exception e) {
                log.error("初始化失败", e);
            }
        }
        stopWatch.stop();
        log.info("初始化MQ配置成功耗时: {}ms", stopWatch.getTotal(TimeUnit.MILLISECONDS));
    }

    /**
     * 绑定生产者
     * @param module
     */
    private void bindProducer(ModuleProperties module) {
        try {
            log.info("module:produce:{}, exchange:{}, routeKey:{}",module.getProducer(),module.getExchange().getName(),module.getRoutingKey());
            AbsProducerService producerService = SpringUtil.getBean(module.getProducer());
            producerService.setExchange(module.getExchange().getName());
            producerService.setRoutingKey(module.getRoutingKey());
            log.debug("绑定生产者: {}", module.getProducer());
        } catch (Exception e) {
            log.error("bindProducer:",e);
            log.warn("无法在容器中找到该生产者[{}]，若需要此生产者则需要做具体实现", module.getProducer());
        }
    }

    /**
     * 绑定消费者
     * @param queue
     * @param exchange
     * @param module
     */
    private void bindConsumer(Queue queue, Exchange exchange, ModuleProperties module) {
        CustomRetryListener customRetryListener = null;
        try {
            customRetryListener = SpringUtil.getBean(module.getRetry().toString());
        }catch (Exception e) {
            log.debug("无法在容器中找到该重试类[{}]，若需要重试则需要做具体实现", module.getRetry());
        }
        try {
            ConsumerContainerFactory factory = ConsumerContainerFactory.builder()
                    .connectionFactory(connectionFactory)
                    .queue(queue)
                    .exchange(exchange)
                    .consumer(SpringUtil.getBean(module.getConsumer()))
                    .retryListener(customRetryListener)
                    .autoAck(module.getAutoAck())
                    .amqpAdmin(amqpAdmin)
                    .build();
            SimpleMessageListenerContainer container = factory.getObject();
            if (Objects.nonNull(container)) {
                container.start();
            }
            log.debug("绑定消费者: {}", module.getConsumer());
        } catch (Exception e) {
            log.warn("无法在容器中找到该消费者[{}]，若需要此消费者则需要做具体实现", module.getConsumer());
        }
    }

    /**
     * 队列绑定交换机
     * @param queue
     * @param exchange
     * @param module
     */
    private void queueBindExchange(Queue queue, Exchange exchange, ModuleProperties module) {
        log.debug("初始化交换机: {}", module.getExchange().getName());
        String queueName = module.getQueue().getName();
        String exchangeName = module.getExchange().getName();
        module.setRoutingKey(StrUtil.format(RabbitEnum.ROUTER_KEY.getValue(), module.getRoutingKey()));
        String routingKey = module.getRoutingKey();
        Binding binding = new Binding(queueName,
                Binding.DestinationType.QUEUE,
                exchangeName,
                routingKey,
                null);
        amqpAdmin.declareQueue(queue);
        amqpAdmin.declareExchange(exchange);
        amqpAdmin.declareBinding(binding);
        log.debug("队列绑定交换机: 队列: {}, 交换机: {}", queueName, exchangeName);
    }

    /**
     * 创建交换机
     * @param module
     * @return
     */
    private Exchange genQueueExchange(ModuleProperties module) {
        ModuleProperties.Exchange exchange = module.getExchange();
        RabbitExchangeTypeEnum exchangeType = exchange.getType();
        exchange.setName(StrUtil.format(RabbitEnum.EXCHANGE.getValue(), exchange.getName()));
        String exchangeName = exchange.getName();
        Boolean isDurable = exchange.isDurable();
        Boolean isAutoDelete = exchange.isAutoDelete();
        Map<String, Object> arguments = exchange.getArguments();
        return getExchangeByType(exchangeType, exchangeName, isDurable, isAutoDelete, arguments);
    }

    /**
     * 根据类型生成交换机
     * @param exchangeType
     * @param exchangeName
     * @param isDurable
     * @param isAutoDelete
     * @param arguments
     * @return
     */
    private Exchange getExchangeByType(RabbitExchangeTypeEnum exchangeType, String exchangeName, Boolean isDurable, Boolean isAutoDelete, Map<String, Object> arguments) {
        AbstractExchange exchange = null;
        switch (exchangeType) {
            // 直连交换机
            case DIRECT:
                exchange = new DirectExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            // 主题交换机
            case TOPIC:
                exchange = new TopicExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            //扇形交换机
            case FANOUT:
                exchange = new FanoutExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            // 头交换机
            case HEADERS:
                exchange = new HeadersExchange(exchangeName, isDurable, isAutoDelete, arguments);
                break;
            default:
                log.warn("未匹配到交换机类型");
                break;
        }
        return exchange;
    }

    /**
     * 创建队列
     * @param module
     * @return
     */
    private Queue genQueue(ModuleProperties module) {
        ModuleProperties.Queue queue = module.getQueue();
        queue.setName(StrUtil.format(RabbitEnum.QUEUE.getValue(), queue.getName()));
        log.debug("初始化队列: {}", queue.getName());
        Map<String, Object> arguments = queue.getArguments();
        if (MapUtil.isEmpty(arguments)) {
            arguments = new HashMap<>();
        }

        // 转换ttl的类型为long
        if (arguments.containsKey("x-message-ttl")) {
            arguments.put("x-message-ttl", Convert.toLong(arguments.get("x-message-ttl")));
        }

        // 绑定死信队列
        String deadLetterExchange = queue.getDeadLetterExchange();
        String deadLetterRoutingKey = queue.getDeadLetterRoutingKey();
        if (StrUtil.isNotBlank(deadLetterExchange) && StrUtil.isNotBlank(deadLetterRoutingKey)) {
            deadLetterExchange = StrUtil.format(RabbitEnum.EXCHANGE.getValue(), deadLetterExchange);
            deadLetterRoutingKey = StrUtil.format(RabbitEnum.ROUTER_KEY.getValue(), deadLetterRoutingKey);
            arguments.put("x-dead-letter-exchange", deadLetterExchange);
            arguments.put("x-dead-letter-routing-key", deadLetterRoutingKey);
            log.debug("绑定死信队列: 交换机: {}, 路由: {}", deadLetterExchange, deadLetterRoutingKey);
        }

        return new Queue(queue.getName(), queue.isDurable(), queue.isExclusive(), queue.isAutoDelete(), arguments);
    }
}

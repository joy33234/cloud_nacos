package com.seektop.common.redis.configuration;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurerSupport;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.cache.RedisCacheWriter;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * 缓存管理配置
 */
@Slf4j
@EnableCaching
@Configuration
public class CacheConfigurer extends CachingConfigurerSupport {

    /**
     * 数据缓存时间管理
     * @return
     */
    @Bean
    @ConditionalOnMissingBean(name = "expires")
    public Map<String, Long> expires(){
        Map<String, Long> expires = new HashMap<>();
        return expires;
    }

    @Bean
    public CacheManager cacheManager(RedisConnectionFactory factory, @Qualifier("expires") Map<String, Long> expires) {
        //缓存配置对象
        RedisCacheConfiguration config = getRedisCacheConfigurationWithTtl(120);
        //缓存配置
        Map<String, RedisCacheConfiguration> cacheConfigurations = new HashMap<>();
        //自定义缓存名，后面使用的@Cacheable的CacheName
        log.debug("缓存时间：{}", expires);
        expires.forEach((key, value) -> cacheConfigurations.put(key, getRedisCacheConfigurationWithTtl(value)));
        //根据redis缓存配置和reid连接工厂生成redis缓存管理器
        RedisCacheManager redisCacheManager = RedisCacheManager
                .builder(RedisCacheWriter.nonLockingRedisCacheWriter(factory))
                .withInitialCacheConfigurations(cacheConfigurations)
                .cacheDefaults(config)
                .transactionAware()
                .build();
        log.debug("自定义RedisCacheManager加载完成");
        return redisCacheManager;
    }

    /**
     * 自定义key
     * @return
     */
    @Override
    public KeyGenerator keyGenerator() {
        return (o, method, objects) -> {
            StringBuilder sb = new StringBuilder();
            sb.append(o.getClass().getSimpleName()).append(".");
            sb.append(method.getName()).append("(");
            String join = StringUtils.join(objects, ",");
            sb.append(null != join ? join : "");
            sb.append(")");
            return sb.toString();
        };
    }

    /**
     * 根据时间配置
     * @param seconds
     * @return
     */
    private RedisCacheConfiguration getRedisCacheConfigurationWithTtl(long seconds) {
        ObjectMapper om = new ObjectMapper();
        om.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        GenericJackson2JsonRedisSerializer.registerNullValueSerializer(om, null);
        om.enableDefaultTyping(ObjectMapper.DefaultTyping.NON_FINAL, JsonTypeInfo.As.PROPERTY);
        GenericJackson2JsonRedisSerializer genericJackson2JsonRedisSerializer = new GenericJackson2JsonRedisSerializer(om);
        RedisCacheConfiguration config = RedisCacheConfiguration.defaultCacheConfig()
                .disableCachingNullValues() //不缓存null值
                .computePrefixWith(name -> name + ":")
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(genericJackson2JsonRedisSerializer))
                .entryTtl(Duration.ofSeconds(seconds));
        return config;
    }
}

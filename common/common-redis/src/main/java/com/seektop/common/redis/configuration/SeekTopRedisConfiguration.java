package com.seektop.common.redis.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.CommonRedisTemplate;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class SeekTopRedisConfiguration {

    @Bean
    public CommonRedisTemplate redisTemplate(RedisConnectionFactory factory) {
        CommonRedisTemplate seekTopRedisTemplate = new CommonRedisTemplate();
        seekTopRedisTemplate.setConnectionFactory(factory);

        seekTopRedisTemplate.setEnableDefaultSerializer(true);

        StringRedisSerializer stringRedisSerializer = new StringRedisSerializer();
        Jackson2JsonRedisSerializer<Object> serializer = new Jackson2JsonRedisSerializer<>(Object.class);

        seekTopRedisTemplate.setKeySerializer(stringRedisSerializer);
        seekTopRedisTemplate.setHashKeySerializer(stringRedisSerializer);

        seekTopRedisTemplate.setHashValueSerializer(serializer);
        seekTopRedisTemplate.setValueSerializer(serializer);
        seekTopRedisTemplate.afterPropertiesSet();
        return seekTopRedisTemplate;
    }

}
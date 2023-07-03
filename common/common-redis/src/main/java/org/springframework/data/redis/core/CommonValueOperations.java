package org.springframework.data.redis.core;

import com.seektop.common.redis.operations.SeekTopRedisOperations;

import java.util.List;

public class CommonValueOperations<K, Object> extends DefaultValueOperations<K, Object> implements SeekTopRedisOperations {

    CommonValueOperations(RedisTemplate template) {
        super(template);
    }

    public <V> V get(K key, Class<V> clazz) {
        return om.convertValue(super.get(key), clazz);
    }

    public <V> List<V> getList(K key, Class<V> clazz) {
        return convertList(super.get(key), clazz);
    }

}
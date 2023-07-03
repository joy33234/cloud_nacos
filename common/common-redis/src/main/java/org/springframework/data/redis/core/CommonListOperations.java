package org.springframework.data.redis.core;

import com.seektop.common.redis.operations.SeekTopRedisOperations;

import java.util.List;

public class CommonListOperations<K, Object> extends DefaultListOperations<K, Object> implements SeekTopRedisOperations {

    CommonListOperations(RedisTemplate template) {
        super(template);
    }

    public <V> V leftPop(K key, Class<V> clazz) {
        return om.convertValue(super.leftPop(key), clazz);
    }
    public <V> V rightPop(K key, Class<V> clazz) {
        return om.convertValue(super.rightPop(key), clazz);
    }

    public <V> List<V> range(K key,Long start,Long end ,Class<V> clazz) {
        return convertList(super.range(key,start,end), clazz);
    }

}
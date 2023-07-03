package org.springframework.data.redis.core;

import com.fasterxml.jackson.databind.JavaType;
import com.seektop.common.redis.operations.SeekTopRedisOperations;

import java.util.Set;

public class CommonSetOperations<K,Object> extends DefaultSetOperations<K,Object> implements SeekTopRedisOperations {

    CommonSetOperations(RedisTemplate template) {
        super(template);
    }

    public <V> Set<V> get(K key, Class<V> clazz){
        return  om.convertValue(super.members(key),getCollectionType(Set.class,clazz));
    }

    public <V> Set<V> get(K key, JavaType clazz){
        return  om.convertValue(super.members(key),getCollectionType(Set.class,clazz));
    }

}
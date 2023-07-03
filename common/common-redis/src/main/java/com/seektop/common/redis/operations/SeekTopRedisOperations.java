package com.seektop.common.redis.operations;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.ObjectUtils;

import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface SeekTopRedisOperations<K, V> {

    ObjectMapper om = new ObjectMapper() {
        {
            this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        }

        @Override
        public <T> T convertValue(Object fromValue, Class<T> toValueType) throws IllegalArgumentException {
            if(ObjectUtils.isEmpty(fromValue)){
                return null;
            }
            return super.convertValue(fromValue, toValueType);
        }

        @Override
        public <T> T convertValue(Object fromValue, JavaType toValueType) throws IllegalArgumentException {
            if(ObjectUtils.isEmpty(fromValue)){
                return null;
            }
            return super.convertValue(fromValue, toValueType);
        }
    };

    /**
     * 转换对象
     *
     * @param from
     * @param clazz
     * @param <T>
     * @return
     */
    default <T> T convertValue(Object from, Class<T> clazz) {
        return om.convertValue(from, clazz);
    }

    /**
     * 转换集合
     *
     * @param from
     * @param clazz
     * @param <T>
     * @return
     */
    default <T> List<T> convertList(Object from, Class<T> clazz) {
        return om.convertValue(from, getCollectionType(Collection.class, clazz));
    }

    /**
     * 获取类型
     *
     * @param collectionClazz
     * @param elementClazz
     * @param <T>
     * @return
     */
    default <T> JavaType getCollectionType(Class<? extends Collection> collectionClazz, Class<T> elementClazz) {
        return om.getTypeFactory().constructCollectionType(collectionClazz, elementClazz);
    }

    default <T> JavaType getCollectionType(Class<? extends Collection> collectionClazz, JavaType elementClazz) {
        return om.getTypeFactory().constructCollectionType(collectionClazz, elementClazz);
    }


    default <T> JavaType getMapType(Class<? extends Map> mapClazz, Class<T> elementClazz) {
        return om.getTypeFactory().constructMapType(mapClazz, String.class,elementClazz);
    }



}
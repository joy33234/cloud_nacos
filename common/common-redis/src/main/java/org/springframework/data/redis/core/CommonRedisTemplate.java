package org.springframework.data.redis.core;

public class CommonRedisTemplate<K,V> extends RedisTemplate<K,V> {

    private final CommonValueOperations<K, V> valueOps = new CommonValueOperations<>(this);
    private final CommonSetOperations<K, V> setOps = new CommonSetOperations<>(this);
    private final CommonListOperations<K, V> listOps = new CommonListOperations<>(this);


    public CommonValueOperations<K, V> getValueOps() {
        return valueOps;
    }

    public CommonSetOperations<K, V> getSetOps() {
        return setOps;
    }

    public CommonListOperations<K,V> getListOps() {
        return listOps;
    }

    public <HK,HV> CommonHashOperations<K, HK, HV> getHashOperations() {
        return  new CommonHashOperations<K,HK,HV>(this);
    }

}
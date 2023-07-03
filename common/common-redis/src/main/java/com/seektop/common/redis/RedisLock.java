package com.seektop.common.redis;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

@Slf4j
@Component
public class RedisLock {


    @Autowired
    private RedisTemplate<String, String> redisTemplate;

    private ThreadLocal<HashMap<String,String>> lockFlag = ThreadLocal.withInitial(()->new HashMap<>());

    public static final String UNLOCK_LUA;

    static {
        StringBuilder sb = new StringBuilder();
        sb.append("if redis.call(\"get\",KEYS[1]) == ARGV[1] ");
        sb.append("then ");
        sb.append("    return redis.call(\"del\",KEYS[1]) ");
        sb.append("else ");
        sb.append("    return 0 ");
        sb.append("end ");
        UNLOCK_LUA = sb.toString();
    }

    /**
     *
     * @param key  redis key
     * @param expire 过期时间：超过该时间释放锁 单位:秒
     * @param retryTimes 重试次数
     * @param sleepMillis 重试时间间隔
     * @return
     */
    public boolean lock(String key, long expire, int retryTimes, long sleepMillis) {
        boolean result = lock(key, expire);
        // 如果获取锁失败，按照传入的重试次数进行重试
        while((!result) && retryTimes-- > 0){
            try {
                log.debug("lock failed, retrying..." + retryTimes);
                Thread.sleep(sleepMillis);
            } catch (InterruptedException e) {
                return false;
            }
            result = lock(key, expire);
        }
        return result;
    }

    private boolean lock(String key, long expire) {
        try {
            Boolean result = redisTemplate.execute((RedisCallback<Boolean>) connection -> {
                String uuid = UUID.randomUUID().toString();
                lockFlag.get().put(key,uuid);
                return connection.set(key.getBytes(),uuid.getBytes(), Expiration.seconds(expire) , RedisStringCommands.SetOption.ifAbsent());
            });
            return result;
        } catch (Exception e) {
            log.error("set redis  exception", e);
        }
        return false;
    }

    /**
     *
     * @param key
     * @return
     * true:正常释放锁
     * false:表示其他线程已经占有了锁，根据业务需求，可以强制释放【删除key】
     */
    public boolean releaseLock(String key) {
        // 释放锁的时候，有可能因为持锁之后方法执行时间大于锁的有效期，此时有可能已经被另外一个线程持有锁，所以不能直接删除
        try {
            DefaultRedisScript<Boolean> lockScript  = new DefaultRedisScript<Boolean>();
            lockScript.setScriptText(UNLOCK_LUA);
            lockScript.setResultType(Boolean.class);
            // 封装参数
            List<String> keyList = new ArrayList<>();
            keyList.add(key);
            /***
             * 踩坑记录 需要指定redisTemplate泛型或者指定取值序列化 否则读出的值不是标准的String字符串 @Autowired
             */
            Boolean result = (Boolean) redisTemplate.execute(lockScript, keyList,lockFlag.get().get(key));
            lockFlag.get().remove(key);
            return result;
        } catch (Exception e) {
            log.error("release lock  exception", e);
        }
        return false;
    }

}


package com.seektop.common.redis.impl;

import com.alibaba.fastjson.JSON;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JavaType;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.Lists;
import com.seektop.common.nacos.DynamicKey;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.enumerate.push.Channel;
import com.seektop.enumerate.push.GlMsgPack;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import redis.clients.jedis.*;
import redis.clients.jedis.params.SetParams;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisServiceImpl implements RedisService {

    /**
     * 空白占位符
     */
    private final String BLANK_CONTENT = "__BLANK__";

    private static final String prefix = "push:channel:";

    private final String OK_CODE = "OK";
    private final String OK_MULTI_CODE = "+OK";

    private final String upperCaseChar = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    /**
     * 默认过期时间 ，3600(秒)
     */
    private final int DEFAULT_EXPIRE_TIME = 3600;

    @Resource
    private Environment env;

    @Resource
    private DynamicKey dynamicKey;

    private JedisPool pool;
    private JedisPool readOnlyPool;
    private JedisPool pushPool;

    private ObjectMapper om = new ObjectMapper() {
        {
            this.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,false);
        }
    };

    @Override
    public void publish(String channel, String message) {
        try (Jedis jedis = getPushResource()) {
            log.info("通过Redis推送的频道ID是{}, 推送的内容是{}", channel, message);
            jedis.publish(channel, message);
        } catch (Exception ex) {
            log.error("通过Redis推送发生异常：频道{} 消息{}发生异常", channel, message, ex);
        }
    }

    @Override
    public List<String> scanKey(String param) {
        try (Jedis jedis = getReadResource()) {
            List<String> result = Lists.newArrayList();
            String cursor = ScanParams.SCAN_POINTER_START;
            ScanParams scanParams = new ScanParams();
            scanParams.match(param);
            scanParams.count(10000);
            while (true) {
                ScanResult<String> scanResult = jedis.scan(cursor, scanParams);
                result.addAll(scanResult.getResult());
                cursor = scanResult.getCursor();
                if (cursor.equals(ScanParams.SCAN_POINTER_START)) {
                    break;
                }
            }
            return result;
        }
    }

    @Override
    public String set(String key, Object obj, int expireTime) {
        String value = BLANK_CONTENT;
        if (obj != null) {
            try {
                value = om.writeValueAsString(obj);
            } catch (IOException e) {
                log.error("Can not write object to redis: {}", obj.toString(), e);
            }
        }
        return set(key, value, expireTime);
    }

    @Override
    public String set(String key, String value, int expireTime) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return null;
        }
        if (value == null) {
            log.warn("Params value is null!");
            return null;
        }
        try (Jedis jedis = getResource()) {
            String result = jedis.set(key, value);
            if (expireTime > 0) {
                jedis.expire(key, expireTime);
            }
            return result;
        }
    }

    /**
     * 判断 返回值是否ok.
     */
    public boolean isStatusOk(String status) {
        return (status != null) && (OK_CODE.equals(status) || OK_MULTI_CODE.equals(status) || "1".equals(status));
    }

    @Override
    public <T> T getHashObject(String key, String field, Class<T> clazz) {
        try (Jedis jedis = getReadResource()) {
            if (!jedis.hexists(key, field)) {
                return null;
            }
            String json = jedis.hget(key, field);
            return json == null ? null : JSON.parseObject(json, clazz);
        }
    }

    @Override
    public Long incrBy(String key, long value) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return null;
        }
        try (Jedis jedis = getResource()) {
            return jedis.incrBy(key, value);
        }
    }

    @Override
    public void setTTL(String key, int seconds) {
        if (seconds < 0) {
            return;
        }
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return;
        }
        try (Jedis jedis = getResource()) {
            jedis.expire(key, seconds);
        }
    }

    @Override
    public Long getTTL(String key) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return 0L;
        }
        try (Jedis jedis = getResource()) {
            Long ttl = jedis.ttl(key);
            if (ttl == null) {
                return 0L;
            }
            return ttl;
        }
    }

    @Override
    public long generateIncr(String key) {
        try (Jedis jedis = getResource()) {
            return jedis.incrBy(key, 1);
        }
    }

    @Override
    public <T> T get(String key, Class<T> clazz) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return null;
        }
        if (clazz == null) {
            log.warn("Params clazz is null!");
            return null;
        }
        String value = get(key);
        if (StringUtils.isBlank(value) || StringUtils.equalsIgnoreCase(value, BLANK_CONTENT)) {
            return null;
        }
        T obj = null;
        try {
            obj = om.readValue(value, clazz);
        } catch (IOException e) {
            log.error("Can not unserialize obj to [{}] with string [{}]", clazz.getName(), value);
        }
        return obj;
    }

    @Override
    public String get(String key) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return StringUtils.EMPTY;
        }
        try (Jedis jedis = getReadResource()) {
            return jedis.get(key);
        }
    }

    @Override
    public Long delete(String... keys) {
        if (keys == null || keys.length == 0) {
            log.warn("Params keys is null or 0 length!");
            return -1L;
        }
        try (Jedis jedis = getResource()) {
            return jedis.del(keys);
        }
    }

    @Override
    public <T> RedisResult<T> getListResult(String key, Class<T> elementClazz) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        if (elementClazz == null) {
            return null;
        }
        RedisResult<T> redisResult = new RedisResult<>();
        String value = get(key);
        if (StringUtils.isBlank(value)) {
            redisResult.setExist(false);
            return redisResult;
        }
        //到此步，则表明redis中存在key
        redisResult.setExist(true);
        if (StringUtils.equalsIgnoreCase(value, BLANK_CONTENT)) {
            return redisResult;
        }
        List<T> list;
        try {
            list = om.readValue(value, getCollectionType(List.class, elementClazz));
            redisResult.setListResult(list);
        } catch (IOException e) {
            log.error("getListResult error : {}", e);
            //到此步直接视为无值
            redisResult.setExist(false);
        }
        return redisResult;
    }

    @Override
    public String getTradeNo(String prefix) {
        StringBuilder tradeNo = new StringBuilder();
        tradeNo.append(prefix);
        tradeNo.append(DateFormatUtils.format(new Date(), "yyyyMMddHHmm"));
        long sequence = generateIncr("trade_sequence");
        tradeNo.append(numToSixUpperString(sequence));
        // 8亿重置
        if (sequence >= 800000000) {
            set("trade_sequence", 1);
        }
        return tradeNo.toString();
    }

    @Override
    public Long lPush(String key, String... members) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try (Jedis jedis = getResource()) {
            return jedis.lpush(key, members);
        }
    }

    @Override
    public String lPOP(String key) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try (Jedis jedis = getResource()) {
            String lPop = jedis.lpop(key);
            return lPop;
        }
    }

    @Override
    public boolean lTrim(String key, int beginIndex, int endIndex) {
        if (StringUtils.isBlank(key)) {
            return false;
        }
        try (Jedis jedis = getResource()) {
            String result = jedis.ltrim(key, beginIndex, endIndex);
            if (!"OK".equals(result) || null == result) {
                return false;
            }
            return true;
        }
    }

    @Override
    public int setNx(String key, String value, int expireTime) {
        if (StringUtils.isBlank(key)) {
            return 0;
        }
        try (Jedis jedis = getResource()) {
            Integer count = jedis.setnx(key, value).intValue();
            if (null != count && count > 0) {
                jedis.expire(key, expireTime);
                return 1;
            }
            return 0;
        }
    }

    @Override
    public long lLEN(String key) {
        if (StringUtils.isBlank(key)) {
            return 0;
        }
        try (Jedis jedis = getResource()) {
            return jedis.llen(key);
        }
    }

    @Override
    public List<String> lRange(String key, long start, long end) {
        if (StringUtils.isBlank(key)) {
            return null;
        }
        try (Jedis jedis = getResource()) {
            return jedis.lrange(key, start, end);
        }
    }

    public void publish(Channel channel, Integer userId, Object data) {
        String msg = GlMsgPack.pack(channel, data);
        String channelName = prefix + channel.value();
        if (userId != null) {
            channelName += ":" + userId;
        }
        try (Jedis jedis = getPushResource()) {
            jedis.publish(channelName, msg);
        }
    }

    @Override
    public long scard(String key) {
        if (StringUtils.isBlank(key)) {
            return 0;
        } else {
            try (Jedis jedis = getReadResource()) {
                return jedis.scard(key);
            }
        }
    }

    @Override
    public boolean exists(String key) {
        if (StringUtils.isBlank(key)) {
            //不接受空值
            return false;
        }
        try (Jedis jedis = getResource()) {
            return jedis.exists(key);
        }
    }

    @Override
    public boolean getLock(String key, Integer second) {
        try (Jedis jedis = getResource()) {
            long result = jedis.setnx(key, "");
            if (result > 0) {
                jedis.expire(key, second);
                return true;
            } else {
                return false;
            }
        }
    }

    @Override
    public void getSafetyLock(String key, Integer second, Integer safetyTime) throws Exception {
        long waitMax = TimeUnit.SECONDS.toMillis(safetyTime);
        long waitAlready = 0;
        // 持续获取锁
        while (getLock(key, second) == false && waitAlready < waitMax) {
            Thread.sleep(1000);
            waitAlready += 1000;
        }
    }

    @Override
    public String set(String key, Object obj) {
        String value = BLANK_CONTENT;
        if (obj != null) {
            try {
                value = om.writeValueAsString(obj);
            } catch (IOException e) {
                log.error("Can not write object to redis:" + obj.toString(), e);
            }
        }
        return set(key, value);
    }

    @Override
    public String set(String key, String value) {
        return this.set(key, value, DEFAULT_EXPIRE_TIME);
    }

    private <T> JavaType getCollectionType(@SuppressWarnings("rawtypes") Class<? extends Collection> collectionClazz, Class<T> elementClazz) {
        return om.getTypeFactory().constructCollectionType(collectionClazz, elementClazz);
    }

    private Jedis getResource() {
        return pool.getResource();
    }

    private Jedis getReadResource() {
        if(!dynamicKey.redisRwSeparation()){
            return pool.getResource();
        }
        return readOnlyPool.getResource();
    }

    private Jedis getPushResource() {
        return pushPool.getResource();
    }

    @PostConstruct
    public void init() {
        JedisPoolConfig config = new JedisPoolConfig();
        int maxIdle = env.getProperty("spring.redis.pool.max-idle") == null ? JedisPoolConfig.DEFAULT_MAX_IDLE : Integer.valueOf(env.getProperty("spring.redis.pool.max-idle"));
        int maxTotal = env.getProperty("spring.redis.pool.max-active") == null ? JedisPoolConfig.DEFAULT_MAX_TOTAL : Integer.valueOf(env.getProperty("spring.redis.pool.max-active"));
        long maxWaitMillis = env.getProperty("spring.redis.pool.max-wait") == null ? JedisPoolConfig.DEFAULT_MAX_WAIT_MILLIS : Long.valueOf(env.getProperty("spring.redis.pool.max-wait"));
        int port = env.getProperty("spring.redis.port") == null ? 6379 : Integer.valueOf(env.getProperty("spring.redis.port"));

        config.setMaxIdle(maxIdle);
        config.setMaxTotal(maxTotal);
        config.setMaxWaitMillis(maxWaitMillis);
        config.setTestOnBorrow(true);
        config.setBlockWhenExhausted(true);

        int timeout = env.getProperty("spring.redis.timeout") == null ? 0 : Integer.valueOf(env.getProperty("spring.redis.timeout"));
        String password = env.getProperty("spring.redis.password");
        String readOnlyHost = env.getProperty("seektop.redis.read.host");
        if (org.apache.commons.lang3.StringUtils.isEmpty(password)) {
            pool = new JedisPool(config, env.getProperty("spring.redis.host"), port, timeout);
            if(StringUtils.isEmpty(readOnlyHost)){
                readOnlyPool = pool;
            }else {
                readOnlyPool = new JedisPool(config, env.getProperty("seektop.redis.read.host"), port, timeout);
            }
        } else {
            pool = new JedisPool(config, env.getProperty("spring.redis.host"), port, timeout, password);
            if(StringUtils.isEmpty(readOnlyHost)){
                readOnlyPool = pool;
            }else {
                readOnlyPool = new JedisPool(config, env.getProperty("seektop.redis.read.host"), port, timeout,password);
            }
        }
        // 推送使用的单独RedisPool
        String pushRedisHost = env.getProperty("spring.redis.host.push", String.class, "127.0.0.1");
        String pushRedisPassword = env.getProperty("spring.redis.password.push", String.class, "");
        int pushRedisPort = env.getProperty("spring.redis.port.push", Integer.class, 6379);
        if (org.springframework.util.StringUtils.isEmpty(pushRedisPassword)) {
            pushPool = new JedisPool(config, pushRedisHost, pushRedisPort, timeout);
        } else {
            pushPool = new JedisPool(config, pushRedisHost, pushRedisPort, timeout, pushRedisPassword);
        }
    }

    @PreDestroy
    public void destroy() {
        try {
            if (pool != null) {
                pool.destroy();
            }
            if(null != readOnlyPool){
                readOnlyPool.destroy();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 将数字转换成6位大写字符串
     *
     * @param num
     * @return
     */
    private String numToSixUpperString(long num) {
        return numToUpperString(num, 6);
    }

    /**
     * 将数字转换成大写字符串
     *
     * @param num
     * @param len
     * @return
     */
    private String numToUpperString(long num, int len) {
        StringBuilder sb = new StringBuilder();
        while (num > 0 && sb.length() < len) {
            sb.insert(0, upperCaseChar.charAt((int) (num % upperCaseChar.length())));
            num = num / upperCaseChar.length();
        }
        int length = sb.length();
        for (int i = 0; i < len - length; i++) {
            sb.insert(0, "0");
        }
        return sb.toString();
    }

    /**
     * 仅当redis中不含对应的key时，设定缓存内容
     *
     * @param key
     * @param value
     * @param expiredTime 缓存内容过期时间 （单位：秒） ，expireTime必须大于0
     * @return
     */
    @Override
    public String setnx(String key, String value, long expiredTime) {
        try (Jedis jedis = getResource()) {
            return jedis.set(key, value, SetParams.setParams().ex((int) expiredTime).nx());
        }
    }

    /**
     * 仅当redis中含有对应的key时，修改缓存内容
     *
     * @param key
     * @param value
     * @param expiredTime 缓存内容过期时间 （单位：秒） ，expireTime必须大于0
     * @return
     */
    @Override
    public String setxx(String key, String value, long expiredTime) {
        try (Jedis jedis = getResource()) {
            return jedis.set(key, value, SetParams.setParams().ex((int) expiredTime).xx());
        }
    }

    @Override
    public Set<String> smembers(String key) {
        if (StringUtils.isBlank(key)) {
            log.warn("key is blank");
            return null;
        }
        Set<String> result;
        try (Jedis jedis = this.getReadResource()) {
            result = jedis.smembers(key);
            return result;
        }
    }

    @Override
    public boolean sismember(String key, String memeber) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(memeber)) {
            return false;
        }
        boolean result;
        try (Jedis jedis = getResource()) {
            result = jedis.sismember(key, memeber);
            return result;
        }
    }

    /**
     * 自增map
     *
     * @param key
     * @param field
     * @param time
     * @param unit
     * @return
     */
    @Override
    public long getAndIncrFromHash(String key, String field, int time, TimeUnit unit) {
        long result = 0;
        try (Jedis jedis = getResource()) {
            if (jedis.hexists(key, field)) {
                return jedis.hincrBy(key, field, 1);
            }
            jedis.hset(key, field, "1");
            jedis.expire(key, time);
            return result;
        }
    }

    /**
     * 获取hash的值
     */
    @Override
    public Map<String, String> hgetAll(String key) {
        try (Jedis jedis = getReadResource()) {
            return jedis.hgetAll(key);
        }
    }

    @Override
    public void putHashValueWithExpireDate(String key, String field, Object value, int time, TimeUnit unit) {
        try (Jedis jedis = getResource()) {
            jedis.hset(key, field, JSON.toJSONString(value));
            jedis.expire(key, time);
        }
    }

    @Override
    public void putHashValue(String key, String field, Object value) {
        try (Jedis jedis = getResource()) {
            jedis.hset(key, field, JSON.toJSONString(value));
        }
    }

    @Override
    public void hashIncr(String key, String field, Integer num) {
        try (Jedis jedis = getResource()) {
            if (jedis.hexists(key, field)) {
                jedis.hincrBy(key, field, num);
            }
        }
    }

    @Override
    public void putOrIncrHash(String key, String field, Integer num) {
        try (Jedis jedis = getResource()) {
            if (jedis.hexists(key, field)) {
                jedis.hincrBy(key, field, num);
            } else {
                jedis.hset(key, field, JSON.toJSONString(num));
            }
        }
    }

    @Override
    public <T> List<T> getHashList(String key, String field, Class<T> clazz) {
        try (Jedis jedis = getReadResource()) {
            if (!jedis.hexists(key, field)) {
                return null;
            }
            String json = jedis.hget(key, field);
            return json == null ? null : JSON.parseArray(json, clazz);
        }
    }

    @Override
    public List<String> hvalsHashValues(String key) {
        try (Jedis jedis = getReadResource()) {
            List<String> list = jedis.hvals(key);
            return list;
        }
    }

    @Override
    public String getStringFromHash(String key, String field) {
        try (Jedis jedis = getReadResource()) {
            if (!jedis.hexists(key, field)) {
                return null;
            }
            String json = jedis.hget(key, field);
            return json == null ? null : JSON.parseObject(json, String.class);
        }
    }

    /**
     * 删除HASH值
     *
     * @param key
     * @param field
     */
    @Override
    public void delHashValue(String key, String field) {
        try (Jedis jedis = getResource()) {
            jedis.hdel(key, field);
        }
    }

    /**
     * @Author: Sim
     * @Description: 获取在线用户数 TODO 数据量大要测试性能问题
     * @params:
     * @Date: 下午4:02 2018/7/23
     */
    @Override
    public long getKeysLen(String key) {
        try (Jedis jedis = getResource()) {
            Set<String> set = jedis.keys(key);
            return set.size();
        }
    }

    /**
     * @Description: 生成随机的数值交易流水号
     */
    @Override
    public Long getTradeNum() {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmm");
        StringBuilder tradeNo = new StringBuilder();
        tradeNo.append(sdf.format(now));
        long sequence = generateIncr("trade_sequence");
        if (sequence >= 8000000) { // 8百万重置
            set("trade_sequence", 1);
        }
        tradeNo.append(sequence);
        return Long.parseLong(tradeNo.toString());
    }

    /**
     * @Author: Sim
     * @Description: 随机移除并返回一个元素
     * @params:
     * @Date: 下午1:57 2018/3/30
     */
    @Override
    public String spop(String key) {
        if (StringUtils.isBlank(key)) {
            log.warn("key is blank");
            return null;
        }
        String result;
        try (Jedis jedis = getResource()) {
            result = jedis.spop(key);
            return result;
        }
    }

    /**
     * @Author: Sim
     * @Description: 把一个或多个元素添加到指定集合
     * @params:
     * @Date: 下午1:29 2018/3/30
     */
    @Override
    public Long sadd(String key, String members) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(members)) {
            log.warn("key or member is blank");
            return null;
        }
        Long result = null;
        try (Jedis jedis = getResource()) {
            result = jedis.sadd(key, members);
            return result;
        }
    }

    @Override
    public Long sadd(String key, String... members) {
        if (ObjectUtils.isEmpty(key) || ObjectUtils.isEmpty(members)) {
            log.warn("key or member is blank");
            return null;
        }
        Long result = null;
        try (Jedis jedis = getResource()) {
            result = jedis.sadd(key, members);
            return result;
        }
    }

    /**
     * @Author: Sim
     * @Description: 添加元素到指定集合并设置集合的有效期
     * @params:
     * @Date: 下午4:02 2018/3/30
     */
    @Override
    public boolean sadd(String key, String members, int expireTime) {
        if (StringUtils.isBlank(key) || StringUtils.isBlank(members)) {
            log.warn("key or member is blank");
            return false;
        }
        List<Object> result = null;
        try (Jedis jedis = getResource()) {
            // 开始事务
            Transaction transaction = jedis.multi();
            transaction.sadd(key, members);
            transaction.expire(key, expireTime);
            // 执行事务 result中返回两个Long类型的1
            result = transaction.exec();
            for (Object rt : result) {
                if (!isStatusOk(rt.toString())) {
                    return false;
                }
            }
            return true;
        }
    }


    /**
     * 添加zset有序集合
     *
     * @param key
     * @return
     */
    @Override
    public void zadd(String key, Double score, String member) {
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return;
        }
        try (Jedis jedis = this.getResource()){
            jedis.zadd(key, score, member);
        }
    }

    /**
     * 从大到小获取zset有序集合
     *
     * @param key
     * @return
     */
    @Override
    public Set<String> zrevrange(String key, Long start, Long end) {
        Set<String> zrevrange = new HashSet<>();

        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return zrevrange;
        }
        try(Jedis jedis = this.getResource()) {
            zrevrange = jedis.zrevrange(key, start, end);
            return zrevrange;
        }
    }

    /**
     * 从小到大获取zset有序集合
     *
     * @param key
     * @return
     */
    @Override
    public Set<String> zrange(String key, Long start, Long end) {
        Set<String> zrange = new HashSet<>();
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return zrange;
        }
        try(Jedis jedis = this.getResource()) {
            zrange = jedis.zrange(key, start, end);
            return zrange;
        }
    }

    /**
     * 获取分数
     */
    @Override
    public Double zscore(String key, String member) {
        Double zscore = new Double("0");
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return zscore;
        }
        try(Jedis jedis = this.getResource()) {
            zscore = jedis.zscore(key, member);
            return zscore;
        }
    }

    @Override
    public Set<Tuple> zrevrangeWithCore(String key, Long start, Long end) {
        Set<Tuple> zrange = new HashSet<>();
        if (StringUtils.isBlank(key)) {
            log.warn("Params key is blank!");
            return zrange;
        }
        try(Jedis jedis = this.getResource()) {
            zrange = jedis.zrangeWithScores(key, start, end);
            return zrange;
        }
    }

}
package com.seektop.common.redis;

import com.seektop.enumerate.push.Channel;
import redis.clients.jedis.Tuple;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public interface RedisService {

    void publish(final String channel, final String message);

    /**
     * 扫描匹配条件的Key
     *
     * @param param
     * @return
     */
    List<String> scanKey(String param);

    /**
     * 写入数据
     *
     * @param key
     * @param obj
     * @return
     */
    String set(String key, Object obj);

    /**
     * 写入/修改 缓存内容
     *
     * @param key
     * @param value
     * @return
     */
    String set(String key, String value);

    /**
     * 写入数据
     *
     * @param key
     * @param obj
     * @param expireTime
     * @return
     */
    String set(String key, Object obj, int expireTime);

    /**
     * 写入字符串数据
     *
     * @param key
     * @param value
     * @param expireTime
     * @return
     */
    String set(String key, String value, int expireTime);

    /**
     * 从Hash集合中获取数据
     *
     * @param key
     * @param field
     * @param clazz
     * @param <T>
     * @return
     */
    <T> T getHashObject(String key, String field, Class<T> clazz);

    /**
     * 加法运算
     *
     * @param key
     * @param value
     * @return
     */
    Long incrBy(String key, long value);

    /**
     * 设置Key过期时间
     *
     * @param key
     * @param seconds
     * @return
     */
    void setTTL(String key, int seconds);

    /**
     * 获取 redis 对应的key的剩余存活时间
     *
     * @param key
     */
    Long getTTL(String key);

    /**
     * 获取一个自增的ID
     *
     * @param key
     * @return
     */
    long generateIncr(String key);

    /**
     * 获取数据
     *
     * @param key
     * @param clazz
     * @param <T>
     * @return
     */
    <T> T get(String key, Class<T> clazz);

    /**
     * 获取字符串类型的数据
     *
     * @param key
     * @return
     */
    String get(String key);

    /**
     * 删除对应Key
     *
     * @param keys
     * @return
     */
    Long delete(String... keys);

    /**
     * 根据key取Collection对象数据
     *
     * @param key
     * @param elementClazz
     * @param <T>
     * @return
     */
    <T> RedisResult<T> getListResult(String key, Class<T> elementClazz);

    String getTradeNo(String prefix);

    /**
     * 将一个或多个值插入到列表头部。 如果 key 不存在，一个空列表会被创建并执行 LPUSH 操作。 当 key 存在但不是列表类型时，返回一个错误。
     * expireTime = 0 则无过期时间
     * return list.size
     */
    Long lPush(String key, String... members);

    /**
     * 用于移除并返回列表的第一个元素。
     * return elementClazz类型对象
     */
    String lPOP(String key);


    /**
     * 对一个列表进行修剪(trim)，就是说，让列表只保留指定区间内的元素，不在指定区间之内的元素都将被删除。
     * 下标 0 表示列表的第一个元素，以 1 表示列表的第二个元素，以此类推。 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此类推。
     * */
    boolean lTrim(String key, int beginIndex, int endIndex);


    /**
     * 命令在设置成功时返回 1 ， 设置失败时返回 0 。
     * */
    int setNx(String key, String value, int expireTime);

    /**
     * 返回列表的长度。 如果列表 key 不存在，则 key 被解释为一个空列表，返回 0 。 如果 key 不是列表类型，返回一个错误。
     * */
    long lLEN(String key);

    /**
     * 返回列表中指定区间内的元素，区间以偏移量 START 和 END 指定。 其中 0 表示列表的第一个元素， 1 表示列表的第二个元素，
     * 以此类推。 你也可以使用负数下标，以 -1 表示列表的最后一个元素， -2 表示列表的倒数第二个元素，以此类推。
     * */
    List<String> lRange(String key, long start, long end);

    /**
     * 发送订阅消息
     */
    void publish(Channel channel, Integer userId, Object data);

    /**
     * 返回目标集合的数量
     */
    long scard(String key);

    Map<String, String> hgetAll(String key);

    void putHashValue(String key, String field, Object value);

    void delHashValue(String key, String field);

    /**
     * 判断对应的key是否存在
     *
     * @param key
     * @return
     */
    boolean exists(String key);

    /**
     * 获取锁
     * @param key
     * @param second
     * @return
     */
    boolean getLock(String key, Integer second);

    /**
     * 持续获取锁
     *
     * @param key
     * @param second
     * @param safetyTime
     * @throws Exception
     */
    void getSafetyLock(final String key, final Integer second, final Integer safetyTime) throws Exception;

    long getAndIncrFromHash(String key, String field, int time, TimeUnit unit);

    void putHashValueWithExpireDate(String key, String field, Object value, int time, TimeUnit unit);

    /**
     * Hash incr增减操作
     *
     * @param key   键
     * @param field 子
     * @param num   增减数
     * @author Chaims
     * @date 2019/3/16 11:27
     */
    void hashIncr(String key, String field, Integer num);

    /**
     * 没有则新增,有则加
     *
     * @param key   键
     * @param field 子
     * @param num   初始值(增减数)
     * @author Chaims
     * @date 2019/3/16 14:34
     */
    void putOrIncrHash(String key, String field, Integer num);

    String getStringFromHash(String key, String field);

    <T> List<T> getHashList(String key, String field, Class<T> clazz);

    /**
     * 根据key获取所有的value
     *
     * @param key ;
     * @return java.util.List<java.lang.String>
     * @author Chaims
     * @date 2019/3/18 21:18
     */
    List<String> hvalsHashValues(String key);

    long getKeysLen(String key);

    /**
     * 获取纯数字分布式单号
     * return channelID + tradeNum
     */
    Long getTradeNum();


    /**
     * @Author: Sim
     * @Description: 随机移除并返回一个元素
     * @params:
     * @Date: 下午1:57 2018/3/30
     */
    String spop(String key);

    /**
     * @Author: Sim
     * @Description: 把一个或多个元素添加到指定集合
     * @params:
     * @Date: 下午1:29 2018/3/30
     */
    Long sadd(String key, String members);

    Long sadd(String key, String... members);

    /**
     * @Author: Sim
     * @Description: 添加元素到指定集合并设置集合的有效期
     * @params:
     * @Date: 下午4:02 2018/3/30
     */
    boolean sadd(String key, String members, int expireTime);

    /**
     * 仅当redis中不含对应的key时，设定缓存内容
     *
     * @param key
     * @param value
     * @param expiredTime 缓存内容过期时间 （单位：秒） ，若expireTime小于0 则表示该内容不过期
     * @return
     */
    String setnx(String key, String value, long expiredTime);

    /**
     * 仅当redis中含有对应的key时，修改缓存内容
     *
     * @param key
     * @param value
     * @param expiredTime 缓存内容过期时间 （单位：秒） ，若expireTime小于0 则表示该内容不过期
     * @return
     */
    String setxx(String key, String value, long expiredTime);

    Set<String> smembers(String key);

    boolean sismember(String key, String memeber);

    /**
     * 添加有序集合
     *
     * @param key
     * @param score
     * @param member
     */
    void zadd(String key, Double score, String member);


    /**
     * 从大到小获取有序集合
     */
    Set<String> zrevrange(String key, Long start, Long end);

    /**
     * 从小到大获取有序集合
     */
    Set<String> zrange(String key, Long start, Long end);

    /**
     * 获取分数
     */
    Double zscore(String key, String name);

    /**
     * 从大到小获取有序集合
     */
    Set<Tuple> zrevrangeWithCore(String key, Long start, Long end);
}
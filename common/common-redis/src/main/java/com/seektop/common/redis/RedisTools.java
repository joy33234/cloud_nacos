package com.seektop.common.redis;

import com.seektop.common.redis.function.TransactionalFunction;
import com.seektop.constant.fund.Constants;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.Language;
import com.seektop.enumerate.TradeNoEnum;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.stereotype.Component;
import org.springframework.data.redis.core.*;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Date;
import java.util.concurrent.TimeUnit;

@Component
public class RedisTools {

    private static final String upperCaseChar = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ";

    private static CommonRedisTemplate redisTemplate;

    private static StringRedisTemplate stringRedisTemplate;

    @Autowired
    public void setRedisTemplate(CommonRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    @Autowired
    public void setStringRedisTemplate(StringRedisTemplate redisTemplate) {
        this.stringRedisTemplate = redisTemplate;
    }

    public static <T> CommonValueOperations<String, T> valueOperations() {
        return redisTemplate.getValueOps();
    }

    public static ValueOperations<String, String> stringOperations() {
        return stringRedisTemplate.opsForValue();
    }

    public static ListOperations<String, String> listOperations() {
        return redisTemplate.getListOps();
    }

    public static <T> CommonHashOperations<String, String, T> hashOperations() {
        return redisTemplate.getHashOperations();
    }

    public static CommonRedisTemplate template(){
        return redisTemplate;
    }

    public static <T> CommonSetOperations<String, T> setOperations() {
        return redisTemplate.getSetOps();
    }

    /**
     * redis 事务执行器
     *
     * @param supplier
     * @param <T>
     * @return
     */
    public static <T> T doTransaction(TransactionalFunction<T> supplier) {
        T execute = (T) redisTemplate.execute(new SessionCallback<T>() {
            public T execute(RedisOperations operations) throws DataAccessException {
                operations.multi();
                try {
                    T t = supplier.doTransaction();
                    operations.exec();
                    return t;
                } catch (Exception e) {
                    operations.discard();
                    throw new RuntimeException(e);
                }
            }
        });
        return execute;
    }

    /**
     * 设置用户有效识别码
     *
     * @param userId
     * @param validCode
     * @param ttl
     */
    public static void setUserValidCode(Integer userId, String validCode, long ttl) {
        valueOperations().set(KeyConstant.USER.USER_VALID_CODE + userId, validCode, ttl, TimeUnit.SECONDS);
    }

    /**
     * 获取用户有效识别码
     *
     * @param userId
     * @return
     */
    public static String getUserValidCode(Integer userId) {
        return valueOperations().get(KeyConstant.USER.USER_VALID_CODE + userId, String.class);
    }

    /**
     * 获取用户默认币种
     *
     * @param userId
     * @return
     */
    public static String getUserDefaultDigitalCoin(Integer userId) {
        return valueOperations().get(KeyConstant.DIGITAL.USER_DEFAULT_DIGITAL_COIN_CACHE + userId, String.class);
    }

    /**
     * 获取交易单号
     *
     * @param tradeNoEnum
     * @return
     */
    public static String getTradeNo(TradeNoEnum tradeNoEnum) {
        StringBuilder tradeNo = new StringBuilder();
        tradeNo.append(tradeNoEnum.code());
        tradeNo.append(DateFormatUtils.format(new Date(), "yyyyMMddHHmm"));
        long sequence = valueOperations().increment("trade_sequence", 1);
        tradeNo.append(numToUpperString(sequence, 6));
        // 8亿重置
        if (sequence >= 800000000) {
            valueOperations().set("trade_sequence", 1);
        }
        return tradeNo.toString();
    }

    /**
     * 获取用户信息
     *
     * @param userId
     * @return
     */
    public static GlUserDO getUser(Integer userId) {
        return valueOperations().get(KeyConstant.USER.DETAIL_CACHE + userId, GlUserDO.class);
    }

    /**
     * 设置充值订单付款超时倒计时
     *
     * @param orderId
     */
    public static void setDigitalRechargeTimeoutExpired(String orderId) {
        valueOperations().set(KeyConstant.DIGITAL.RECHARGE_ORDER_TIMEOUT_EXPIRED + orderId, orderId, Constants.DIGITAL_RECHARGE_ORDER_TIMEOUT_TIME, TimeUnit.MINUTES);
    }

    /**
     * 删除充值订单付款超时倒计时
     *
     * @param orderId
     */
    public static void delDigitalRechargeTimeoutExpired(String orderId) {
        template().delete(KeyConstant.DIGITAL.RECHARGE_ORDER_TIMEOUT_EXPIRED + orderId);
    }

    /**
     * 获取充值订单付款超时倒计时
     *
     * @param orderId
     * @param timeUnit
     * @return
     */
    public static long getDigitalRechargeTimeoutExpired(String orderId, TimeUnit timeUnit) {
        return template().getExpire(KeyConstant.DIGITAL.RECHARGE_ORDER_TIMEOUT_EXPIRED + orderId, timeUnit);
    }

    /**
     * 获取指定币种兑CNY的汇率
     *
     * @param coin
     * @return
     */
    public static BigDecimal getExchangeRate(String coin) {
        return hashOperations().get(KeyConstant.DIGITAL.C2C_EXCHANGE_RATE_CNY, coin, BigDecimal.class);
    }

    /**
     * 设置指定币种兑CNY的汇率
     *
     * @param coin
     * @param rate
     */
    public static void setExchangeRate(String coin, BigDecimal rate) {
        hashOperations().put(KeyConstant.DIGITAL.C2C_EXCHANGE_RATE_CNY, coin, rate);
    }

    /**
     * 设置用户的默认语言
     *
     * @param userId
     * @param language
     */
    public static void setUserDefaultLanguage(Integer userId, Language language) {
        valueOperations().set(KeyConstant.USER.USER_DEFAULT_LANGUAGE + userId, language.getCode());
    }

    /**
     * 获取用户的默认语言
     *
     * @param userId
     * @return
     */
    public static Language getUserDefaultLanguage(Integer userId) {
        String languageCode = valueOperations().get(KeyConstant.USER.USER_DEFAULT_LANGUAGE + userId, String.class);
        if (StringUtils.isEmpty(languageCode)) {
            return Language.ZH_CN;
        } else {
            Language language = Language.getLanguage(languageCode);
            if (ObjectUtils.isEmpty(language)) {
                return Language.ZH_CN;
            } else {
                return language;
            }
        }
    }

    protected static String numToUpperString(long num, int len) {
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

}
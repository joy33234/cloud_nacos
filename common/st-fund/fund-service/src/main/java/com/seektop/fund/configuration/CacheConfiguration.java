package com.seektop.fund.configuration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 缓存管理配置
 */
@Configuration
public class CacheConfiguration {

    /**
     * 数据缓存时间管理
     * @return
     */
    @Bean
    public Map<String, Long> expires(){
        Map<String, Long> expires = new HashMap<>();
        // 缓存名称，缓存时间（秒）
        expires.put(BANK, 180L);
        expires.put(FUND_USER_LEVEL, 600L);
        expires.put(FUND_RECHARGE_MANAGE_OPERATORS, 3600L);
        expires.put(AUTO_CONDITION_MERCHANT_ACCOUNT, 3600L);
        return expires;
    }

    /**
     * 银行缓存名称
     */
    public final static String BANK = "fund_bank";
    public final static String FUND_USER_LEVEL = "fund_user_level";
    public final static String FUND_RECHARGE_MANAGE_OPERATORS = "fund_recharge_manage_operators";
    public final static String AUTO_CONDITION_MERCHANT_ACCOUNT = "fund_withdraw_auto_condition_merchant_account";
}

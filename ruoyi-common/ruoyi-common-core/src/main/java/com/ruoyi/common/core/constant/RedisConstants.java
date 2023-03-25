package com.ruoyi.common.core.constant;

import com.ruoyi.common.core.utils.DateUtil;

import java.util.Date;

/**
 * 权限相关通用常量
 * 
 * @author ruoyi
 */
public class RedisConstants
{
    /**
     * 用户ID字段
     */
    public static final String OKX_TICKER = "okx_ticker";


    public static final String OKX_TICKER_MARKET = "okx_ticker_market";

    public static  final String getTicketKey() {
        return RedisConstants.OKX_TICKER_MARKET + DateUtil.getFormateDate(new Date(),DateUtil.YYYYMMDD);
    }


}

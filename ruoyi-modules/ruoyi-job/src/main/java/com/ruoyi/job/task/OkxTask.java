package com.ruoyi.job.task;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.api.RemoteOkxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Date;

/**
 * 定时任务调度测试
 * 
 */
@Component("okxTask")
public class OkxTask
{

    @Autowired
    private RemoteOkxService remoteOkxService;

    public void ryMultipleParams(String s, Boolean b, Long l, Double d, Integer i)
    {
        System.out.println(StringUtils.format("执行多参方法： 字符串类型{}，布尔类型{}，长整型{}，浮点型{}，整形{}", s, b, l, d, i));
    }

    public void ryParams(String params)
    {
        System.out.println("执行有参方法：" + params);
    }

    public void syncCoins(){
        System.out.println(DateUtil.formatForTime(new Date()) + " - syncCoins执行无参方法");
        remoteOkxService.syncCoin(SecurityConstants.INNER);
    }

    public void syncCoinsTurnOver(){
        System.out.println(DateUtil.formatForTime(new Date()) + " - syncCoinsTurnOver执行无参方法");
        remoteOkxService.syncCoinTurnOver(SecurityConstants.INNER);
    }

    public void syncCurrencies(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncCurrencies执行无参方法");
        remoteOkxService.syncCurrencies(SecurityConstants.INNER);
    }

    public void syncTicker(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncTicker执行无参方法");
        remoteOkxService.syncTicker(SecurityConstants.INNER);
    }

    public void syncTickerDb(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncTickerDb执行无参方法");
        remoteOkxService.syncTickerDb(SecurityConstants.INNER);
    }


    public void syncCount(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncCount执行无参方法");
        remoteOkxService.syncCount(SecurityConstants.INNER);
    }

    public void syncOrderBuy(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncOrderBuy-执行无参方法");
        remoteOkxService.syncBuyOrder(SecurityConstants.INNER);
    }

    public void syncOrderBuyFee(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncOrderBuyFee-执行无参方法");
        remoteOkxService.syncBuyOrderFee(SecurityConstants.INNER);
    }

    public void syncOrderSell(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - syncOrderSell-执行无参方法");
        remoteOkxService.syncSellOrder(SecurityConstants.INNER);
    }


    public void init(){
        System.out.println(DateUtil.formatForTime(new Date()) +  " - init-执行无参方法");
        remoteOkxService.init(SecurityConstants.INNER);
    }
}

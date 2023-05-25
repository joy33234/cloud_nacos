package com.ruoyi.job.task;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.system.api.RemoteOkxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

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

    public void syncCurrencies(){
        System.out.println("syncCurrencies执行无参方法");
        remoteOkxService.syncCurrencies(SecurityConstants.INNER);
    }

    public void syncTicker(){
        System.out.println("syncTicker执行无参方法");
        remoteOkxService.syncTicker(SecurityConstants.INNER);
    }


    public void syncCount(){
        System.out.println("syncCount执行无参方法");
        remoteOkxService.syncCount(SecurityConstants.INNER);
    }

    public void syncOrderBuy(){
        System.out.println("syncOrderBuy-执行无参方法");
        remoteOkxService.syncBuyOrder(SecurityConstants.INNER);
    }

    public void syncOrderBuyFee(){
        System.out.println("syncOrderBuyFee-执行无参方法");
        remoteOkxService.syncBuyOrderFee(SecurityConstants.INNER);
    }

    public void syncOrderSell(){
        System.out.println("syncOrderSell-执行无参方法");
        remoteOkxService.syncSellOrder(SecurityConstants.INNER);
    }
}

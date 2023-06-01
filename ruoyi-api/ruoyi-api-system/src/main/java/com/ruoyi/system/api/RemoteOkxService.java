package com.ruoyi.system.api;

import com.ruoyi.common.core.constant.SecurityConstants;
import com.ruoyi.common.core.constant.ServiceNameConstants;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.system.api.factory.RemoteUserFallbackFactory;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.*;

/**
 * 用户服务
 * 
 * @author ruoyi
 */
@FeignClient(contextId = "remoteOkxService", value = ServiceNameConstants.OKX_SERVICE, fallbackFactory = RemoteUserFallbackFactory.class)
public interface RemoteOkxService
{
    /**
     * 同步所有币种
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/currencies")
    public R<Void> syncCurrencies(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 同步所有币种
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/ticker")
    public R<Void> syncTicker(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);


    /**
     * 同步帐户币种数量
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/count")
    public R<Void> syncCount(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);


    /**
     * 同步买入订单
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/buy/order")
    public R<Void> syncBuyOrder(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);

    /**
     * 同步买入订单手续费
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/buy/order/fee")
    public R<Void> syncBuyOrderFee(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);


    /**
     * 同步卖出订单
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/sell/order")
    public R<Void> syncSellOrder(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);


    /**
     * 同步coin standard
     *
     * @param source 请求来源
     * @return 结果
     */
    @GetMapping("/sync/coin")
    public R<Void> syncCoin(@RequestHeader(SecurityConstants.FROM_SOURCE) String source);


}

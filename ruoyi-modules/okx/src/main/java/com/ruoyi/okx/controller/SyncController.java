package com.ruoyi.okx.controller;

import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.okx.business.SyncBusiness;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;


/**
 * 参数配置 信息操作处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/sync")
public class SyncController extends BaseController
{
    @Autowired
    private SyncBusiness syncBusiness;

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/coin")
    public AjaxResult syncCoin(){
        syncBusiness.syncCoin();
        return success();
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/buy/order")
    public AjaxResult syncBuy(){
        syncBusiness.syncBuyOrder();
        return success();
    }

    @GetMapping(value = "/buy/order/fee")
    public AjaxResult syncBuyFee(){
        syncBusiness.syncBuyOrderFee();
        return success();
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/sell/order")
    public AjaxResult syncSell(){
        syncBusiness.syncSellOrder();
        return success();
    }



    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/ticker")
    public AjaxResult syncTickercker(){
        syncBusiness.syncTicker();
        return success();
    }
    

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/count")
    public AjaxResult syncCount(){
        syncBusiness.syncCoinBalance();
        return success();
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/currencies")
    public AjaxResult syncCurrencies(){
        syncBusiness.syncCurrencies();
        return success();
    }

}

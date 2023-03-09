package com.ruoyi.okx.controller;

import com.ruoyi.common.core.utils.poi.ExcelUtil;
import com.ruoyi.common.core.web.controller.BaseController;
import com.ruoyi.common.core.web.domain.AjaxResult;
import com.ruoyi.common.core.web.page.TableDataInfo;
import com.ruoyi.common.log.annotation.Log;
import com.ruoyi.common.log.enums.BusinessType;
import com.ruoyi.common.security.annotation.RequiresPermissions;
import com.ruoyi.okx.business.CoinBusiness;
import com.ruoyi.okx.business.SyncBusiness;
import com.ruoyi.okx.business.TickerBusiness;
import com.ruoyi.okx.domain.OkxCoin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletResponse;
import java.util.List;

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

    @Autowired
    private TickerBusiness tickerBusiness;

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/buy/order")
    public AjaxResult syncBuy()
    {
        return success(syncBusiness.syncBuyOrder());
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/sell/order")
    public AjaxResult syncSell()
    {
        return success(syncBusiness.syncSellOrder());
    }



    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/ticker")
    public AjaxResult syncTicker()
    {
        return success(tickerBusiness.syncTicker());
    }
    

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/count")
    public AjaxResult syncCount()
    {
        return success(syncBusiness.syncCoinBalance());
    }

    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/currencies")
    public AjaxResult syncCurrencies()
    {
        return success(syncBusiness.syncCurrencies());
    }


    /**
     * 根据参数获取详细信息
     */
    @GetMapping(value = "/unit")
    public AjaxResult syncMin()
    {
        return success(syncBusiness.syncUnit());
    }

}

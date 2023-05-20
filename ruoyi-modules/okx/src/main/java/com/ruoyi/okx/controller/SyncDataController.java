//package com.ruoyi.okx.controller;
//
//import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
//import com.ruoyi.common.core.utils.DateUtil;
//import com.ruoyi.common.core.web.controller.BaseController;
//import com.ruoyi.common.core.web.domain.AjaxResult;
//import com.ruoyi.okx.business.*;
//import com.ruoyi.okx.domain.OkxCoinProfit;
//import com.ruoyi.okx.domain.OkxCoinTicker;
//import com.ruoyi.okx.params.DO.BuyRecordDO;
//import com.ruoyi.okx.params.DO.SellRecordDO;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
//import javax.annotation.Resource;
//import java.util.Date;
//
//
///**
// * 参数配置 信息操作处理
// *
// * @author ruoyi
// */
//@RestController
//@RequestMapping("/sync/data")
//public class SyncDataController extends BaseController
//{
//    @Autowired
//    private BuyRecordBusiness buyRecordBusiness;
//
//    @Resource
//    private SellRecordBusiness sellRecordBusiness;
//
//    @Resource
//    private CoinBusiness coinBusiness;
//
//    @Resource
//    private AccountBalanceBusiness balanceBusiness;
//
//    @Resource
//    private AccountBusiness accountBusiness;
//
//    @Resource
//    private TickerBusiness tickerBusiness;
//
//    @Resource
//    private ProfitBusiness profitBusiness;
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/buy/order")
//    public AjaxResult syncBuy(){
//        Date now = new Date();
//        BuyRecordDO buyRecordDO = new BuyRecordDO();
//        buyRecordDO.getParams().put("beginTime", DateUtil.getMinTime(now));
//        buyRecordDO.getParams().put("endTime", DateUtil.getMaxTime(now));
//        return success(buyRecordBusiness.selectList(buyRecordDO));
//    }
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/sell/order")
//    public AjaxResult syncSell(){
//        Date now = new Date();
//        SellRecordDO sellRecordDO = new SellRecordDO();
//        sellRecordDO.getParams().put("beginTime", DateUtil.getMinTime(now));
//        sellRecordDO.getParams().put("endTime", DateUtil.getMaxTime(now));
//        return success(sellRecordBusiness.selectList(sellRecordDO));
//    }
//
//
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/ticker")
//    public AjaxResult syncTicker(){
//        Date now =  new Date();
//        LambdaQueryWrapper<OkxCoinTicker> wrapper1 = new LambdaQueryWrapper();
//        wrapper1.ge(OkxCoinTicker::getCreateTime, DateUtil.getMinTime(now));
//        wrapper1.le(OkxCoinTicker::getCreateTime, DateUtil.getMaxTime(now));
//        return success( tickerBusiness.list(wrapper1));
//    }
//
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/account")
//    public AjaxResult syncAccount(){
//        return success(accountBusiness.list());
//    }
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/balance")
//    public AjaxResult syncBalance(){
//        return success(balanceBusiness.list());
//    }
//
//
//
//    /**
//     * 同步币种
//     */
//    @GetMapping(value = "/coin")
//    public AjaxResult syncCoin(){
//        return success(coinBusiness.selectCoinList(null));
//    }
//
//
//    /**
//     * 同步币种
//     */
//    @GetMapping(value = "/profit")
//    public AjaxResult syncProfit(){
//        Date now =  new Date();
//        LambdaQueryWrapper<OkxCoinProfit> wrapper = new LambdaQueryWrapper();
//        wrapper.ge(OkxCoinProfit::getCreateTime, DateUtil.getMinTime(now));
//        wrapper.le(OkxCoinProfit::getCreateTime, DateUtil.getMaxTime(now));
//        return success(profitBusiness.list(wrapper));
//    }
//
//
//}

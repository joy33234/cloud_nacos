//package com.ruoyi.okx.controller;
//
//import com.ruoyi.common.core.web.controller.BaseController;
//import com.ruoyi.common.core.web.domain.AjaxResult;
//import com.ruoyi.okx.business.TickerBusiness;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.web.bind.annotation.GetMapping;
//import org.springframework.web.bind.annotation.RequestMapping;
//import org.springframework.web.bind.annotation.RestController;
//
///**
// * 参数配置 信息操作处理
// *
// * @author ruoyi
// */
//@RestController
//@RequestMapping("/ticker")
//public class TickerController extends BaseController
//{
//    @Autowired
//    private TickerBusiness tickerBusiness;
//
//    /**
//     * 根据参数获取详细信息
//     */
//    @GetMapping(value = "/sync")
//    public AjaxResult syncBuy()
//    {
//        return success(tickerBusiness.syncTicker());
//    }
//
//
//
//}

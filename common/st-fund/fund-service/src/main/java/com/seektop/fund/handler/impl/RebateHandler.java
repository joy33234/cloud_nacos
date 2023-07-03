package com.seektop.fund.handler.impl;//package com.seektop.fund.handler.impl;
//
//import com.seektop.common.rest.rpc.RPCResponseUtils;
//import com.seektop.constant.HandlerResponseCode;
//import com.seektop.constant.ProjectConstant;
//import com.seektop.dto.GlUserDO;
//import com.seektop.enumerate.push.Channel;
//import com.seektop.exception.GlobalException;
//import com.seektop.fund.business.GlFundUserAccountBusiness;
//import com.seektop.fund.handler.FundReportHandler;
//import com.seektop.report.fund.ActivityRebateReport;
//import com.seektop.report.fund.HandlerResponse;
//import com.seektop.system.dto.result.SystemNoticeTemplateDO;
//import com.seektop.system.service.SystemNoticeTemplateService;
//import com.seektop.user.service.GlUserService;
//import lombok.extern.slf4j.Slf4j;
//import org.apache.dubbo.config.annotation.Reference;
//import org.springframework.stereotype.Component;
//
//import javax.annotation.Resource;
//import java.math.RoundingMode;
//import java.text.SimpleDateFormat;
//import java.util.Date;
//
///**
// * 返水的handler
// */
////@Component("fundReportHandler" + 3002)
//@Slf4j
//public class RebateHandler implements FundReportHandler<ActivityRebateReport> {
//
////    @Resource
////    private GlFundUserAccountBusiness glFundUserAccountBusiness;
////    @Reference(retries = 2, timeout = 3000)
////    private SystemNoticeTemplateService systemNoticeTemplateService;
////    @Reference(retries = 2, timeout = 3000)
////    private GlUserService glUserService;
////
////    @Override
////    public HandlerResponse handleFund(ActivityRebateReport report) throws GlobalException {
////        try{
////
////            GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(report.getUserId().intValue()));
////            if (userDO == null) {
////                // 当正常验证处理
////                log.error("查询用户失败  userId = {}", report.getUserId());
////                return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), "rpcUserDO == null");
////            }
////
////            HandlerResponse handlerResponse = glFundUserAccountBusiness.doRebate(report, userDO);
////            handlerResponse.getExtraInfo().put("gameName", report.getGameName());
////            sendRebateNotice(report, userDO);
////            return handlerResponse;
////        }catch (Exception e){
////            return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), e.getMessage());
////        }
////    }
////
////    private void sendRebateNotice(ActivityRebateReport report, GlUserDO userDO) {
////        // 发送消息
////        try{
////            SystemNoticeTemplateDO snt = systemNoticeTemplateService.findById(ProjectConstant.SystemNoticeTempleteId.REBATE);
////            if (snt == null) {
////                log.error("返水通知模版在数据库中未配置");
////                return;
////            }
////            Date date = new Date();
////            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
////            String startTime = sdf.format(date);
////            String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
////            String notice = tNotice.replaceAll("\\[amount\\]", report.getAmount().setScale(2, RoundingMode.DOWN).toString());
////            notice = notice.replaceAll("\\[gameName\\]", report.getGameName()); //增加游戏名称，模板增加gameName关键字
////            glFundUserAccountBusiness.pushNotice(report, userDO, Channel.Rebate.value(), ProjectConstant.SystemNoticeTempleteId.REBATE, notice);
////        }catch (Exception e){
////            log.error(e.getMessage(), e);
////            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", report.getTransactionId());
////        }
////    }
//}
////package com.seektop.fund.handler.impl;
////
////import com.seektop.common.rest.rpc.RPCResponse;
////import com.seektop.common.rest.rpc.RPCResponseUtils;
////import com.seektop.constant.HandlerResponseCode;
////import com.seektop.constant.ProjectConstant;
////import com.seektop.dto.GlUserDO;
////import com.seektop.enumerate.push.Channel;
////import com.seektop.exception.GlobalException;
////import com.seektop.fund.business.GlFundUserAccountBusiness;
////import com.seektop.fund.handler.FundReportHandler;
////import com.seektop.report.fund.ActivityRebateReport;
////import com.seektop.report.fund.HandlerResponse;
////import com.seektop.system.dto.result.SystemNoticeTemplateDO;
////import com.seektop.system.service.SystemNoticeTemplateService;
////import com.seektop.user.service.GlUserService;
////import lombok.extern.slf4j.Slf4j;
////import org.apache.dubbo.config.annotation.Reference;
////import org.springframework.stereotype.Component;
////
////import javax.annotation.Resource;
////import java.math.RoundingMode;
////import java.text.SimpleDateFormat;
////import java.util.Date;
////
/////**
//// * 返水的handler
//// */
////@Component("fundReportHandler" + 3002)
////@Slf4j
////public class RebateHandler implements FundReportHandler<ActivityRebateReport> {
////
////    @Resource
////    private GlFundUserAccountBusiness glFundUserAccountBusiness;
////    @Reference(timeout = 6000, retries = 3)
////    private SystemNoticeTemplateService systemNoticeTemplateService;
////    @Reference(timeout = 6000, retries = 3)
////    private GlUserService glUserService;
////
////    @Override
////    public HandlerResponse handleFund(ActivityRebateReport report) throws GlobalException {
////        try{
////            RPCResponse<GlUserDO> rpcUserDO = glUserService.findById(report.getUserId().intValue());
////
////            GlUserDO userDO = null;
////            if (RPCResponseUtils.isFail(rpcUserDO)) {
////                // 当正常验证处理
////                log.error("查询用户失败  userId = {},message = {}", report.getUserId(), rpcUserDO.getMessage());
////                return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), "rpcUserDO == null");
////            }else {
////                userDO = rpcUserDO.getData();
////            }
////
////            HandlerResponse handlerResponse = glFundUserAccountBusiness.doRebate(report, userDO);
////            handlerResponse.getExtraInfo().put("gameName", report.getGameName());
////            sendRebateNotice(report, userDO);
////            return handlerResponse;
////        }catch (Exception e){
////            return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), e.getMessage());
////        }
////    }
////
////    private void sendRebateNotice(ActivityRebateReport report, GlUserDO userDO) {
////        // 发送消息
////        try{
////            SystemNoticeTemplateDO snt = systemNoticeTemplateService.findById(ProjectConstant.SystemNoticeTempleteId.REBATE);
////            if (snt == null) {
////                log.error("返水通知模版在数据库中未配置");
////                return;
////            }
////            Date date = new Date();
////            SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
////            String startTime = sdf.format(date);
////            String tNotice = snt.getTemeContent().replaceAll("\\[time\\]", startTime);
////            String notice = tNotice.replaceAll("\\[amount\\]", report.getAmount().setScale(2, RoundingMode.DOWN).toString());
////            notice = notice.replaceAll("\\[gameName\\]", report.getGameName()); //增加游戏名称，模板增加gameName关键字
////            glFundUserAccountBusiness.pushNotice(report, userDO, Channel.Rebate.value(), ProjectConstant.SystemNoticeTempleteId.REBATE, notice);
////        }catch (Exception e){
////            log.error(e.getMessage(), e);
////            log.error("&&&&&&== orderId = {}  推送失败，但订单仍可继续进行。", report.getTransactionId());
////        }
////    }
////}

package com.seektop.fund.controller.backend;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.fund.controller.backend.param.monitor.*;
import com.seektop.fund.handler.RechargePayerMonitorHandler;
import com.seektop.fund.model.RechargePayerMonitorUsernameWhiteList;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/monitor/recharge/payer")
public class RechargePayerMonitorController extends FundBackendBaseController {

    @Resource
    private RechargePayerMonitorHandler rechargePayerMonitorHandler;

    @PostMapping(value = "/record/count")
    public Result monitorRecordCount(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, ManageParamBaseDO paramBaseDO) {
        return rechargePayerMonitorHandler.monitorRecordCount(adminDO,paramBaseDO);
    }

    @PostMapping(value = "/record/list")
    public Result monitorRecordList(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorRecordListParamDO paramDO) {
        return rechargePayerMonitorHandler.monitorRecordList(adminDO, paramDO);
    }

    @PostMapping(value = "/submit/config/times")
    public Result submitTimes(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorTimesParamDO paramDO) {
        Result result = rechargePayerMonitorHandler.submitTimes(adminDO, paramDO);
        if (result.getCode() == ResultCode.SUCCESS.getCode()) {
            rechargePayerMonitorHandler.monitorFromTimesConfig(paramDO.getTimes());
        }
        return result;
    }

    @PostMapping(value = "/get/config/times")
    public Result getTimes(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, ManageParamBaseDO paramBaseDO) {
        return rechargePayerMonitorHandler.getTimes(adminDO, paramBaseDO);
    }

    @PostMapping(value = "/submit/add/whitelist/name")
    public Result submitAddWhiteList4Name(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorNameWhiteListSubmitParamDO paramDO) {
        Result result = rechargePayerMonitorHandler.submitAddWhiteList4Name(adminDO, paramDO);
        if (result.getCode() == ResultCode.SUCCESS.getCode()) {
            JSONObject resultDataObj = (JSONObject) result.getData();
            rechargePayerMonitorHandler.monitorFromPayerName((resultDataObj.getJSONArray("successNameList")));
        }
        return result;
    }

    @PostMapping(value = "/submit/delete/whitelist/name")
    public Result submitDeleteWhiteList4Name(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, Integer recordId, ManageParamBaseDO paramBaseDO) {
        Result result = rechargePayerMonitorHandler.submitDeleteWhiteList4Name(adminDO, recordId, paramBaseDO);
        result.setData(null);
        return result;
    }

    @PostMapping(value = "/record/whitelist/name")
    public Result whiteList4Name(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorNameWhiteListParamDO paramDO) {
        return rechargePayerMonitorHandler.whiteList4Name(adminDO, paramDO);
    }

    @PostMapping(value = "/submit/add/whitelist/username")
    public Result submitAddWhiteList4Username(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorUsernameWhiteListSubmitParamDO paramDO) {
        return rechargePayerMonitorHandler.submitAddWhiteList4Username(adminDO, paramDO);
    }

    @PostMapping(value = "/submit/delete/whitelist/username")
    public Result submitDeleteWhiteList4Username(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, Integer userId, ManageParamBaseDO paramBaseD) {
        Result result = rechargePayerMonitorHandler.submitDeleteWhiteList4Username(adminDO, userId, paramBaseD);
        if (result.getCode() == ResultCode.SUCCESS.getCode()) {
            RechargePayerMonitorUsernameWhiteList usernameWhiteList = (RechargePayerMonitorUsernameWhiteList)result.getData();
            // 异步处理：删除用户账号白名单以后立即检查一次当前用户是否需要进监控列表
            rechargePayerMonitorHandler.monitorFromUserId(usernameWhiteList.getUserId(), null);
        }
        result.setData(null);
        return result;
    }

    @PostMapping(value = "/record/whitelist/username")
    public Result whiteList4Username(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargePayerMonitorUsernameWhiteListParamDO paramDO) {
        return rechargePayerMonitorHandler.whiteList4Username(adminDO, paramDO);
    }

}
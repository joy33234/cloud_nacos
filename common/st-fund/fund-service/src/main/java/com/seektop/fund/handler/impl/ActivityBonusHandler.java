package com.seektop.fund.handler.impl;

import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.handler.FundReportHandler;
import com.seektop.report.fund.ActivityBonusReport;
import com.seektop.report.fund.HandlerResponse;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 活动奖励加钱
 */
@Component("fundReportHandler" + 3001)
@Slf4j
public class ActivityBonusHandler implements FundReportHandler<ActivityBonusReport> {

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService glUserService;


    @Override
    public HandlerResponse handleFund(ActivityBonusReport report) throws GlobalException {
        try {
            GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(report.getUserId().intValue()));
            if (userDO == null) {
                // 当正常验证处理
                log.error("查询用户失败  userId = {}", report.getUserId());
                return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), "rpcUserDO == null");
            }

            HandlerResponse handlerResponse = glFundUserAccountBusiness.doActivityAward(report, userDO);
            return handlerResponse;
        } catch (Exception e) {
            return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), e.getMessage());
        }
    }
}
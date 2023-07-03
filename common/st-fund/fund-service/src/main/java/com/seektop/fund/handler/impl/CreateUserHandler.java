package com.seektop.fund.handler.impl;

import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.handler.FundReportHandler;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.report.fund.HandlerResponse;
import com.seektop.report.fund.UserAccountCreateReport;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 注册账户时自动创建财务账户
 */
@Slf4j
@Component("fundReportHandler" + 3000)
public class CreateUserHandler implements FundReportHandler<UserAccountCreateReport> {

    @Autowired
    private GlFundUserAccountBusiness glFundUserAccountBusiness;
    @Autowired
    private GlFundUserLevelLockBusiness glFundUserLevelLockBusiness;
    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;

    @Override
    @Transactional(rollbackFor = GlobalException.class)
    public HandlerResponse handleFund(UserAccountCreateReport report) throws GlobalException {
        try {
            GlUserDO user = RPCResponseUtils.getData(glUserService.findById(report.getUserId()));
            if(user == null){
                return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), "user == null");
            }
            // 检查是否存在用户绑定的关系
            GlFundUserLevelLock levelLock = glFundUserLevelLockBusiness.findById(report.getUserId());
            if (levelLock != null) {
                return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.FAIL.getCode(), "levelLock != null");
            }
            //币种
            DigitalCoinEnum coinEnum = DigitalCoinEnum.getDigitalCoin(report.getCoinCode());
            // 检查是否存在用户账户信息
            DigitalUserAccount userAccount = glFundUserAccountBusiness.getUserAccount(report.getUserId(),coinEnum);
            if (userAccount != null) {
                throw new GlobalException("userAccount != null");
            }

            glFundUserAccountBusiness.createFundAccount(user, coinEnum, report.getUsername());
            return HandlerResponse.generateByFundBaseReport(report, HandlerResponseCode.SUCCESS.getCode(), null);
        } catch (Exception ex) {
            throw new GlobalException(ex);
        }
    }

}
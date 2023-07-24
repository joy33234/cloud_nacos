package com.seektop.fund.handler;

import com.google.common.collect.Lists;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.business.withdraw.GlWithdrawBankBusiness;
import com.seektop.fund.controller.backend.param.bankcard.WithdrawBankEditParamDO;
import com.seektop.fund.controller.backend.param.bankcard.WithdrawBankListParamDO;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawBankListForeheadParamDO;
import com.seektop.fund.controller.forehead.result.WithdrawBankListResult;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.GlWithdrawBank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class WithdrawBankHandler {

    private final GlWithdrawBankBusiness glWithdrawBankBusiness;

    public Result withdrawBankListByCoin(GlUserDO userDO, WithdrawBankListForeheadParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            List<WithdrawBankListResult> resultList = Lists.newArrayList();
            List<GlWithdrawBank> withdrawBankList = glWithdrawBankBusiness.findByCoin(paramDO.getCoin());
            if (CollectionUtils.isEmpty(withdrawBankList)) {
                return newBuilder.success().addData(resultList).build();
            }
            for (GlWithdrawBank withdrawBank : withdrawBankList) {
                WithdrawBankListResult resultDO = new WithdrawBankListResult();
                resultDO.setCoin(withdrawBank.getCoin());
                resultDO.setBankId(withdrawBank.getBankId());
                resultDO.setBankName(withdrawBank.getBankName());
                resultDO.setBankLogo(withdrawBank.getBankLogo());
                resultList.add(resultDO);
            }
            return newBuilder.success().addData(resultList).build();
        } catch (Exception ex) {
            log.error("加载提现银行列表时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public Result withdrawBankList(GlAdminDO adminDO, WithdrawBankListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(glWithdrawBankBusiness.findList(adminDO, paramDO)).build();
        } catch (Exception ex) {
            log.error("加载提现银行列表时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public Result submitWithdrawBankEdit(GlAdminDO adminDO, WithdrawBankEditParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            GlWithdrawBank withdrawBank = glWithdrawBankBusiness.findById(paramDO.getBankId());
            if (ObjectUtils.isEmpty(withdrawBank)) {
                return newBuilder.fail().setLocalConfigKey(FundLanguageMvcEnum.BANKCARD_WITHDRAW_ID_NOT_EXIST).build();
            }
            GlWithdrawBank updateWithdrawBank = new GlWithdrawBank();
            updateWithdrawBank.setBankId(paramDO.getBankId());
            updateWithdrawBank.setBankName(paramDO.getBankName());
            updateWithdrawBank.setBankLogo(paramDO.getBankLogo());
            updateWithdrawBank.setCoin(paramDO.getCoin());
            glWithdrawBankBusiness.updateByPrimaryKeySelective(updateWithdrawBank);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("编辑提现银行时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

}
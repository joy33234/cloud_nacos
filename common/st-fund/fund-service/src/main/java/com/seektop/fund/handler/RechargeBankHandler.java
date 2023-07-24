package com.seektop.fund.handler;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.business.recharge.GlRechargeBankBusiness;
import com.seektop.fund.controller.backend.param.bankcard.RechargeBankEditParamDO;
import com.seektop.fund.controller.backend.param.bankcard.RechargeBankListParamDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.model.GlRechargeBank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class RechargeBankHandler {

    private final GlRechargeBankBusiness glRechargeBankBusiness;

    public Result rechargeBankList(GlAdminDO adminDO, RechargeBankListParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            return newBuilder.success().addData(glRechargeBankBusiness.findList(adminDO, paramDO)).build();
        } catch (Exception ex) {
            log.error("加载充值银行列表时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

    public Result submitRechargeBankEdit(GlAdminDO adminDO, RechargeBankEditParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            GlRechargeBank rechargeBank = glRechargeBankBusiness.findById(paramDO.getBankId());
            if (ObjectUtils.isEmpty(rechargeBank)) {
                return newBuilder.fail().setLocalConfigKey(FundLanguageMvcEnum.BANKCARD_RECHARGE_ID_NOT_EXIST).build();
            }
            GlRechargeBank updateRechargeBank = new GlRechargeBank();
            updateRechargeBank.setBankId(paramDO.getBankId());
            updateRechargeBank.setBankName(paramDO.getBankName());
            updateRechargeBank.setBankLogo(paramDO.getBankLogo());
            updateRechargeBank.setCoin(paramDO.getCoin());
            glRechargeBankBusiness.updateByPrimaryKeySelective(updateRechargeBank);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("编辑充值银行时发生异常", ex);
            return newBuilder.fail().build();
        }
    }

}
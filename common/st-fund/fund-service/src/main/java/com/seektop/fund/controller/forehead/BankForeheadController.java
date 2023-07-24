package com.seektop.fund.controller.forehead;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlUserDO;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawBankListForeheadParamDO;
import com.seektop.fund.handler.WithdrawBankHandler;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@Slf4j
@RestController
@RequestMapping(value = "/forehead/fund/bank")
public class BankForeheadController extends FundForeheadBaseController {

    @Resource
    private WithdrawBankHandler withdrawBankHandler;

    @PostMapping(value = "/withdraw/support/list")
    public Result withdrawBank(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, @Validated WithdrawBankListForeheadParamDO paramDO) {
        return withdrawBankHandler.withdrawBankListByCoin(userDO, paramDO);
    }

}
package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.bankcard.WithdrawBankEditParamDO;
import com.seektop.fund.controller.backend.param.bankcard.WithdrawBankListParamDO;
import com.seektop.fund.handler.WithdrawBankHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/withdraw/bank")
public class WithdrawBankManageController extends FundBackendBaseController {

    @Resource
    private WithdrawBankHandler withdrawBankHandler;

    @PostMapping(value = "/list")
    public Result list(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated WithdrawBankListParamDO paramDO) {
        return withdrawBankHandler.withdrawBankList(adminDO, paramDO);
    }

    @PostMapping(value = "/submit/edit")
    public Result submitWithdrawBankEdit(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated WithdrawBankEditParamDO paramDO) {
        return withdrawBankHandler.submitWithdrawBankEdit(adminDO, paramDO);
    }

}
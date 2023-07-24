package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.bankcard.RechargeBankEditParamDO;
import com.seektop.fund.controller.backend.param.bankcard.RechargeBankListParamDO;
import com.seektop.fund.handler.RechargeBankHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/recharge/bank")
public class RechargeBankManageController extends FundBackendBaseController {

    @Resource
    private RechargeBankHandler rechargeBankHandler;

    @PostMapping(value = "/list")
    public Result list(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargeBankListParamDO paramDO) {
        return rechargeBankHandler.rechargeBankList(adminDO, paramDO);
    }

    @PostMapping(value = "/submit/edit")
    public Result submitRechargeBankEdit(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargeBankEditParamDO paramDO) {
        return rechargeBankHandler.submitRechargeBankEdit(adminDO, paramDO);
    }

}
package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.c2c.C2CRechargeConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CSeoRechargeConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CSeoWithdrawConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CWithdrawConfigParamDO;
import com.seektop.fund.handler.C2CConfigHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/manage/fund/c2c/config")
public class C2CConfigManagerController extends FundBackendBaseController {

    @Resource
    private C2CConfigHandler c2CConfigHandler;

    /**
     * 保存配置：提现相关
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/submit/withdraw")
    public Result submitWithdrawConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CWithdrawConfigParamDO paramDO) {
        return c2CConfigHandler.submitWithdrawConfig(adminDO, paramDO);
    }

    /**
     * 保存配置：充值相关
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/submit/recharge")
    public Result submitRechargeConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CRechargeConfigParamDO paramDO) {
        return c2CConfigHandler.submitRechargeConfig(adminDO, paramDO);
    }

    /**
     * 保存配置：seo提现相关
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/seo/submit/withdraw")
    public Result seoSubmitWithdrawConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CSeoWithdrawConfigParamDO paramDO) {
        return c2CConfigHandler.seoSubmitWithdrawConfig(adminDO, paramDO);
    }

    /**
     * 保存配置：seo充值相关
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/seo/submit/recharge")
    public Result seoSubmitRechargeConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated C2CSeoRechargeConfigParamDO paramDO) {
        return c2CConfigHandler.seoSubmitRechargeConfig(adminDO, paramDO);
    }

    /**
     * 保存配置：可选金额
     *
     * @param adminDO
     * @param amounts
     * @return
     */
    @PostMapping(value = "/submit/amount")
    public Result submitChooseAmount(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, String amounts) {
        return c2CConfigHandler.submitChooseAmount(adminDO, amounts);
    }

    /**
     * 保存配置：开关
     *
     * @param adminDO
     * @param isOpen
     * @return
     */
    @PostMapping(value = "/submit/switch")
    public Result submitSwitch(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, Integer isOpen) {
        return c2CConfigHandler.submitSwitch(adminDO, isOpen);
    }

    /**
     * 获取配置
     *
     * @param adminDO
     * @return
     */
    @PostMapping(value = "/get")
    public Result getBackendConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO) {
        return c2CConfigHandler.getBackendConfig(adminDO);
    }

}
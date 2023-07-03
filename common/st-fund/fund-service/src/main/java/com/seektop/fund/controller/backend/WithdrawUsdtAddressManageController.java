package com.seektop.fund.controller.backend;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.controller.backend.param.withdraw.USDTWithdrawConfigSubmitParamDO;
import com.seektop.fund.controller.backend.param.withdraw.USDTWithdrawDelDO;
import com.seektop.fund.handler.USDTConfigHandler;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;

@RestController
@RequestMapping(value = "/manage/fund/usdt", produces = "application/json;charset=utf-8")
public class WithdrawUsdtAddressManageController extends FundBackendBaseController {

    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;
    @Resource
    private USDTConfigHandler usdtConfigHandler;

    /**
     * 获取数字货币提现配置
     *
     * @param type
     * @return
     */
    @PostMapping(value = "/load/config")
    public Result loadConfig(@NotNull Short type) {
        return usdtConfigHandler.loadWithdrawConfig(type);
    }

    /**
     * 保存数字货币提现配置
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    @PostMapping(value = "/submit/config")
    public Result submitConfig(@ModelAttribute(name = "adminInfo", binding = false) GlAdminDO adminDO, @Validated USDTWithdrawConfigSubmitParamDO paramDO) throws GlobalException {
        return usdtConfigHandler.submitWithdrawConfig(adminDO, paramDO);
    }

    /**
     * 查询用户已绑定USDT地址
     *
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/list")
    public Result list(@NotNull Integer userId) throws GlobalException {
        return Result.genSuccessResult(glWithdrawUserUsdtAddressBusiness.findByUserId(userId, null));
    }

    /**
     * 删除用户usdt 钱包地址
     */
    @PostMapping(value = "/del")
    public Result del(@Validated USDTWithdrawDelDO usdtWithdrawDelDO,
                      @ModelAttribute(name = "adminInfo", binding = false) GlAdminDO glAdminDO) {
        return glWithdrawUserUsdtAddressBusiness.backendDel(usdtWithdrawDelDO, glAdminDO);

    }
}
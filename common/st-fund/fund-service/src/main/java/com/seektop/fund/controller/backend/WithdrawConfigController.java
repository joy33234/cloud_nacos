package com.seektop.fund.controller.backend;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.enumerate.WithdrawConfigEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawCommonConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawGeneralConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawProxyConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawQuickConfig;
import com.seektop.fund.controller.backend.dto.withdraw.RejectWithdrawRequestDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.CommonConfigDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.GeneralConfigDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.ProxyConfigDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.QuickConfigDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@RestController
@RequestMapping("/manage/fund/withdraw/config")
public class WithdrawConfigController extends FundBackendBaseController {

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawConfigBusiness configBusiness;

    /**
     * 保存普通提现设置
     *
     * @param config
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/general/save")
    public Result saveGeneralConfig(@Validated GeneralConfigDO config) {
        try {
            configBusiness.generalSave(config);
            return Result.genSuccessResult();
        } catch (Exception ex) {
            log.error("保存普通提现配置时发生异常", ex);
            return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_NORMAL_CONFIG_SAVE_ERROR).parse(config.getLanguage()));
        }
    }

    /**
     * 查询普通提现设置
     *
     * @return
     */
    @PostMapping("/general/find")
    public Result generalConfig(ParamBaseDO paramBaseDO,@RequestParam String coin) {
        try {
            GlWithdrawGeneralConfig generalConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG_COIN + coin, WithdrawConfigEnum.GENERAL.getMessage(), GlWithdrawGeneralConfig.class);
            if (ObjectUtils.isEmpty(generalConfig)) {
                return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_NORMAL_CONFIG_EMPTY).parse(paramBaseDO.getLanguage()));
            }
            return Result.genSuccessResult(generalConfig);
        } catch (Exception ex) {
            log.error("查询普通提现配置时发生异常", ex);
            return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_NORMAL_CONFIG_GET_ERROR).parse(paramBaseDO.getLanguage()));
        }
    }

    /**
     * 保存大额提现设置
     *
     * @param config
     * @return
     */
    @PostMapping(value = "/quick/save", produces = "application/json;charset=utf-8")
    public Result saveQuickConfig(@Validated QuickConfigDO config) {
        try {
            configBusiness.quickSave(config);
            return Result.genSuccessResult();
        } catch (Exception ex) {
            log.error("保存大额提现配置时发生异常", ex);
            return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_LARGE_CONFIG_SAVE_ERROR).parse(config.getLanguage()));
        }
    }

    /**
     * 获取大额提现
     *
     * @return
     */
    @PostMapping("/quick/find")
    public Result findQuickConfig(ParamBaseDO paramBaseDO ,@RequestParam String coin) {
        try {
            GlWithdrawQuickConfig quickConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_QUICK_CONFIG_COIN + coin, WithdrawConfigEnum.QUICK.getMessage(), GlWithdrawQuickConfig.class);
            if (ObjectUtils.isEmpty(quickConfig)) {
                return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_LARGE_CONFIG_EMPTY).parse(paramBaseDO.getLanguage()));
            }
            return Result.genSuccessResult(quickConfig);
        } catch (Exception ex) {
            log.error("查询大额提现配置时发生异常", ex);
            return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_LARGE_CONFIG_GET_ERROR).parse(paramBaseDO.getLanguage()));
        }
    }

    /**
     * 保存代理提现设置
     *
     * @param config
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/proxy/save")
    public Result saveProxyConfig(@Validated ProxyConfigDO config) {
        try {
            configBusiness.proxySave(config);
            return Result.genSuccessResult();
        } catch (Exception ex) {
            log.error("保存代理提现配置时发生异常", ex);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_PROXY_CONFIG_SAVE_ERROR).parse(config.getLanguage()));

        }
    }

    /**
     * 获取代理提现设置
     *
     * @return
     */
    @PostMapping("/proxy/find")
    public Result findProxyConfig(ParamBaseDO paramBaseDO) {
        try {
            GlWithdrawProxyConfig proxyConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_PROXY_CONFIG, WithdrawConfigEnum.PROXY.getMessage(), GlWithdrawProxyConfig.class);
            if (ObjectUtils.isEmpty(proxyConfig)) {
                return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_PROXY_CONFIG_EMPTY).parse(paramBaseDO.getLanguage()));

            }
            return Result.genSuccessResult(proxyConfig);
        } catch (Exception ex) {
            log.error("查询代理提现配置时发生异常", ex);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_PROXY_CONFIG_GET_ERROR).parse(paramBaseDO.getLanguage()));
        }
    }

    /**
     * 保存提现通用设置
     *
     * @param config
     * @return
     */
    @PostMapping(value = "/common/save")
    public Result saveCommonConfig(@Validated CommonConfigDO config) {
        try {
            configBusiness.commonSave(config);
            return Result.genSuccessResult();
        } catch (Exception ex) {
            log.error("保存通用提现配置时发生异常", ex);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_COMMON_CONFIG_SAVE_ERROR).parse(config.getLanguage()));
        }
    }

    /**
     * 获取提现通用设置
     *
     * @return
     */
    @PostMapping("/common/find")
    public Result findCommonConfig(ParamBaseDO paramBaseDO, @RequestParam(value = "coin" ) String coin) {
        try {
            return Result.genSuccessResult(configBusiness.find(RedisKeyHelper.WITHDRAW_COMMON_CONFIG + coin, WithdrawConfigEnum.COMMON.getMessage(), GlWithdrawCommonConfig.class));
        } catch (Exception ex) {
            log.error("查询通用提现配置时发生异常", ex);
            return Result.genFailResult(LanguageLocalParser.key(FundLanguageMvcEnum.WITHDRAW_COMMON_CONFIG_GET_ERROR).parse(paramBaseDO.getLanguage()));

        }
    }

    /**
     * 拒绝提现理由保存
     */
    @PostMapping(value = "/reject/reason/save")
    public Result saveReason(@RequestParam(value = "reasons") List<String> reasons) {
        redisService.set(RedisKeyHelper.WITHDRAW_REASON_REJECTION, reasons, -1);
        return Result.genSuccessResult();
    }

    /**
     * 拒绝提现理由查询
     */
    @GetMapping(value = "/reject/reason/list")
    public Result getReasons() {
        return Result.genSuccessResult(configBusiness.getRejectReasons());
    }

    /**
     * 保存平台提现开关数据或查询提现内容
     */
    @PostMapping(value = "/reject/withdraw/save")
    public Result rejectWithdraw(RejectWithdrawRequestDO requestDO) {
        return Result.genSuccessResult(configBusiness.rejectWithdraw(requestDO));
    }

}

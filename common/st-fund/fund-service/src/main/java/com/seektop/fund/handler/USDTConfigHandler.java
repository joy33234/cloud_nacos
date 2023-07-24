package com.seektop.fund.handler;

import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.WithdrawConfigEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawGeneralConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawProxyConfig;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawQuickConfig;
import com.seektop.fund.controller.backend.dto.withdraw.config.UsdtConfig;
import com.seektop.fund.controller.backend.param.withdraw.USDTWithdrawConfigSubmitParamDO;
import com.seektop.fund.controller.backend.result.USDTConfigResult;
import lombok.extern.slf4j.Slf4j;
import org.assertj.core.util.Sets;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class USDTConfigHandler {

    private final List<Short> typeAllowArray = Arrays.asList(
            (short) 0,      // 会员端
            (short) 1       // 代理端
    );

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawBusiness withdrawBusiness;

    @Resource
    private GlWithdrawConfigBusiness configBusiness;

    /**
     * 获取提现配置
     *
     * @param type
     * @return
     */
    public Result loadWithdrawConfig(Short type) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(type) || typeAllowArray.contains(type) == false) {
            return newBuilder.paramError().build();
        }
        if (type == 0) {
            return newBuilder.success().addData(loadUserWithdrawConfig()).build();
        } else {
            return newBuilder.success().addData(loadProxyWithdrawConfig()).build();
        }
    }

    /**
     * 保存提现配置
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitWithdrawConfig(GlAdminDO adminDO, USDTWithdrawConfigSubmitParamDO paramDO) throws GlobalException {
        Result.Builder newBuilder = Result.newBuilder();
        Short type = paramDO.getType();
        if (typeAllowArray.contains(type) == false) {
            return newBuilder.paramError().build();
        }
        List<String> protocols = Arrays.asList(FundConstant.ProtocolType.ERC20, FundConstant.ProtocolType.TRC20, FundConstant.ProtocolType.OMNI);
        if (!paramDO.getProtocols().isEmpty()) {
            for (String protocol: paramDO.getProtocols()) {
                if (!protocols.contains(protocol)) {
                    return newBuilder.paramError().build();
                }
            }
        }

        BigDecimal withdrawRate = withdrawBusiness.getWithdrawRate((int) type);
        if (type == 0) {
            setUserWithdrawConfig(adminDO, paramDO.getStatus(), withdrawRate ,paramDO.getProtocols());
        } else {
            setProxyWithdrawConfig(adminDO, paramDO.getStatus(), withdrawRate,paramDO.getProtocols());
        }
        return newBuilder.success().build();
    }

    protected USDTConfigResult loadUserWithdrawConfig() {
        USDTConfigResult result = new USDTConfigResult();
        UsdtConfig config = redisService.getHashObject(RedisKeyHelper.WITHDRAW_USDT_CONFIG, WithdrawConfigEnum.USDT.getMessage(), UsdtConfig.class);
        if (ObjectUtils.isEmpty(config)) {
            log.error("获取redisUSDT提现配置异常");
            result.setStatus(1);
            result.setProtocols(Sets.newLinkedHashSet(FundConstant.ProtocolType.ERC20));
        } else {
            result.setStatus(config.getStatus());
            result.setProtocols(config.getProtocols());
        }
        result.setRate(withdrawBusiness.getWithdrawRate(UserConstant.UserType.PLAYER));
        return result;
    }

    protected void setUserWithdrawConfig(GlAdminDO adminDO, final Integer status, final BigDecimal rate , Set<String> protocals) throws GlobalException {
        UsdtConfig configDO = new UsdtConfig();
        configDO.setStatus(status);
        configDO.setProtocols(protocals);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_USDT_CONFIG, WithdrawConfigEnum.USDT.getMessage(), configDO);
        //普通提现设置
        GlWithdrawGeneralConfig generalConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG, WithdrawConfigEnum.GENERAL.getMessage(), GlWithdrawGeneralConfig.class);
        if (ObjectUtils.isEmpty(generalConfig)) {
            generalConfig = new GlWithdrawGeneralConfig();
        }
        generalConfig.setProtocols(protocals);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG, WithdrawConfigEnum.GENERAL.getMessage(), generalConfig);
        //大额提现设置
        GlWithdrawQuickConfig quickConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_QUICK_CONFIG, WithdrawConfigEnum.QUICK.getMessage(), GlWithdrawQuickConfig.class);
        if (ObjectUtils.isEmpty(quickConfig)) {
            quickConfig = new GlWithdrawQuickConfig();
        }
        quickConfig.setProtocols(protocals);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_QUICK_CONFIG, WithdrawConfigEnum.QUICK.getMessage(), quickConfig);
    }

    protected USDTConfigResult loadProxyWithdrawConfig() {
        USDTConfigResult result = new USDTConfigResult();
        UsdtConfig config = redisService.getHashObject(RedisKeyHelper.WITHDRAW_USDT_CONFIG_PROXY, WithdrawConfigEnum.USDT.getMessage(), UsdtConfig.class);
        if (ObjectUtils.isEmpty(config)) {
            log.error("获取redisUSDT提现配置异常");
            result.setStatus(1);
            result.setProtocols(Sets.newLinkedHashSet(FundConstant.ProtocolType.ERC20));
        } else {
            result.setStatus(config.getStatus());
            result.setProtocols(config.getProtocols());
        }
        result.setRate(withdrawBusiness.getWithdrawRate(UserConstant.UserType.PROXY));
        return result;
    }

    protected void setProxyWithdrawConfig(GlAdminDO adminDO, final Integer status, final BigDecimal rate, Set<String> protocals) throws GlobalException {
        UsdtConfig configDO = new UsdtConfig();
        configDO.setStatus(status);
        configDO.setProtocols(protocals);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_USDT_CONFIG_PROXY, WithdrawConfigEnum.USDT.getMessage(), configDO);

        // 代理提现设置
        GlWithdrawProxyConfig quickConfig = configBusiness.find(RedisKeyHelper.WITHDRAW_PROXY_CONFIG, WithdrawConfigEnum.PROXY.getMessage(), GlWithdrawProxyConfig.class);
        if (ObjectUtils.isEmpty(quickConfig)) {
            quickConfig = new GlWithdrawProxyConfig();
        }
        quickConfig.setProtocols(protocals);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_PROXY_CONFIG, WithdrawConfigEnum.PROXY.getMessage(), quickConfig);
    }

}
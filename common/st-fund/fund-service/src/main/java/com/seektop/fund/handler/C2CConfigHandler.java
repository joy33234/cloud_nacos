package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.common.redis.RedisLock;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.param.c2c.C2CRechargeConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CSeoRechargeConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CSeoWithdrawConfigParamDO;
import com.seektop.fund.controller.backend.param.c2c.C2CWithdrawConfigParamDO;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2CConfigHandler {

    protected final static String CONFIG_UPDATE_LOCK_KEY = "C2C_CONFIG_UPDATE_REDIS_LOCK_KEY";

    private final RedisLock redisLock;

    /**
     * 保存配置：提现相关参数
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitWithdrawConfig(GlAdminDO adminDO, C2CWithdrawConfigParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        log.debug("收到的提现配置参数是：{}", JSON.toJSONString(paramDO));
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            // 提现支持的VIP等级
            List<Integer> withdrawVipLevels = Arrays.asList(paramDO.getWithdrawVipLevels().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setWithdrawVipLevels(withdrawVipLevels);
            configDO.setWithdrawDailyUseLimit(paramDO.getWithdrawDailyUseLimit());
            configDO.setWithdrawHandlingFeeType(paramDO.getWithdrawHandlingFeeType());
            configDO.setWithdrawHandlingFeeValue(paramDO.getWithdrawHandlingFeeValue());
            configDO.setWithdrawHandlingFeeMax(paramDO.getWithdrawHandlingFeeMax());
            configDO.setWithdrawReceiveConfirmAlertTime(paramDO.getWithdrawReceiveConfirmAlertTime());
            configDO.setWithdrawReceiveConfirmAlertTimeout(paramDO.getWithdrawReceiveConfirmAlertTimeout());
            configDO.setMatchWaitTime(paramDO.getMatchWaitTime());
            configDO.setWithdrawForceSuccessTime(paramDO.getWithdrawForceSuccessTime());
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2C配置提现相关参数，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }

    /**
     * 保存配置：充值相关参数
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result submitRechargeConfig(GlAdminDO adminDO, C2CRechargeConfigParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        log.debug("收到的充值配置参数是：{}", JSON.toJSONString(paramDO));
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            // 充值支持的用户层级
            List<Integer> rechargeUserLevels = Arrays.asList(paramDO.getRechargeUserLevels().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            // 充值支持的VIP等级
            List<Integer> rechargeVipLevels = Arrays.asList(paramDO.getRechargeVipLevels().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setRechargeUserLevels(rechargeUserLevels);
            configDO.setRechargeVipLevels(rechargeVipLevels);
            configDO.setRechargeDailyUseLimit(paramDO.getRechargeDailyUseLimit());
            configDO.setRechargeDailyCancelLimit(paramDO.getRechargeDailyCancelLimit());
            configDO.setRechargeAlertTime(paramDO.getRechargeAlertTime());
            configDO.setRechargePaymentTimeout(paramDO.getRechargePaymentTimeout());
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2C配置充值相关参数，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }


    /**
     * 保存配置：seo提现相关参数
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result seoSubmitWithdrawConfig(GlAdminDO adminDO, C2CSeoWithdrawConfigParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        log.debug("收到的seo提现配置参数是：{}", JSON.toJSONString(paramDO));
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            // seo用户提现支持的VIP等级
            List<Integer> withdrawSeoVipLevels = Arrays.asList(paramDO.getWithdrawSeoVipLevels().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setWithdrawSeoVipLevels(withdrawSeoVipLevels);
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2Cseo配置提现相关参数，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }

    /**
     * 保存配置：seo充值相关参数
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public Result seoSubmitRechargeConfig(GlAdminDO adminDO, C2CSeoRechargeConfigParamDO paramDO) {
        Result.Builder newBuilder = Result.newBuilder();
        log.debug("收到的seo充值配置参数是：{}", JSON.toJSONString(paramDO));
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            // 充值支持的seo用户VIP等级
            List<Integer> rechargeSeoVipLevels = Arrays.asList(paramDO.getRechargeSeoVipLevels().split(",")).stream().map(s -> Integer.valueOf(s.trim())).collect(Collectors.toList());
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setRechargeSeoVipLevels(rechargeSeoVipLevels);
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2Cseo配置充值相关参数，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }

    /**
     * 保存配置：选择金额
     *
     * @param adminDO
     * @param amounts
     * @return
     */
    public Result submitChooseAmount(GlAdminDO adminDO, String amounts) {
        Result.Builder newBuilder = Result.newBuilder();
        if (StringUtils.isEmpty(amounts)) {
            return newBuilder.paramError().build();
        }
        log.debug("收到的配置金额参数是：{}", amounts);
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            List<Integer> chooseAmounts = Arrays.asList(amounts.split(",")).stream().map(s -> new Integer(s.trim())).collect(Collectors.toList());
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setChooseAmounts(chooseAmounts);
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2C配置选择金额，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }

    /**
     * 保存配置：开关
     *
     * @param adminDO
     * @param isOpen
     * @return
     */
    public Result submitSwitch(GlAdminDO adminDO, Integer isOpen) {
        Result.Builder newBuilder = Result.newBuilder();
        if (ObjectUtils.isEmpty(isOpen)) {
            return newBuilder.paramError().build();
        }
        try {
            redisLock.lock(CONFIG_UPDATE_LOCK_KEY, 20, 20, 200);
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                Result result = newBuilder.fail().setMessage("保存失败，配置不存在").build();
                result.setKeyConfig(FundLanguageMvcEnum.SETTING_NOT_EXIST);
                return result;
            }
            configDO.setIsOpen(isOpen == 1 ? true : false);
            RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("保存C2C配置开关，时发生错误", ex);
            Result result = newBuilder.fail().setMessage("保存配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_SAVE_ERROR);
            return result;
        } finally {
            redisLock.releaseLock(CONFIG_UPDATE_LOCK_KEY);
        }
    }

    /**
     * 获取后台配置
     *
     * @param adminDO
     * @return
     */
    public Result getBackendConfig(GlAdminDO adminDO) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
            if (ObjectUtils.isEmpty(configDO)) {
                configDO = getDefaultC2CConfig();
                RedisTools.valueOperations().set(KeyConstant.C2C.C2C_CONFIG, configDO);
            }
            return newBuilder.success().addData(configDO).build();
        } catch (Exception ex) {
            log.error("获取配置时发生错误", ex);
            Result result = newBuilder.fail().setMessage("获取配置时发生错误").build();
            result.setKeyConfig(FundLanguageMvcEnum.SETTING_GET_ERROR);
            return result;
        }
    }

    protected C2CConfigDO getDefaultC2CConfig() {
        C2CConfigDO configDO = new C2CConfigDO();
        configDO.setIsOpen(false);
        configDO.setMatchWaitTime(1);
        configDO.setChooseAmounts(Arrays.asList(
                Integer.valueOf(100), Integer.valueOf(200), Integer.valueOf(300), Integer.valueOf(500),
                Integer.valueOf(800), Integer.valueOf(1000), Integer.valueOf(2000), Integer.valueOf(5000)
        ));
        configDO.setRechargeDailyUseLimit(10);
        configDO.setRechargeDailyCancelLimit(5);
        configDO.setRechargeAlertTime(2);
        configDO.setRechargePaymentTimeout(5);
        configDO.setWithdrawDailyUseLimit(10);
        configDO.setWithdrawHandlingFeeType(1);
        configDO.setWithdrawHandlingFeeValue(BigDecimal.ZERO);
        configDO.setWithdrawHandlingFeeMax(BigDecimal.ZERO);
        configDO.setWithdrawReceiveConfirmAlertTime(2);
        configDO.setWithdrawReceiveConfirmAlertTimeout(5);
        return configDO;
    }

}
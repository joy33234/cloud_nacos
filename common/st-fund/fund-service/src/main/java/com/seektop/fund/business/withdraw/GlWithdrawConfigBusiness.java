package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.enumerate.WithdrawConfigEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.config.dto.*;
import com.seektop.fund.controller.backend.dto.withdraw.RejectWithdrawRequestDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Slf4j
@Component
public class GlWithdrawConfigBusiness {

    @Resource
    private RedisService redisService;

    /**
     * 获取提现配置
     *
     * @param key
     * @param filed
     * @param t
     * @param <T>
     * @return
     * @throws GlobalException
     */
    public <T> T find(String key, String filed, Class<T> t) throws GlobalException {
        try {
            Object config = redisService.getHashObject(key, filed, t);
            if (ObjectUtils.isEmpty(config)) {
                return null;
            }
            T target = t.newInstance();
            BeanUtils.copyProperties(config, target);
            return target;
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            throw new GlobalException(e);
        }
    }

    public GlWithdrawQuickConfig getWithdrawQuickConfig(String coin) throws GlobalException {
        return find(RedisKeyHelper.WITHDRAW_QUICK_CONFIG_COIN + coin, WithdrawConfigEnum.QUICK.getMessage(), GlWithdrawQuickConfig.class);
    }

    public GlWithdrawGeneralConfig getWithdrawGeneralConfig(String coin) throws GlobalException {
        return find(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG_COIN + coin, WithdrawConfigEnum.GENERAL.getMessage(), GlWithdrawGeneralConfig.class);
    }

    public GlWithdrawCommonConfig getWithdrawCommonConfig(String coin) throws GlobalException {
        return find(RedisKeyHelper.WITHDRAW_COMMON_CONFIG_COIN + coin, WithdrawConfigEnum.COMMON.getMessage(), GlWithdrawCommonConfig.class);
    }

    public GlWithdrawProxyConfig getWithdrawProxyConfig(String coin) throws GlobalException {
        return find(RedisKeyHelper.WITHDRAW_PROXY_CONFIG_COIN + coin, WithdrawConfigEnum.PROXY.getMessage(), GlWithdrawProxyConfig.class);
    }

    public GlWithdrawConfig getWithdrawConfig() throws GlobalException {
        return find(RedisKeyHelper.WITHDRAW_CONFIG, WithdrawConfigEnum.CONFIG.getMessage(), GlWithdrawConfig.class);
    }


    public void commonSave(CommonConfigDO configDO) {
        GlWithdrawCommonConfig commonConfig = DtoUtils.transformBean(configDO, GlWithdrawCommonConfig.class);
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_COMMON_CONFIG_COIN + commonConfig.getCoin(), WithdrawConfigEnum.COMMON.getMessage(), commonConfig);
    }

    /**
     * 保存普通提现设置
     *
     * @param config
     * @throws GlobalException
     */
    public void generalSave(GeneralConfigDO config) throws GlobalException {
        GlWithdrawCommonConfig commonConfig = redisService.getHashObject(RedisKeyHelper.WITHDRAW_COMMON_CONFIG_COIN + config.getCoin(), WithdrawConfigEnum.COMMON.getMessage(), GlWithdrawCommonConfig.class);
        log.info("commonConfig:{}", JSON.toJSONString(commonConfig));
        if (commonConfig == null) {
            throw new GlobalException("请先配置通用提现设置");
        }

        if (config.getFreeTimes() > config.getCountLimit()) {
            throw new GlobalException("“每天提现次数上限”必须大于或等于“每天免费提现次数”");
        }

        if (config.getMinLimit().compareTo(config.getMaxLimit()) >= 0) {
            throw new GlobalException("“单笔最低限额”不能等于或大于“单笔最高限额”");
        }

        if (config.getMinUSDTLimit().compareTo(config.getMaxUSDTLimit()) >= 0) {
            throw new GlobalException("“数字货币单笔最低限额”不能等于或大于“数字货币单笔最高限额”");
        }

        if (config.getFeeType().equals("fix")) {
            if (config.getMinLimit().compareTo(config.getFee()) != 1) {
                throw new GlobalException("“单笔最低限额”不能低于“固定手续金额”");
            }
            if (config.getFee().compareTo(config.getFeeLimit()) == 1) {
                throw new GlobalException("“固定手续金额”不能超过“手续费上限”");
            }
        }
        if (config.getMaxLimit().compareTo(commonConfig.getAmountLimit()) == 1) {
            throw new GlobalException("“单笔最高限额”不能超出“每日金额上限”");
        }
        if (!CollectionUtils.isEmpty(config.getLimitList())) {
            for (GeneralConfigLimitDO obj: config.getLimitList()) {
                if (obj.getMinAmount().compareTo(obj.getMaxAmount()) > 0) {
                    throw new GlobalException("最低限额不能高于最高限额");
                }
            }
        }

        GlWithdrawGeneralConfig originalConfig = find(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG_COIN + config.getCoin(), WithdrawConfigEnum.GENERAL.getMessage(), GlWithdrawGeneralConfig.class);
        checkAmount(config.getUsdtLimits(), originalConfig.getProtocols());

        GlWithdrawGeneralConfig generalConfig = DtoUtils.transformBean(config, GlWithdrawGeneralConfig.class);
        generalConfig.setProtocols(originalConfig.getProtocols());
        //保存普通提现设置
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_GENERAL_CONFIG_COIN + generalConfig.getCoin(), WithdrawConfigEnum.GENERAL.getMessage(), generalConfig);
    }

    /**
     * 保存大额提现设置
     *
     * @param config
     * @throws GlobalException
     */
    public void quickSave(QuickConfigDO config) throws GlobalException {
        GlWithdrawCommonConfig commonConfig = redisService.getHashObject(RedisKeyHelper.WITHDRAW_COMMON_CONFIG_COIN + config.getCoin(), WithdrawConfigEnum.COMMON.getMessage(), GlWithdrawCommonConfig.class);
        if (commonConfig == null) {
            throw new GlobalException("请先配置通用提现设置");
        }

        if (config.getMinLimit().compareTo(config.getMaxLimit()) >= 0) {
            throw new GlobalException("“单笔最低限额”必须小于“单笔最高限额”");
        }
        if (config.getMinUSDTLimit().compareTo(config.getMaxUSDTLimit()) >= 0) {
            throw new GlobalException("“数字货币单笔最低限额”必须小于“数字货币单笔最高限额”");
        }
        //体育流水赠送设置
        BigDecimal sportMaxAmount = null;
        int sportFreeTimes = 0;
        for (WithdrawEffectBetRuleDO sportRule : config.getSportRuleList()) {
            if (sportMaxAmount == null && sportFreeTimes == 0) {
                sportMaxAmount = sportRule.getBetAmount();
                sportFreeTimes = sportRule.getFreeTimes();
            } else {
                if (sportMaxAmount.compareTo(sportRule.getBetAmount()) != -1) {
                    throw new GlobalException("体育流水赠送设置:周累计有效投注金额请按升序排列");
                }
                if (sportFreeTimes >= sportRule.getFreeTimes()) {
                    throw new GlobalException("体育流水赠送设置:赠送免费提现次数请按升序排列");
                }
                sportMaxAmount = sportRule.getBetAmount();
                sportFreeTimes = sportRule.getFreeTimes();
            }
        }

        //娱乐流水赠送设置
        BigDecimal funMaxAmount = null;
        int funFreeTimes = 0;
        for (WithdrawEffectBetRuleDO funGameRule : config.getFunGameRuleList()) {
            if (funMaxAmount == null && funFreeTimes == 0) {
                funMaxAmount = funGameRule.getBetAmount();
                funFreeTimes = funGameRule.getFreeTimes();
            } else {
                if (funMaxAmount.compareTo(funGameRule.getBetAmount()) != -1) {
                    throw new GlobalException("娱乐流水赠送设置:周累计有效投注金额请按升序排列");
                }
                if (funFreeTimes >= funGameRule.getFreeTimes()) {
                    throw new GlobalException("娱乐流水赠送设置:赠送免费提现次数请按升序排列");
                }
                funMaxAmount = funGameRule.getBetAmount();
                funFreeTimes = funGameRule.getFreeTimes();
            }
        }

        //提现拆单设置
        BigDecimal splitMinAmount = null;
        BigDecimal splitMaxAmount = null;
        for (WithdrawSplitRuleDO item : config.getSplitRuleList()) {

            if (item.getRandomAmount().compareTo(BigDecimal.ZERO) != 1) {
                throw new GlobalException("“随机值”不能小于等于0");
            }
            //单条数据,提现金额区间最小值必须小于最大值
            if (item.getMinAmount().compareTo(item.getMaxAmount()) >= 0) {
                throw new GlobalException("“拆单金额区间“数据异常");
            }
            if (item.getMinAmount().compareTo(config.getMinLimit()) < 0 ||
                    item.getMaxAmount().compareTo(config.getMaxLimit()) > 0) {
                throw new GlobalException("“提现金额区间“不能超过“单笔提现限额设置“");
            }
            if (item.getSplitAmount().compareTo(item.getMaxAmount()) > 0 ) {
                throw new GlobalException("“拆单金额“设置不可超出“提现金额区间”范围");
            }
            if (item.getSplitAmount().compareTo(config.getMinLimit()) == -1 ||
                    item.getSplitAmount().compareTo(config.getMaxLimit()) == 1) {
                throw new GlobalException("“拆单金额”设置不可超出“单笔提现限额设置”范围");
            }

            if (splitMinAmount == null || splitMaxAmount == null) {
                splitMinAmount = item.getMinAmount();
                splitMaxAmount = item.getMaxAmount();
            } else {
                if (splitMaxAmount.compareTo(item.getMinAmount()) >= 0) {
                    throw new GlobalException("“提现拆单设置 - 提现金额区间请按升序排列");
                }
                splitMaxAmount = item.getMaxAmount();
            }
        }
        if (splitMaxAmount.compareTo(commonConfig.getAmountLimit()) == 1 ||
                splitMaxAmount.compareTo(config.getMaxLimit()) == 1) {
            throw new GlobalException("提现金额拆分最大区间,不可超出每日提现金额上限");
        }

        GlWithdrawQuickConfig originalConfig = find(RedisKeyHelper.WITHDRAW_QUICK_CONFIG_COIN + config.getCoin(), WithdrawConfigEnum.QUICK.getMessage(), GlWithdrawQuickConfig.class);
        checkAmount(config.getUsdtLimits(), originalConfig.getProtocols());

        GlWithdrawQuickConfig quickConfig = DtoUtils.transformBean(config, GlWithdrawQuickConfig.class);
        quickConfig.setProtocols(originalConfig.getProtocols());
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_QUICK_CONFIG_COIN + quickConfig.getCoin(), WithdrawConfigEnum.QUICK.getMessage(), quickConfig);
    }


    /**
     * 保存代理提现设置
     *
     * @param config
     * @throws GlobalException
     */
    public void proxySave(ProxyConfigDO config) throws GlobalException {
        if (config.getMinLimit().compareTo(config.getMaxLimit()) >= 0) {
            throw new GlobalException("“单笔最低金额”不能大于“单笔最高金额”");
        }
        if (config.getMaxLimit().compareTo(config.getAmountLimit()) >= 0) {
            throw new GlobalException("“单笔最高金额”不能大于“每日提现金额上限”");
        }
        if (config.getMinUSDTLimit().compareTo(config.getMaxUSDTLimit()) >= 0) {
            throw new GlobalException("“数字货币单笔最低金额”不能大于“数字货币单笔最高金额”");
        }
        if (config.getMaxUSDTLimit().compareTo(config.getAmountLimit()) >= 0) {
            throw new GlobalException("“单笔数字货币最高金额”不能大于“每日提现金额上限”");
        }

        BigDecimal splitMinAmount = null;
        BigDecimal splitMaxAmount = null;
        for (WithdrawSplitRuleDO item : config.getSplitRuleList()) {
            if (item.getRandomAmount().compareTo(BigDecimal.ZERO) != 1) {
                throw new GlobalException("“随机值”不能小于等于0");
            }
            if (item.getSplitAmount().compareTo(item.getMaxAmount()) == 1) {
                throw new GlobalException("“拆单金额”不可超出“提现金额区间”最大值");
            }
            if (item.getSplitAmount().compareTo(config.getMinLimit()) == -1 ||
                    item.getSplitAmount().compareTo(config.getMaxLimit()) == 1) {
                throw new GlobalException("“拆单金额”必须在“单笔提现限额设置”范围之内");
            }
            if (splitMinAmount == null || splitMaxAmount == null) {
                splitMinAmount = item.getMinAmount();
                splitMaxAmount = item.getMaxAmount();
            } else {
                if (splitMaxAmount.compareTo(item.getMinAmount()) >= 0) {
                    throw new GlobalException("提现拆单金额区间请按升序排列");
                }
                splitMaxAmount = item.getMaxAmount();
            }
        }
        if (splitMaxAmount.compareTo(config.getAmountLimit()) == 1 || splitMaxAmount.compareTo(config.getMaxLimit()) == 1) {
            throw new GlobalException("提现金额最大区间设置不可超出“每日提现金额上限”或“单笔最高限额”");
        }
        GlWithdrawProxyConfig originalConfig = find(RedisKeyHelper.WITHDRAW_PROXY_CONFIG_COIN + config.getCoin(), WithdrawConfigEnum.PROXY.getMessage(), GlWithdrawProxyConfig.class);
        checkAmount(config.getUsdtLimits(), originalConfig.getProtocols());
        GlWithdrawProxyConfig proxyConfig = DtoUtils.transformBean(config, GlWithdrawProxyConfig.class);
        proxyConfig.setProtocols(originalConfig.getProtocols());
        redisService.putHashValue(RedisKeyHelper.WITHDRAW_PROXY_CONFIG_COIN + proxyConfig.getCoin(), WithdrawConfigEnum.PROXY.getMessage(), proxyConfig);
    }

    /**
     * 校验金额与协议
     * @param set
     * @param protocols
     * @throws GlobalException
     */
    private void checkAmount(List<GlWithdrawUSDTLimit> set, Set<String> protocols) throws GlobalException {
        if (!CollectionUtils.isEmpty(set)) {
            for (GlWithdrawUSDTLimit obj: set) {
                if (obj.getMinAmount().compareTo(obj.getMaxAmount()) > 0) {
                    throw new GlobalException("最低限额不能高于最高限额");
                }
                if (CollectionUtils.isEmpty(protocols) || !protocols.contains(obj.getProtocol())) {
                    throw new GlobalException(obj.getProtocol() + "不支持提现，请先配置数字货币提现设置");
                }
            }
        }
    }


    /**
     * 拒绝提现快捷理由设置
     *
     * @return
     */
    public List<String> getRejectReasons() {
        List<String> succList = null;
        RedisResult<String> resultList = redisService.getListResult(RedisKeyHelper.WITHDRAW_REASON_REJECTION, String.class);
        if (resultList == null || resultList.getListResult() == null || resultList.getListResult().isEmpty()) {
            succList = new ArrayList<>();
        } else {
            succList = resultList.getListResult();
        }
        return succList;
    }

    /**
     * 平台提现开关设置
     *
     * @param rejectWithdrawRequest
     * @return
     */
    public RejectWithdrawRequestDO rejectWithdraw(RejectWithdrawRequestDO rejectWithdrawRequest) {
        if (rejectWithdrawRequest != null) {
            if ("0".equals(rejectWithdrawRequest.getType()) && redisService.exists(RedisKeyHelper.WITHDRAW_REJECT_TIPS)) {
                redisService.delete(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
            } else if ("1".equals(rejectWithdrawRequest.getType())) {
                redisService.set(RedisKeyHelper.WITHDRAW_REJECT_TIPS, JSONObject.toJSONString(rejectWithdrawRequest), -1);
            }
        }
        String json = redisService.get(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
        if (StringUtils.isEmpty(json)) {
            return new RejectWithdrawRequestDO();
        } else {
            return JSONObject.toJavaObject(JSONObject.parseObject(json), RejectWithdrawRequestDO.class);
        }
    }

}
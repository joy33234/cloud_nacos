package com.seektop.fund.handler.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.activity.service.RechargeWithdrawTempleService;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.nacos.GameConfig;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.GlRequestUtil;
import com.seektop.common.utils.MD5;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.param.betting.FindBettingCommParamDO;
import com.seektop.data.service.BettingService;
import com.seektop.data.service.SeoTeamService;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.WithdrawConfigEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.WithdrawStatusEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.*;
import com.seektop.fund.business.withdraw.config.dto.*;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawCardResult;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO;
import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawUsdtResult;
import com.seektop.fund.controller.backend.dto.withdraw.RejectWithdrawRequestDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.GeneralConfigLimitDO;
import com.seektop.fund.controller.backend.dto.withdraw.config.UsdtConfig;
import com.seektop.fund.controller.backend.result.withdraw.WithdrawBankSettingResult;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawConfirmDto;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawSubmitDO;
import com.seektop.fund.controller.forehead.result.GlWithdrawDetailResult;
import com.seektop.fund.controller.forehead.result.GlWithdrawInfoResult;
import com.seektop.fund.controller.forehead.result.WithdrawCalcFreeTimesResult;
import com.seektop.fund.controller.forehead.result.WithdrawResult;
import com.seektop.fund.dto.param.proxy.FundProxyAccountDO;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.handler.WithdrawHandler;
import com.seektop.fund.handler.validation.Validator;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlWithdrawHandlerManager;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawNotifyResponse;
import com.seektop.fund.service.FundProxyAccountService;
import com.seektop.report.fund.WithdrawReport;
import com.seektop.risk.dto.param.BlackCheckBanned;
import com.seektop.risk.dto.param.BlackMonitorDO;
import com.seektop.risk.service.BlackService;
import com.seektop.system.dto.MobileValidateDto;
import com.seektop.system.service.GlSystemApiService;
import com.seektop.user.dto.GlUserLockDo;
import com.seektop.user.service.GlUserSecurityService;
import com.seektop.user.service.GlUserService;
import com.seektop.user.service.GlUserWithdrawService;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.ObjectUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component("withdrawHandler")
public class WithdrawHandlerImpl implements WithdrawHandler {

    private static DecimalFormat decimalFormat = new DecimalFormat("0.00");

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserService userService;

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserSecurityService glUserSecurityService;

    @DubboReference(retries = 2, timeout = 3000)
    private GlSystemApiService systemApiService;

    @DubboReference(retries = 2, timeout = 3000)
    private UserManageService userManageService;

    @DubboReference(retries = 2, timeout = 3000)
    private BlackService blackService;

    @DubboReference(retries = 2, timeout = 3000)
    private BettingService bettingService;

    @DubboReference(retries = 2, timeout = 3000)
    private FundProxyAccountService fundProxyAccountService;

    @DubboReference(retries = 2, timeout = 3000)
    private GlUserWithdrawService glUserWithdrawService;

    @DubboReference(retries = 2, timeout = 3000)
    private RechargeWithdrawTempleService rechargeWithdrawTempleService;

    @DubboReference
    private SeoTeamService seoTeamService;

    @Resource
    private GlWithdrawHandlerManager glWithdrawHandlerManager;

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private GlWithdrawConfigBusiness withdrawConfigBusiness;

    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private GlWithdrawSplitBusiness glWithdrawSplitBusiness;

    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    @Resource
    private WithdrawApiRecordBusiness withdrawApiRecordBusiness;

    @Resource
    private WithdrawAlarmBusiness withdrawAlarmBusiness;

    @Resource
    private ReportService reportService;

    @Resource
    private GlWithdrawEffectBetBusiness glWithdrawEffectBetBusiness;

    @Resource
    private GlWithdrawTransactionalBusiness glWithdrawTransactionalBusiness;

    @Resource
    private GameConfig gameConfig;

    @Value("${check.user.bank.whitelist:}")
    private String whitelistCfg;

    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;

    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlWithdrawReceiveInfoBusiness glWithdrawReceiveInfoBusiness;

    @Resource
    private GlWithdrawConfigBusiness configBusiness;
    @Resource
    private UserVipUtils userVipUtils;
    @Resource
    private C2COrderHandler c2COrderHandler;


    private boolean checkWithdrawBankSetting(Integer bankId) {
        RedisResult<WithdrawBankSettingResult> redisResult = redisService.getListResult(RedisKeyHelper.WITHDRAW_BANK_SETTING_CACHE, WithdrawBankSettingResult.class);
        if (!redisResult.isExist()) {
            return true;
        }

        for (WithdrawBankSettingResult bank : redisResult.getListResult()) {
            if (bank.getBankId().equals(bankId) && bank.getStatus() == 0) {
                return false;
            }
        }
        return true;
    }



    @Override
    public Result loadWithdrawInfo(GlUserDO userDO, String coin) {
        // 用户手机号码检测
        try {
            RPCResponse<GlUserDO> rpcResponse = userService.findById(userDO.getId());
            if (RPCResponseUtils.isSuccess(rpcResponse)) {
                userDO = rpcResponse.getData();
            }
        } catch (Exception ex) {
            log.error("通过用户ID获取用户详情发生异常", ex);
        }
        // 提现功能是否开启
        if (redisService.exists(RedisKeyHelper.WITHDRAW_REJECT_TIPS)) {
            String json = redisService.get(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
            RejectWithdrawRequestDO rejectWithdrawRequest = JSONObject.toJavaObject(JSONObject.parseObject(json), RejectWithdrawRequestDO.class);
            return Result.genFailResult(ResultCode.WITHDRAWAL_CLOSED.getCode(), rejectWithdrawRequest.getContent());
        }
        try {
            return Result.genSuccessResult(withdrawInfo(userDO, coin));
        } catch (Exception ex) {
            log.error("获取提现信息错误", ex);
            return Result.genFailResult("获取提现信息错误");
        }
    }

    @Override
    public Result loadWithdrawInfoNew(GlUserDO userDO, String coin) {
        try {
            return Result.genSuccessResult(withdrawInfo(userDO, coin));
        } catch (GlobalException e) {
            log.error("获取提现信息错误", e);
            return Result.genFailResult(e.getExtraMessage());
        }
    }

    /**
     * 获取用户提现信息
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @Override
    public GlWithdrawInfoResult withdrawInfo(GlUserDO userDO, String coin) throws GlobalException {
        Date now = new Date();

        DigitalCoinEnum coinEnum = DigitalCoinEnum.getDigitalCoin(coin);
        if (coinEnum == null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "币种异常");
        }
        //当天最小值&最大值
        DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(userDO.getId(), coinEnum);
        if (account == null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "中心钱包账户不存在,请联系客服.");
        }

        GlWithdrawInfoResult result = new GlWithdrawInfoResult();

        // 提现功能是否开启
        if (redisService.exists(RedisKeyHelper.WITHDRAW_REJECT_TIPS)) {
            String json = redisService.get(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
            RejectWithdrawRequestDO rejectWithdrawRequest = JSONObject.toJavaObject(JSONObject.parseObject(json), RejectWithdrawRequestDO.class);
            result.setBankWithdrawOpen(false);
            result.setWithdrawCloseTitle(rejectWithdrawRequest.getTitle());
            result.setWithdrawCloseValue(rejectWithdrawRequest.getContent());
        }

        // 1. 用户已绑定手机号
        result.setMobile(StringUtils.isEmpty(userDO.getTelephone()) ? null : userDO.getTelArea() + StringEncryptor.encryptMobile(userDO.getTelephone()));
        // 2. 用户真实姓名
        result.setName(StringEncryptor.encryptUsername(userDO.getReallyName()));
        // 4. 用户提现流水信息
        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(), coinEnum.getCode());
        // 5. 会员绑定银行卡信息
        this.setCardList(result, userDO.getId());
        // 会员已绑定USDT列表
        this.setUsdtList(result, userDO.getId());

        // 6. 用户余额
        result.setBalance(account.getBalance().compareTo(BigDecimal.ZERO) != 1 ? BigDecimal.ZERO : account.getBalance());

        // 提现是否需要短信验证
        RPCResponse<Boolean> rpcResponse = glUserSecurityService.getWithdrawSecurity(userDO.getId());
        if (RPCResponseUtils.isFail(rpcResponse)) {
            throw new GlobalException(ResultCode.SERVER_ERROR, "服务异常,稍后再试");
        }
        result.setNeedSms(rpcResponse.getData());

        if (UserConstant.Type.PLAYER == userDO.getUserType()) {
            //会员提现信息
            this.setPlayerWithdrawInfo(result, userDO, account, effectBet, now, coinEnum);
            // 设置会员USDT提现的开关和汇率
            result.setUsdtWithdrawOpen(this.setWithdrawUsdtOpen(userDO.getId(), false, result));
            result.setUsdtWithdrawRate(glWithdrawBusiness.getWithdrawRate(UserConstant.Type.PLAYER));
            result.setC2CWithdrawOpen(this.setC2CWithdrawOpen(userDO, coin));
        } else {
            //代理提现配置
            GlWithdrawProxyConfig proxyConfig = withdrawConfigBusiness.getWithdrawProxyConfig(coinEnum.getCode());
            // 代理提现 -  今日已提现次数
            int withdrawProxyCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 3, coinEnum.getCode());
            proxyConfig.setWithdrawCount(withdrawProxyCount);
            result.setProxyConfig(proxyConfig);
            //今日提现次数
            result.setWithdrawTimes(withdrawProxyCount);
            // 代理可提现金额
            FundProxyAccountDO proxyAccount = RPCResponseUtils.getData(fundProxyAccountService.findById(userDO.getId()));
            result.setAgentValidAmount(ObjectUtils.isEmpty(proxyAccount) ? BigDecimal.ZERO : proxyAccount.getValidWithdrawal());
            // 当前用户今日已提现金额
            BigDecimal todayWithdrawAmount = glWithdrawBusiness.getWithdrawAmountTotal(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now));
            result.setWithdrawSumAmount(todayWithdrawAmount);
            // 设置代理USDT提现的开关和汇率
            result.setUsdtWithdrawOpen(this.setWithdrawUsdtOpen(userDO.getId(), true, result));
            result.setUsdtWithdrawRate(glWithdrawBusiness.getWithdrawRate(UserConstant.Type.PROXY));
        }
        log.info("withdrawLimit_user:{},generalConfig:{}",JSON.toJSONString(userDO), JSON.toJSONString(result.getGeneralConfig()));
        return result;
    }

    /**
     * 用户已绑定银行卡信息
     *
     * @param result
     * @param userId
     */
    private void setCardList(GlWithdrawInfoResult result, Integer userId) {
        List<GlWithdrawUserBankCard> cardList = glWithdrawUserBankCardBusiness.findUserActiveCardList(userId);
        boolean selected = false;
        List<GlWithdrawCardResult> cards = new ArrayList<>();
        if (cardList != null && !cardList.isEmpty()) {
            for (GlWithdrawUserBankCard userBankCard : cardList) {
                GlWithdrawCardResult glWithdrawCardResult = new GlWithdrawCardResult();
                glWithdrawCardResult.setCardId(userBankCard.getCardId());
                glWithdrawCardResult.setBankId(userBankCard.getBankId());
                glWithdrawCardResult.setBankName(userBankCard.getBankName());
                glWithdrawCardResult.setCardNo(StringEncryptor.encryptBankCard(userBankCard.getCardNo()));
                glWithdrawCardResult.setName(StringEncryptor.encryptUsername(userBankCard.getName()));
                glWithdrawCardResult.setSelected(userBankCard.getSelected());
                if (userBankCard.getSelected().equals(1)) {
                    selected = true;
                }
                cards.add(glWithdrawCardResult);
            }

            if (!selected) {
                for (GlWithdrawCardResult cardResult : cards) {
                    cardResult.setSelected(1);
                    break;
                }
            }
        }

        result.setCardList(cards);
    }

    /**
     * 用户已绑定银行卡信息
     *
     * @param result
     * @param userId
     */
    private void setUsdtList(GlWithdrawInfoResult result, Integer userId) {
        List<GlWithdrawUserUsdtAddress> usdtList = glWithdrawUserUsdtAddressBusiness.findByUserId(userId, 0);
        List<GlWithdrawUsdtResult> usdts = new ArrayList<>();
        if (usdtList != null && !usdtList.isEmpty()) {
            boolean selected = false;

            for (GlWithdrawUserUsdtAddress usdtAddress : usdtList) {

                GlWithdrawUsdtResult usdtResult = new GlWithdrawUsdtResult();
                usdtResult.setUsdtId(usdtAddress.getId());
                usdtResult.setNickName(usdtAddress.getNickName());
                usdtResult.setProtocol(usdtAddress.getProtocol());
                usdtResult.setAddress(usdtAddress.getAddress());
                usdtResult.setSelected(usdtAddress.getSelected());
                if (usdtAddress.getSelected().equals(1)) {
                    selected = true;
                }
                usdts.add(usdtResult);
            }

            if (!selected) {
                for (GlWithdrawUsdtResult usdtResult : usdts) {
                    usdtResult.setSelected(1);
                    break;
                }
            }
        }
        result.setUsdtList(usdts);
    }

    private boolean setWithdrawUsdtOpen(Integer userId, Boolean isProxy, GlWithdrawInfoResult result) {
        UsdtConfig config;
        if (isProxy) {
            config = redisService.getHashObject(RedisKeyHelper.WITHDRAW_USDT_CONFIG_PROXY, WithdrawConfigEnum.USDT.getMessage(), UsdtConfig.class);
        } else {
            config = redisService.getHashObject(RedisKeyHelper.WITHDRAW_USDT_CONFIG, WithdrawConfigEnum.USDT.getMessage(), UsdtConfig.class);
        }
        if (null == config || config.getStatus() == 1) {
            return false;
        }
        if (ObjectUtils.isNotEmpty(result)) {
            result.setProtocols(config.getProtocols());
        }
        if (config.getStatus() == 2) {
            String key = RedisKeyHelper.USER_USDT_RECHARGE_SUCCESS + userId;
            Boolean hasDigitalPay = redisService.get(key, Boolean.class);
            if (ObjectUtils.isEmpty(hasDigitalPay)) {
                hasDigitalPay = glRechargeBusiness.hasDigitalPaySuccess(userId);
                if (hasDigitalPay) {
                    redisService.set(key, hasDigitalPay);
                }
            }
            return hasDigitalPay;
        }
        return true;
    }

    public boolean setC2CWithdrawOpen(GlUserDO glUserDO, String coin) throws GlobalException {
        UserVIPCache vipCache = userVipUtils.getUserVIPCache(glUserDO.getId());
        Optional.ofNullable(vipCache).ifPresent(vip -> {
            glUserDO.setVipLevel(vipCache.getVipLevel());
        });

        C2CConfigDO config = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
         //后台配置开关
        Boolean result = config.getIsOpen();
        //过滤用户VIP等级
        RPCResponse<Boolean> rpcResponse = seoTeamService.seoUser(glUserDO.getId());
        if (rpcResponse == null) {
            throw new GlobalException("获取用户是否为seo用户异常");
        }
        if (CollectionUtils.isEmpty(config.getWithdrawVipLevels())
                || (CollectionUtils.isNotEmpty(config.getWithdrawVipLevels()) && rpcResponse.getData() == false && !config.getWithdrawVipLevels().contains(glUserDO.getVipLevel()))
                || (CollectionUtils.isNotEmpty(config.getWithdrawSeoVipLevels()) && rpcResponse.getData() == true && !config.getWithdrawSeoVipLevels().contains(glUserDO.getVipLevel()))) {
            result = false;
        }
        //过滤用户锁定极速提现用户
        RPCResponse<GlUserLockDo> userDetailResp = glUserWithdrawService.findByUserId(glUserDO.getId());
        if (RPCResponseUtils.isFail(userDetailResp)) {
            throw new GlobalException("获取用户提现锁定状态异常");
        }
        GlUserLockDo rechargeDo = RPCResponseUtils.getData(userDetailResp);
        if (rechargeDo != null && rechargeDo.getLockStatus() == 1) {
            result =  false;
        }
        // 已提现次数
        Date now = new Date();
        int cToCWithdrawCount = glWithdrawBusiness.getWithdrawCount(glUserDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, FundConstant.AisleType.C2C, coin);
        if (cToCWithdrawCount >= config.getWithdrawDailyUseLimit()) {
            result =  false;
        }
        return result;
    }

    /**
     * 会员提现基础信息
     *
     * @param result
     * @param userDO
     * @param account
     * @param effectBet
     * @param now
     * @throws GlobalException
     */
    private void setPlayerWithdrawInfo(GlWithdrawInfoResult result, GlUserDO userDO, DigitalUserAccount account,
                                       GlWithdrawEffectBet effectBet, Date now,DigitalCoinEnum coinEnum) throws GlobalException {
        // 通用设置
        GlWithdrawCommonConfig commonConfig = withdrawConfigBusiness.getWithdrawCommonConfig(coinEnum.getCode());
        if (null == commonConfig) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        // 普通提现配置
        GlWithdrawGeneralConfig generalConfig = withdrawConfigBusiness.getWithdrawGeneralConfig(coinEnum.getCode());
        if (null == generalConfig) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        // 大额提现配置
        GlWithdrawQuickConfig largeConfig = withdrawConfigBusiness.getWithdrawQuickConfig(coinEnum.getCode());
        if (null == largeConfig) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        // 极速提现配置
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (null == configDO) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        GlC2CWithdrawConfig c2CWithdrawConfig = DtoUtils.transformBean(configDO, GlC2CWithdrawConfig.class);
        c2CWithdrawConfig.setAmounts(configDO.getChooseAmounts());

        //提示说明开关
        result.setTipStatus("1".equals(commonConfig.getTipStatus()) ? "1" : "0");
        //提现流水倍数
        largeConfig.setMultiple(commonConfig.getMultiple());
        //每天提现金额上限
        largeConfig.setAmountLimit(commonConfig.getAmountLimit());

        generalConfig.setMultiple(commonConfig.getMultiple());
        generalConfig.setAmountLimit(commonConfig.getAmountLimit());

        c2CWithdrawConfig.setMultiple(commonConfig.getMultiple());

        // 普通提现:当日已提现次数
        int withdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 1,coinEnum.getCode());
        generalConfig.setWithdrawCount(withdrawCount);

        // 普通提现:已使用免费次数
        int generalFreeWithdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), 0, 1, coinEnum.getCode());

        // 普通提现:剩余免费提现次数
        int generalLeftFreeCount = generalConfig.getFreeTimes() - generalFreeWithdrawCount;
        generalConfig.setLeftFreeCount(generalLeftFreeCount > 0 ? generalLeftFreeCount : 0);

        // 普通提现:剩余提现次数
        int leftWithdrawCount = generalConfig.getCountLimit() - withdrawCount > 0 ? generalConfig.getCountLimit() - withdrawCount : 0;
        generalConfig.setLeftWithdrawCount(leftWithdrawCount);

        //用户层级限额
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        if (userlevel.getWithdrawOff() == 1) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "当前会员层级不可提现,请联系客服.");
        }
        List<GeneralConfigLimitDO> limitDOs = generalConfig.getLimitList().stream()
                .filter(obj -> obj.getLevelIds().contains(userlevel.getLevelId())).collect(Collectors.toList());
        Optional.ofNullable(limitDOs).ifPresent(obj -> {
            if (ObjectUtils.isNotEmpty(obj)) {
                generalConfig.setMinLimit(obj.get(0).getMinAmount());
                generalConfig.setMaxLimit(obj.get(0).getMaxAmount());
            }
        });

        result.setGeneralConfig(generalConfig);

        // 大额提现:当天已提现次数
        int withdrawDayQuickCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 2, coinEnum.getCode());
        largeConfig.setWithdrawCount(withdrawDayQuickCount);

        // 大额提现：当天剩余提现次数
        int largeLeftCount = largeConfig.getCountLimit() - withdrawDayQuickCount;
        largeConfig.setLeftWithdrawCount(largeLeftCount > 0 ? largeLeftCount : 0);

        // 大额提现:已使用免费次数
        int freeTimesUsed = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getCurrentMonday(now), DateUtils.getCurrentSunday(now), 0, 2, coinEnum.getCode());

        // 极速提现:当日已提现次数
        int ctocWithdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 4, coinEnum.getCode());
        c2CWithdrawConfig.setWithdrawCount(ctocWithdrawCount);
        //当天剩余提现次数
        int ctoCLeftCount = configDO.getWithdrawDailyUseLimit() - ctocWithdrawCount;
        c2CWithdrawConfig.setLeftWithdrawCount(ctoCLeftCount > 0 ? ctoCLeftCount : 0);
        result.setC2CWithdrawConfig(c2CWithdrawConfig);

        // 计算本周已使用免费提现次数、赠送免费大额提现次数
        WithdrawCalcFreeTimesResult freeTimesResult = this.calcWithdrawFreeTimes(userDO, largeConfig.getSportRuleList(), largeConfig.getFunGameRuleList(), now);

        result.setSportFreezeBalance(freeTimesResult.getSportValidBet());
        result.setFunFreezeBalance(freeTimesResult.getFunValidBet());
        largeConfig.setFreeTimes(freeTimesResult.getSportFreeTims() + freeTimesResult.getFunFreeTimes());
        largeConfig.setLeftFreeCount(largeConfig.getFreeTimes() - freeTimesUsed > 0 ? largeConfig.getFreeTimes() - freeTimesUsed : 0);

        result.setQuickConfig(largeConfig);

        //今日提现次数
        result.setWithdrawTimes(withdrawCount + withdrawDayQuickCount);

        FindBettingCommParamDO bettingCommParamDO = new FindBettingCommParamDO();
        bettingCommParamDO.setStartTime(effectBet.getEffectStartTime().getTime());
        bettingCommParamDO.setEndTime(now.getTime());
        bettingCommParamDO.setGamePlatformIds(new ArrayList<>());
        bettingCommParamDO.setUserId(userDO.getId());
        bettingCommParamDO.setCoinCode(coinEnum.getCode());
        //用户当前已完成提现流水
        RPCResponse<BigDecimal> validBalance = bettingService.sumBettingEffectiveAmountForWithdraw(bettingCommParamDO);
        if (RPCResponseUtils.isFail(validBalance)) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }

        result.setLeftAmount(BigDecimal.ZERO);
        if (userDO.getUserType() == UserConstant.Type.PLAYER && effectBet != null
                && effectBet.getRequiredBet().compareTo(BigDecimal.ZERO) == 1) {

            BigDecimal leftAmount = effectBet.getRequiredBet().subtract(validBalance.getData());
            if (leftAmount.compareTo(BigDecimal.ONE) == -1) {
                leftAmount = BigDecimal.ZERO;
            }
            result.setLeftAmount(leftAmount.setScale(2, RoundingMode.UP));
            result.setRequireAmount(effectBet.getRequiredBet().setScale(2, RoundingMode.UP));
        }

        // 当前用户今日已提现金额
        BigDecimal todayWithdrawAmount = glWithdrawBusiness.getWithdrawAmountTotal(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now));
        result.setWithdrawSumAmount(todayWithdrawAmount);
    }

    /**
     * 根据每周完成的体育投注、娱乐投注计算赠送免费提现次数
     *
     * @return
     * @throws GlobalException
     */
    private WithdrawCalcFreeTimesResult calcWithdrawFreeTimes(GlUserDO userDO, List<GlWithdrawRule> sportRuleList, List<GlWithdrawRule> funGameRuleList, Date now)
            throws GlobalException {
        if (ObjectUtils.isEmpty(sportRuleList) || ObjectUtils.isEmpty(funGameRuleList)) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }

        //当前周最小值&最大值(周一 00:00:00  周日23:59:59)
        Date weekMinTime = DateUtils.getCurrentMonday(now);
        Date weekMaxTime = DateUtils.getCurrentSunday(now);

        FindBettingCommParamDO bettingCommParamDO = new FindBettingCommParamDO();
        bettingCommParamDO.setUserId(userDO.getId());
        bettingCommParamDO.setStartTime(weekMinTime.getTime());
        bettingCommParamDO.setEndTime(weekMaxTime.getTime());

        //本周已完成体育类流水
        List<Integer> sportCodes = gameConfig.getGamePlatformCodeByType(FundConstant.GamePlatBetEffectType.SPORT);
        bettingCommParamDO.setGamePlatformIds(sportCodes);
        RPCResponse<BigDecimal> sport = bettingService.sumBettingEffectiveAmountForWithdraw(bettingCommParamDO);
        if (RPCResponseUtils.isFail(sport)) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }

        //本周已完成娱乐类流水
        List<Integer> funCodes = gameConfig.getGamePlatformCodeByType(FundConstant.GamePlatBetEffectType.FUN);
        bettingCommParamDO.setGamePlatformIds(funCodes);
        RPCResponse<BigDecimal> fun = bettingService.sumBettingEffectiveAmountForWithdraw(bettingCommParamDO);
        if (RPCResponseUtils.isFail(fun)) {
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }

        BigDecimal sportBet = sport.getData();
        BigDecimal funBet = fun.getData();

        WithdrawCalcFreeTimesResult calcFreeTimesResult = new WithdrawCalcFreeTimesResult();

        // 体育流水赠送免费次数
        Integer sportFreeTimes = 0;
        BigDecimal sportValidBet = BigDecimal.ZERO;

        if (sportBet.compareTo(BigDecimal.ZERO) >= 0) {
            for (GlWithdrawRule sportRule : sportRuleList) {
                //体育流水赠送配置 升序设置
                if (sportBet.compareTo(sportRule.getBetAmount()) == -1) {
                    sportValidBet = sportRule.getBetAmount().subtract(sportBet);
                    break;
                }
                sportFreeTimes = sportRule.getFreeTimes();
            }
        }
        calcFreeTimesResult.setSportValidBet(sportValidBet);
        calcFreeTimesResult.setSportFreeTims(sportFreeTimes);

        // 娱乐类流水赠送免费次数
        Integer funFreeTimes = 0;
        BigDecimal funValidBet = BigDecimal.ZERO;

        if (funBet.compareTo(BigDecimal.ZERO) >= 0) {
            for (GlWithdrawRule funRule : funGameRuleList) {
                if (funBet.compareTo(funRule.getBetAmount()) == -1) {
                    funValidBet = funRule.getBetAmount().subtract(funBet);
                    break;
                }
                funFreeTimes = funRule.getFreeTimes();
            }
        }

        calcFreeTimesResult.setFunValidBet(funValidBet);
        calcFreeTimesResult.setFunFreeTimes(funFreeTimes);
        return calcFreeTimesResult;
    }

    /**
     * 提现接口是否开启
     *
     * @param userDO
     * @return
     */
    @Override
    public RejectWithdrawRequestDO isClosed(GlUserDO userDO) {
        // 提现功能是否开启
        if (redisService.exists(RedisKeyHelper.WITHDRAW_REJECT_TIPS)) {
            String json = redisService.get(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
            return JSONObject.toJavaObject(JSONObject.parseObject(json), RejectWithdrawRequestDO.class);
        }
        return null;
    }

    /**
     * 游客查看提现配置信息(提现说明)
     *
     * @return
     * @throws GlobalException
     */
    @Override
    public GlWithdrawInfoResult withdrawInfoForVisitor() throws GlobalException {
        DigitalCoinEnum coinEnum = DigitalCoinEnum.CNY;
        GlWithdrawInfoResult result = new GlWithdrawInfoResult();
        // 提现配置文件
        GlWithdrawGeneralConfig generalConfig = withdrawConfigBusiness.getWithdrawGeneralConfig(coinEnum.getCode());
        GlWithdrawQuickConfig quickConfig = withdrawConfigBusiness.getWithdrawQuickConfig(coinEnum.getCode());
        GlWithdrawProxyConfig proxyConfig = withdrawConfigBusiness.getWithdrawProxyConfig(coinEnum.getCode());
        GlWithdrawCommonConfig commonConfig = withdrawConfigBusiness.getWithdrawCommonConfig(coinEnum.getCode());
        // 提现说明开关
        result.setTipStatus("1".equals(commonConfig.getTipStatus()) ? "1" : "0");

        quickConfig.setMultiple(commonConfig.getMultiple());
        quickConfig.setAmountLimit(commonConfig.getAmountLimit());

        generalConfig.setAmountLimit(commonConfig.getAmountLimit());
        generalConfig.setMultiple(commonConfig.getMultiple());

        result.setGeneralConfig(generalConfig);
        result.setQuickConfig(quickConfig);
        result.setProxyConfig(proxyConfig);
        return result;
    }

    private boolean checkWhiteList(Integer userId) {
        if (StringUtils.isEmpty(whitelistCfg)) {
            return false;
        }
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userId);
        List<String> whiteList = Arrays.asList(whitelistCfg.split(","));
        for (String item : whiteList) {
            if (item.equals(userlevel.getLevelId().toString())) {
                return true;
            }
        }
        return false;
    }

    /**
     * 提现验证
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param key
     * @return
     * @throws GlobalException
     */
    private String validateWithdrawParam(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, String key) throws GlobalException {
        //虚拟用户不可以提现，表现为立刻失败
        if (ObjectUtils.isEmpty(userDO) || "1".equals(userDO.getIsFake())) {
            return "网络故障无法打开页面";
        }
        if ((null != withdrawSubmitDO.getUsdtId() && null != withdrawSubmitDO.getCardId()) ||
                (null == withdrawSubmitDO.getCardId() && null == withdrawSubmitDO.getUsdtId())) {
            return "请求参数异常:请选择提现到账类型";
        }
        // 用户手机号码和银行卡绑定验证
        String checkedOnOff = redisService.get(RedisKeyHelper.CHECKED_USER_INFO_ONOFF);
        if (UserConstant.Type.PLAYER == userDO.getUserType() && StringUtils.isNotEmpty(checkedOnOff) && "1".equals(checkedOnOff)) {
            if (StringUtils.isEmpty(userDO.getTelephone())) {
                return "请绑定手机号码";
            }
            List<GlWithdrawUserBankCard> glWithdrawUserBankCards = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
            if (ObjectUtils.isEmpty(glWithdrawUserBankCards) && ObjectUtils.isNotEmpty(withdrawSubmitDO.getCardId())) {
                return "请绑定银行卡";
            }
        }
        if (new BigDecimal(withdrawSubmitDO.getAmount().intValue()).compareTo(withdrawSubmitDO.getAmount()) < 0) {
            return "请输入整数金额";
        }

        if (null == withdrawSubmitDO.getCardId() && null == withdrawSubmitDO.getUsdtId()) {
            return "参数异常:请选择收款账户";
        }
        // 短信验证
        RPCResponse<Boolean> response = glUserSecurityService.getWithdrawSecurity(userDO.getId());
        if (RPCResponseUtils.isSuccess(response) && response.getData()) {
            MobileValidateDto validateDto = new MobileValidateDto();
            validateDto.setTelArea(userDO.getTelArea());
            validateDto.setMobile(userDO.getTelephone());
            validateDto.setType(ProjectConstant.MSG_TYPE_CASH);
            validateDto.setCode(withdrawSubmitDO.getCode());
            if (StringUtils.isEmpty(withdrawSubmitDO.getCode()) || !RPCResponseUtils.getData(systemApiService.mobileValidate(validateDto))) {
                return "短信验证码错误";
            }
            systemApiService.clearCode(userDO.getTelArea(), userDO.getTelephone(), ProjectConstant.MSG_TYPE_CASH);
        }

        if (userDO.getStatus() == UserConstant.Status.NEW) {
            return "请修改初始密码.";
        }

        if (userDO.getStatus() != UserConstant.Status.NORMAL) {
            return "提现账号异常";
        }

        if (!RPCResponseUtils.getData(userManageService.hasWithdrawAuditRecord(userDO.getId()))) {
            return "账户有待审核资金流水调整";
        }

        if (glWithdrawBusiness.validateLastWithdraw(userDO.getId())) {
            return "上笔提现正在出款,请稍后再试.";
        }

        Long tempCount = redisService.incrBy(key, 1);
        if (tempCount > 1) {
            return "操作频繁,请90秒后尝试";
        }
        redisService.setTTL(key, 90);

        //极速提现 - 提交金额为到帐金额
        if (withdrawSubmitDO.getType() == 4) {
            //到账金额
            BigDecimal arrailAmount = withdrawSubmitDO.getAmount();
            withdrawSubmitDO.setArrivalAmount(arrailAmount);
            Optional.ofNullable(RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class)).ifPresent(obj -> {
                //提现金额
                BigDecimal subAmount = BigDecimal.ZERO;
                if(obj.getWithdrawHandlingFeeType() == 2) {
                    subAmount = arrailAmount.add(obj.getWithdrawHandlingFeeValue());
                } else {
                    BigDecimal fee = withdrawSubmitDO.getAmount().multiply(obj.getWithdrawHandlingFeeValue()).divide(BigDecimal.valueOf(100),2,RoundingMode.DOWN);
                    if (fee.compareTo(obj.getWithdrawHandlingFeeMax()) == 1) {
                        fee = obj.getWithdrawHandlingFeeMax();
                    }
                    subAmount = withdrawSubmitDO.getArrivalAmount().add(fee);
                }
                withdrawSubmitDO.setAmount(subAmount);
            });
        }

        // 金额判断
        DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(userDO.getId(), DigitalCoinEnum.CNY);
        if (account.getBalance().compareTo(withdrawSubmitDO.getAmount()) == -1) {
            return "可提余额不足";
        }

        GlWithdrawEffectBet effectBet = glWithdrawEffectBetBusiness.findOne(userDO.getId(),DigitalCoinEnum.CNY.getCode());

        // 判断提现流水是否完成
        if (userDO.getUserType() == UserConstant.Type.PLAYER && effectBet.getRequiredBet() != null
                && effectBet.getRequiredBet().setScale(2, RoundingMode.DOWN).compareTo(BigDecimal.ZERO) == 1) {

            //用户已完成有效流水
            FindBettingCommParamDO bettingCommParamDO = new FindBettingCommParamDO();
            bettingCommParamDO.setUserId(userDO.getId());
            bettingCommParamDO.setStartTime(effectBet.getEffectStartTime().getTime());
            bettingCommParamDO.setEndTime(new Date().getTime());
            bettingCommParamDO.setGamePlatformIds(new ArrayList<>());
            bettingCommParamDO.setCoinCode(DigitalCoinEnum.CNY.getCode());

            RPCResponse<BigDecimal> validBalance = bettingService.sumBettingEffectiveAmountForWithdraw(bettingCommParamDO);
            if (RPCResponseUtils.isFail(validBalance)) {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
            if (effectBet.getRequiredBet().compareTo(validBalance.getData().add(BigDecimal.ONE)) == 1) {
                return "提现失败:提现流水不足";
            }
        }
        return null;
    }

    private String validateProxy(GlWithdrawProxyConfig proxyConfig, BigDecimal amount, BigDecimal todayWithdrawAmount,
                                 Integer withdrawProxyCount, Integer withdrawType , GlWithdrawUserUsdtAddress usdtAddress) {
        //银行提现金额校验
        if (withdrawType == 1) {
            if (amount.compareTo(proxyConfig.getMinLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(proxyConfig.getMinLimit()));
                return message;
            }
            if (amount.compareTo(proxyConfig.getMaxLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(proxyConfig.getMaxLimit()));
                return message;
            }
        }
        //USDT提现金额校验
        else if (withdrawType == 2) {
            if (amount.compareTo(proxyConfig.getMinUSDTLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(proxyConfig.getMinUSDTLimit()));
                return message;
            }
            if (amount.compareTo(proxyConfig.getMaxUSDTLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(proxyConfig.getMaxUSDTLimit()));
                return message;
            }
            if (!CollectionUtils.isEmpty(proxyConfig.getUsdtLimits())) {
                for (GlWithdrawUSDTLimit obj: proxyConfig.getUsdtLimits()) {
                    if (obj.getProtocol().equals(usdtAddress.getProtocol())
                        && (amount.compareTo(obj.getMinAmount()) < 0 || amount.compareTo(obj.getMaxAmount()) > 0)) {
                        return String.format("提现金额不在提现金额区间内： %s - %s元", decimalFormat.format(obj.getMinAmount()), decimalFormat.format(obj.getMaxAmount()));
                    }
                }
            }
        }

        if (withdrawProxyCount >= proxyConfig.getCountLimit()) {
            String message = String.format("今日已提现 %d 次,提现次数达到上限.", withdrawProxyCount);
            return message;
        }
        if (proxyConfig.getAmountLimit().compareTo(todayWithdrawAmount.add(amount)) == -1) {
            String message = String.format("今日已提现 %s 元,本次提现 %s 元.超出提现金额上限",
                    decimalFormat.format(todayWithdrawAmount), decimalFormat.format(amount));
            return message;
        }
        return null;
    }

    private String validateGeneral(Integer userId, GlWithdrawGeneralConfig generalConfig, BigDecimal amount, Date now,
                                   Integer withdrawType, GlWithdrawUserUsdtAddress usdtAddress) {
        // 已提现次数
        int generalWithdrawCount = glWithdrawBusiness.getWithdrawCount(userId, DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 1, generalConfig.getCoin());
        //银行卡提现校验金额
        if (withdrawType == 1) {
            if (amount.compareTo(generalConfig.getMinLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(generalConfig.getMinLimit()));
                return message;
            }
            if (amount.compareTo(generalConfig.getMaxLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(generalConfig.getMaxLimit()));
                return message;
            }
        }
        //USDT提现校验金额
        else if (withdrawType == 2) {
            if (amount.compareTo(generalConfig.getMinUSDTLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(generalConfig.getMinUSDTLimit()));
                return message;
            }
            if (amount.compareTo(generalConfig.getMaxUSDTLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(generalConfig.getMaxUSDTLimit()));
                return message;
            }
            if (!CollectionUtils.isEmpty(generalConfig.getUsdtLimits())) {
                for (GlWithdrawUSDTLimit obj: generalConfig.getUsdtLimits()) {
                    if (obj.getProtocol().equals(usdtAddress.getProtocol())
                            && (amount.compareTo(obj.getMinAmount()) < 0 || amount.compareTo(obj.getMaxAmount()) > 0)) {
                        return String.format("提现金额不在提现金额区间内： %s - %s元", decimalFormat.format(obj.getMinAmount()), decimalFormat.format(obj.getMaxAmount()));
                    }
                }
            }
        }

        if (generalWithdrawCount >= generalConfig.getCountLimit()) {
            String message = String.format("今日已提现 %d 次,提现次数达到上限.", generalWithdrawCount);
            return message;
        }
        String feeType = "fix";
        if (feeType.equals(generalConfig.getFeeType())) {
            if (amount.compareTo(generalConfig.getFee()) <= 0) {
                String message = String.format("提现金额不能低于固定手续费 %s元", decimalFormat.format(generalConfig.getFeeType()));
                return message;
            }
        }
        return null;
    }

    private String validateQuick(Integer userId, GlWithdrawQuickConfig quickConfig, BigDecimal amount, Date now
            , Integer withdrawType, GlWithdrawUserUsdtAddress usdtAddress) throws GlobalException {
        GlWithdrawGeneralConfig generalConfig = withdrawConfigBusiness.getWithdrawGeneralConfig(quickConfig.getCoin());
        //银行卡提现
        if (withdrawType == 1) {
            if (amount.compareTo(quickConfig.getMinLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(quickConfig.getMinLimit()));
                return message;
            }
            if (amount.compareTo(quickConfig.getMaxLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(quickConfig.getMaxLimit()));
                return message;
            }
        }
        //USDT提现
        else if (withdrawType == 2) {
            if (amount.compareTo(quickConfig.getMinUSDTLimit()) == -1) {
                String message = String.format("提现金额低于单笔最低限额 %s元", decimalFormat.format(quickConfig.getMinUSDTLimit()));
                return message;
            }
            if (amount.compareTo(quickConfig.getMaxUSDTLimit()) == 1) {
                String message = String.format("提现金额超出单笔最高限额 %s元", decimalFormat.format(quickConfig.getMaxUSDTLimit()));
                return message;
            }
            if (!CollectionUtils.isEmpty(quickConfig.getUsdtLimits())) {
                for (GlWithdrawUSDTLimit obj: quickConfig.getUsdtLimits()) {
                    if (obj.getProtocol().equals(usdtAddress.getProtocol())
                            && (amount.compareTo(obj.getMinAmount()) < 0 || amount.compareTo(obj.getMaxAmount()) > 0)) {
                        return String.format("提现金额不在提现金额区间内： %s - %s元", decimalFormat.format(obj.getMinAmount()), decimalFormat.format(obj.getMaxAmount()));
                    }
                }
            }
        }

        Integer largeWithdrawCount = glWithdrawBusiness.getWithdrawCount(userId, DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 2,quickConfig.getCoin());
        if (largeWithdrawCount >= quickConfig.getCountLimit()) {
            String message = String.format("今日大额已提现 %d 次,提现次数达到上限.", largeWithdrawCount);
            return message;
        }
        return null;
    }

    private String validateC2C(GlUserDO userDO, C2CConfigDO config, BigDecimal amount, Date now, BigDecimal arrivalAmount,String coin) {
        //过滤vip等级和用户层级
        UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
        if (!config.getWithdrawVipLevels().contains(vipCache.getVipLevel())) {
            return "当前用户vip等级与配置不匹配";
        }
        if (!config.getChooseAmounts().contains(Integer.valueOf(arrivalAmount.setScale(0,RoundingMode.DOWN).toString()))) {
            return "请选择页面提供的快捷金额";
        }
        // 已提现次数
        int cToCWithdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 4, coin);
        if (cToCWithdrawCount >= config.getWithdrawDailyUseLimit()) {
            return String.format("今日极速已提现 %d 次,提现次数达到上限.", cToCWithdrawCount);
        }

        if (2 == config.getWithdrawHandlingFeeType()) {
            if (amount.compareTo(config.getWithdrawHandlingFeeValue()) <= 0) {
                String message = String.format("提现金额不能低于固定手续费 %s元", decimalFormat.format(config.getWithdrawHandlingFeeValue()));
                return message;
            }
        }
        return null;
    }

    /**
     * 提现订单提交
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public List<GlWithdraw> doWithdrawSubmit(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException {
        log.info("doWithdrawSubmit_withdrawSubmitDO:{},userDO:{}", JSON.toJSONString(withdrawSubmitDO) , JSON.toJSONString(userDO));
        // GlUserDO userDO对象中的手机信息被脱敏，查一次数据
        userDO = RPCResponseUtils.getData(userService.findById(userDO.getId()));

        Validator validator = Validator.build().add(null == userDO, "用户未登录或不存在").valid();

        if (DigitalCoinEnum.getDigitalCoin(withdrawSubmitDO.getCoin()) == null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "币种异常");
        }

        // 判断是USDT提现还是银行卡提现
        Integer withdrawType = 1;
        if (withdrawSubmitDO.getCardId() != null && withdrawSubmitDO.getCardId() > 0) {
            withdrawType = 1;
        }
        if (withdrawSubmitDO.getUsdtId() != null && withdrawSubmitDO.getUsdtId() > 0) {
            withdrawType = 2;
        }
        // 检查普通提现功能是否开启（银行卡提现）
        if (redisService.exists(RedisKeyHelper.WITHDRAW_REJECT_TIPS)
                && withdrawType == 1 && withdrawSubmitDO.getType() == FundConstant.AisleType.NORMAL ) {
            String json = redisService.get(RedisKeyHelper.WITHDRAW_REJECT_TIPS);
            RejectWithdrawRequestDO rejectWithdrawRequest = JSONObject.toJavaObject(JSONObject.parseObject(json), RejectWithdrawRequestDO.class);
            throw new GlobalException(ResultCode.WITHDRAWAL_CLOSED, rejectWithdrawRequest.getContent());
        }
        GlWithdrawInfoResult result = new GlWithdrawInfoResult();
        // 检查提现功能是否开启（数字货币提现）
        Boolean isProxy = userDO.getUserType() == UserConstant.UserType.PROXY;
        if (setWithdrawUsdtOpen(userDO.getId(), isProxy ,result) == false && withdrawType == 2) {
            throw new GlobalException(ResultCode.WITHDRAWAL_CLOSED, "USDT提现已关闭,请联系客服.");
        }
        //极速提现
        if (setC2CWithdrawOpen(userDO,withdrawSubmitDO.getCoin()) == false && withdrawSubmitDO.getType() == FundConstant.AisleType.C2C) {
            throw new GlobalException(ResultCode.WITHDRAWAL_CLOSED, "极速提现已关闭,请联系客服.");
        }

        // 检查当前会员层级是否可以提现
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        if (userlevel.getWithdrawOff() == 1) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "当前会员层级不可提现,请联系客服.");
        }

        // 多端控制
        String key = RedisKeyHelper.LOCK_WITHDRAW + userDO.getId();

        //提现数据验证
        String failMessage = validateWithdrawParam(withdrawSubmitDO, userDO, key);
        if (StringUtils.isNotEmpty(failMessage)) {
            log.error("会员:{}提现验证未通过：{}", userDO.getUsername(), failMessage);
            throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
        }

        //校验提现银行卡或者USDT收币地址
        List<String> bankCards = new ArrayList<>();
        GlWithdrawUserBankCard userCard = null;
        if (null != withdrawSubmitDO.getCardId()) {
            userCard = glWithdrawUserBankCardBusiness.findById(withdrawSubmitDO.getCardId());
            if (userCard != null && !userDO.getId().equals(userCard.getUserId())) {
                failMessage = "提现银行卡信息异常.";
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }

            if (!checkWithdrawBankSetting(userCard.getBankId())) {
                failMessage = userCard.getBankName() + "维护，请更换其他提现银行卡.";
                log.error("会员:{}提现验证未通过：{}", userDO.getUsername(), failMessage);
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }
            bankCards.add(userCard.getCardNo().trim());
        }

        GlWithdrawUserUsdtAddress usdtAddress = null;
        if (null != withdrawSubmitDO.getUsdtId()) {
            usdtAddress = glWithdrawUserUsdtAddressBusiness.findById(withdrawSubmitDO.getUsdtId());
            if (null != usdtAddress && !usdtAddress.getUserId().equals(userDO.getId())) {
                failMessage = "USDT收币信息异常.";
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }
            if (usdtAddress !=null && !result.getProtocols().isEmpty() && !result.getProtocols().contains(usdtAddress.getProtocol())) {
                failMessage = "USDT钱包协议不支持，请更换.";
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }
            if (usdtAddress != null && usdtAddress.getStatus() != 0) {
                failMessage = "USDT收币钱包地址不存在，请选择新的提币地址.";
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }
        }


        // 用户设备号从当前Request请求中获取
        userDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
        //开关打开 进行黑名单禁止功能
        BlackCheckBanned blackCheckBanned = new BlackCheckBanned();
        blackCheckBanned.setUser(userDO);
        blackCheckBanned.setBankCards(bankCards);
        blackCheckBanned.setIp(withdrawSubmitDO.getRequestIp());
        blackCheckBanned.setTypeList(Arrays.asList(ProjectConstant.BlackBehavior.WITHDRAW, ProjectConstant.BlackBehavior.WITHDRAW_FULL_LOCK));
        RPCResponse<Boolean> response = blackService.checkBlackBanned(blackCheckBanned);

        if (RPCResponseUtils.isSuccess(response) && response.getData()) {
            throw new GlobalException(ResultCode.DATA_ERROR, "您已经被禁止提现,请联系客服.");
        }

        //V2 黑名单需求添加 2.异步校验是否监控
        String onOff = redisService.get(RedisKeyHelper.BLACK_ONOFF);
        if (StringUtils.isNotEmpty(onOff) && "1".equals(onOff)) {
            userDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
            this.checkBlackMonitor(userDO, bankCards, withdrawSubmitDO.getRequestIp(), ProjectConstant.BlackBehavior.WITHDRAW_CHANGE_LEVEL);
            this.checkBlackMonitor(userDO, bankCards, withdrawSubmitDO.getRequestIp(), ProjectConstant.BlackBehavior.WITHDRAW);
        }

        Date now = new Date();
        // 本次提现总金额
        BigDecimal amount = withdrawSubmitDO.getAmount().setScale(2, RoundingMode.DOWN);
        // 当前用户今日已提现金额
        BigDecimal todayWithdrawAmount = glWithdrawBusiness.getWithdrawAmountTotal(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now));
        // 提现类型（1-普通提现、2-大额提现、3-代理提现、4-极速提现）
        Integer type = withdrawSubmitDO.getType();
        // 提现拆单配置
        List<GlWithdrawSplitRule> splitRuleList = new ArrayList<>();

        List<GlWithdraw> glWithdrawList = new ArrayList<>();
        // 代理提现
        if (userDO.getUserType() == UserConstant.Type.PROXY) {

            FundProxyAccount proxyAccount = fundProxyAccountBusiness.findById(userDO.getId());
            if (null == proxyAccount) {
                throw new GlobalException(ResultCode.DATA_ERROR, "代理可提现额度异常,请联系客服");
            }
            if (proxyAccount.getValidWithdrawal().compareTo(BigDecimal.ZERO) != 1
                    || proxyAccount.getValidWithdrawal().compareTo(amount) == -1) {
                throw new GlobalException(ResultCode.DATA_ERROR, "代理可提现额度不足,最多可提现金额:" + proxyAccount.getValidWithdrawal());
            }
            GlWithdrawProxyConfig proxyConfig = withdrawConfigBusiness.getWithdrawProxyConfig(withdrawSubmitDO.getCoin());
            Integer proxyWithdrawTotalCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), null, 3, proxyConfig.getCoin());
            // 代理提现验证
            failMessage = validateProxy(proxyConfig, amount, todayWithdrawAmount, proxyWithdrawTotalCount, withdrawType, usdtAddress);
            if (failMessage != null) {
                throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
            }
            splitRuleList = proxyConfig.getSplitRuleList();
            glWithdrawList = this.doWithdrawForProxy(withdrawSubmitDO, userDO, proxyConfig, userCard, usdtAddress, now, withdrawType);

        } else {
            GlWithdrawCommonConfig commonConfig = withdrawConfigBusiness.getWithdrawCommonConfig(withdrawSubmitDO.getCoin());
            if (commonConfig.getAmountLimit().compareTo(amount.add(todayWithdrawAmount)) == -1) {
                String message = String.format("今日已提现%s元,本次提现%s元,提现金额超出上限.",
                        decimalFormat.format(todayWithdrawAmount), decimalFormat.format(amount));
                throw new GlobalException(ResultCode.DATA_ERROR, message);
            }
            //普通提现
            if (type == 1) {
                GlWithdrawGeneralConfig generalConfig = withdrawConfigBusiness.getWithdrawGeneralConfig(withdrawSubmitDO.getCoin());

                failMessage = validateGeneral(userDO.getId(), generalConfig, amount, now, withdrawType, usdtAddress);
                if (failMessage != null) {
                    throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
                }
                //普通提现根据用户层级验证提现限额

                if (withdrawType == 1 && !CollectionUtils.isEmpty(generalConfig.getLimitList())) {
                    for (GeneralConfigLimitDO obj: generalConfig.getLimitList()) {
                       if (obj.getLevelIds().contains(userlevel.getLevelId())
                               && (withdrawSubmitDO.getAmount().compareTo(obj.getMinAmount()) < 0
                                    || withdrawSubmitDO.getAmount().compareTo(obj.getMaxAmount()) > 0)) {
                           String message = String.format("您的提现金额区间为%s - %s",decimalFormat.format(obj.getMinAmount()),decimalFormat.format(obj.getMaxAmount()));
                           throw new GlobalException(ResultCode.DATA_ERROR, message);
                       }
                    }
                }
                GlWithdraw withdraw = this.doWithdrawForCommon(withdrawSubmitDO, userDO, generalConfig, userCard, usdtAddress, now);
                glWithdrawList.add(withdraw);

            } else if (type == 2){ //大额提现
                GlWithdrawQuickConfig quickConfig = withdrawConfigBusiness.getWithdrawQuickConfig(withdrawSubmitDO.getCoin());

                failMessage = validateQuick(userDO.getId(), quickConfig, amount, now, withdrawType, usdtAddress);
                if (failMessage != null) {
                    throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
                }
                splitRuleList = quickConfig.getSplitRuleList();

                glWithdrawList = this.doWithdrawForLarge(withdrawSubmitDO, userDO, quickConfig, userCard, usdtAddress, now, withdrawType);
            } else if (type == 4) {
                //撮合系统提现配置
                C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
                failMessage = validateC2C(userDO, configDO, amount, now, withdrawSubmitDO.getArrivalAmount(), withdrawSubmitDO.getCoin());
                if (failMessage != null) {
                    throw new GlobalException(ResultCode.DATA_ERROR, failMessage);
                }
                GlWithdraw withdraw = this.doWithdrawForCToC(withdrawSubmitDO, userDO, configDO, userCard, usdtAddress, now);
                glWithdrawList.add(withdraw);
            }
        }

        // 更新银行卡选中状态
        if (null != userCard && userCard.getSelected().equals(0)) {
            glWithdrawUserBankCardBusiness.doUserCardSelect(userCard);
        }
        // 更新USDT钱包地址选中状态
        if (null != usdtAddress && usdtAddress.getSelected().equals(0)) {
            glWithdrawUserUsdtAddressBusiness.doSelect(usdtAddress);
        }

        // 提交提现事务&上报
        if (glWithdrawTransactionalBusiness.doWithdraw(userDO, glWithdrawList, withdrawSubmitDO, JSON.toJSONString(splitRuleList))) {
            for (GlWithdraw withdraw : glWithdrawList) {
                withdraw.setCardNo(StringEncryptor.encryptBankCard(withdraw.getCardNo()));
                //发送自动出款消息
                glWithdrawBusiness.sendWithdrawMsg(withdraw);
            }
            redisService.delete(key);
            return glWithdrawList;
        } else {
            redisService.delete(key);
            log.info("用户:{} 提交提现事务异常", userDO.getUsername());
            throw new GlobalException(ResultCode.DATA_ERROR, "流水或余额不足");
        }
    }

    /**
     * 普通提现
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param generalConfig
     * @param userCard
     * @param now
     * @return
     * @throws GlobalException
     */
    private GlWithdraw doWithdrawForCommon(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, GlWithdrawGeneralConfig generalConfig,
                                           GlWithdrawUserBankCard userCard, GlWithdrawUserUsdtAddress usdtAddress, Date now) throws GlobalException {
        // 普通提现：已使用免费次数
        int generalFreeWithdrawCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now), 0, 1, generalConfig.getCoin());

        // 本次提现总金额
        BigDecimal amount = withdrawSubmitDO.getAmount().setScale(2, RoundingMode.DOWN);
        // True 免费
        Boolean freeWithdraw = generalFreeWithdrawCount < generalConfig.getFreeTimes();
        // 提现手续费
        BigDecimal withdrawFee = BigDecimal.ZERO;
        if (!freeWithdraw) {
            if ("fix".equals(generalConfig.getFeeType())) {
                withdrawFee = generalConfig.getFee();
            } else {
                withdrawFee = amount.multiply(generalConfig.getFee()).divide(BigDecimal.valueOf(100));
                if (withdrawFee.compareTo(generalConfig.getFeeLimit()) == 1) {
                    withdrawFee = generalConfig.getFeeLimit();
                }
            }
        }
        //用户提现记录
        GlWithdrawDO withdrawDO = new GlWithdrawDO();
        withdrawDO.setAmount(amount);
        withdrawDO.setFee(withdrawFee);
        withdrawDO.setClientType(withdrawSubmitDO.getHeaderOsType());
        withdrawDO.setCreateDate(now);
        withdrawDO.setLastUpdate(now);
        withdrawDO.setFreeStatus(freeWithdraw ? 0 : 1);
        withdrawDO.setAisleType(1);
        withdrawDO.setSplitStatus(0);
        withdrawDO.setBatchNumber(null);
        withdrawDO.setIp(withdrawSubmitDO.getRequestIp());
        withdrawDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
        withdrawDO.setTotalAmount(amount);
        withdrawDO.setCoin(withdrawSubmitDO.getCoin());

        //会员层级数据
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());

        //提现数据风控判断 & 出款方式确认
        GlWithdraw glWithdraw = glWithdrawBusiness.doUserWithdraw(withdrawDO, userDO, userCard, usdtAddress, level);

        return glWithdraw;
    }

    /**
     * 大额提现
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param quickConfig
     * @param userCard
     * @param now
     * @return
     * @throws GlobalException
     */
    private List<GlWithdraw> doWithdrawForLarge(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, GlWithdrawQuickConfig quickConfig,
                                                GlWithdrawUserBankCard userCard, GlWithdrawUserUsdtAddress usdtAddress, Date now, Integer withdrawType) throws GlobalException {
        List<GlWithdraw> result = new ArrayList<>();
        // 本次提现总金额
        BigDecimal amount = withdrawSubmitDO.getAmount().setScale(2, RoundingMode.DOWN);
        // 大额提现本周已使用免费次数
        int quickWithdrawFreeTotalCount = glWithdrawBusiness.getWithdrawCount(userDO.getId(), DateUtils.getCurrentMonday(now), DateUtils.getCurrentSunday(now), 0, 2, quickConfig.getCoin());
        //拆单次数
        int splitCount = 0;
        //拆单金额
        BigDecimal splitAmount = BigDecimal.ZERO;
        //拆单随机金额
        BigDecimal randomAmount = BigDecimal.ZERO;

        WithdrawCalcFreeTimesResult freeTimesResult = this.calcWithdrawFreeTimes(userDO, quickConfig.getSportRuleList(), quickConfig.getFunGameRuleList(), now);
        //本次提现是否免费 True 免费
        Boolean freeWithdraw = quickWithdrawFreeTotalCount < freeTimesResult.getFunFreeTimes() + freeTimesResult.getSportFreeTims();

        //数字货币不拆单
        if (withdrawType == 1) {
            for (GlWithdrawSplitRule item : quickConfig.getSplitRuleList()) {
                // 获取拆单配置信息，计算拆单次数
                if (amount.compareTo(item.getMinAmount()) == 1
                        && amount.compareTo(item.getMaxAmount()) != 1) {
                    splitAmount = item.getSplitAmount();
                    randomAmount = item.getRandomAmount();
                    splitCount = (int) Math.ceil(amount.divide(splitAmount, 1, BigDecimal.ROUND_UP).doubleValue());
                }
            }
        }
        // 提现金额拆分后
        List<BigDecimal> withdrawAmount = new ArrayList<>();
        double moneyMin = 0.01;
        Random random = new Random();
        NumberFormat formatter = new DecimalFormat("#.##");

        if (splitCount > 0) {
            for (int i = 1; i <= splitCount; i++) {
                BigDecimal money = new BigDecimal(formatter.format(random.nextDouble() * (randomAmount.doubleValue() - moneyMin) + moneyMin)).setScale(0, RoundingMode.DOWN);
                // 已拆分金额
                BigDecimal sumAmount = withdrawAmount.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                // 临界值拆分 随机值 + 拆分值 不能大于提现金额
                if (money.add(splitAmount).compareTo(amount) == 1 && i == 1) {
                    withdrawAmount.add(amount);
                } else {
                    // 本次拆分金额
                    BigDecimal splitMoney = money.add(splitAmount);
                    if (money.add(sumAmount).compareTo(amount) == 1 || splitMoney.add(sumAmount).compareTo(amount) == 1) {
                        splitMoney = amount.subtract(sumAmount);
                    }
                    if (splitMoney.compareTo(BigDecimal.ZERO) == 1) {
                        withdrawAmount.add(splitMoney);
                    }
                }
            }
        } else {
            withdrawAmount.add(amount);
        }

        // 标识本次提现是否拆单（0-未拆单、1-拆单）
        Integer splitStatus = 0;

        int index = withdrawAmount.size();
        if (index != 1) {
            splitStatus = 1;
            // 拆分的最后一笔金额  不能小于提现最低限额
            if (withdrawAmount.get(index - 1).compareTo(quickConfig.getMinLimit()) == -1) {
                withdrawAmount.set(index - 2, withdrawAmount.get(index - 1).add(withdrawAmount.get(index - 2)));
                withdrawAmount.remove(index - 1);
            }
        }
        //会员层级数据
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());

        //计算拆单批次号
        Integer splitTotalCount = glWithdrawSplitBusiness.getTodayWithdrawSplitCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now));

        //计算手续费
        for (int i = 0; i < withdrawAmount.size(); i++) {
            String batchNumber = null;
            if (splitStatus == 1) {
                batchNumber = (splitTotalCount + 1) + "-" + (withdrawAmount.size()) + "-" + (i + 1);
            }
            BigDecimal withdrawFee = BigDecimal.ZERO;
            //计算手续费
            if (!freeWithdraw) {
                if ("fix".equals(quickConfig.getFeeType())) {
                    withdrawFee = quickConfig.getFee();
                } else {
                    withdrawFee = withdrawAmount.get(i).multiply(quickConfig.getFee()).divide(BigDecimal.valueOf(100));
                }
            }
            //用户提现记录
            GlWithdrawDO withdrawDO = new GlWithdrawDO();
            withdrawDO.setAmount(withdrawAmount.get(i));
            withdrawDO.setFee(withdrawFee);
            withdrawDO.setClientType(withdrawSubmitDO.getHeaderOsType());
            withdrawDO.setCreateDate(now);
            withdrawDO.setLastUpdate(now);
            withdrawDO.setFreeStatus(freeWithdraw ? 0 : 1);
            withdrawDO.setAisleType(2);
            withdrawDO.setSplitStatus(splitStatus);
            withdrawDO.setBatchNumber(batchNumber);
            withdrawDO.setIp(withdrawSubmitDO.getRequestIp());
            withdrawDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
            withdrawDO.setTotalAmount(amount);
            withdrawDO.setCoin(withdrawSubmitDO.getCoin());

            //提现数据风控判断 & 出款方式确认
            GlWithdraw glWithdraw = glWithdrawBusiness.doUserWithdraw(withdrawDO, userDO, userCard, usdtAddress, level);

            result.add(glWithdraw);
        }
        return result;
    }

    /**
     * 代理提现
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param proxyConfig
     * @param userCard
     * @param now
     * @return
     * @throws GlobalException
     */
    private List<GlWithdraw> doWithdrawForProxy(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, GlWithdrawProxyConfig proxyConfig,
                                                GlWithdrawUserBankCard userCard, GlWithdrawUserUsdtAddress usdtAddress, Date now, Integer withdrawType) throws GlobalException {
        List<GlWithdraw> result = new ArrayList<>();
        // 本次提现总金额
        BigDecimal amount = withdrawSubmitDO.getAmount().setScale(2, RoundingMode.DOWN);
        //拆单金额
        BigDecimal splitAmount = BigDecimal.ZERO;
        //拆单随机金额
        BigDecimal randomAmount = BigDecimal.ZERO;

        //拆单次数
        int splitCount = 0;
        //数字货币不拆单
        if (withdrawType == 1) {
            for (GlWithdrawSplitRule item : proxyConfig.getSplitRuleList()) {
                // 获取拆单配置信息，计算拆单次数
                if (amount.compareTo(item.getMinAmount()) == 1
                        && amount.compareTo(item.getMaxAmount()) != 1) {
                    splitAmount = item.getSplitAmount();
                    randomAmount = item.getRandomAmount();
                    splitCount = (int) Math.ceil(amount.divide(splitAmount, 1, BigDecimal.ROUND_UP).doubleValue());
                }
            }
        }
        // 提现金额拆分后
        List<BigDecimal> withdrawAmount = new ArrayList<>();
        double moneyMin = 0.01;
        Random random = new Random();
        NumberFormat formatter = new DecimalFormat("#.##");

        // 标识本次提现是否拆单（0-未拆单、1-拆单）
        Integer splitStatus = 0;
        if (splitCount > 0) {
            splitStatus = 1;
            for (int i = 1; i <= splitCount; i++) {
                BigDecimal money = new BigDecimal(formatter.format(random.nextDouble() * (randomAmount.doubleValue() - moneyMin) + moneyMin)).setScale(0, RoundingMode.DOWN);
                // 已拆分金额
                BigDecimal sumAmount = withdrawAmount.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
                // 临界值拆分 随机值 + 拆分值 不能大于提现金额
                if (money.add(splitAmount).compareTo(amount) == 1 && i == 1) {
                    withdrawAmount.add(amount);
                } else {
                    // 本次拆分金额
                    BigDecimal splitMoney = money.add(splitAmount);
                    if (money.add(sumAmount).compareTo(amount) == 1 || splitMoney.add(sumAmount).compareTo(amount) == 1) {
                        splitMoney = amount.subtract(sumAmount);
                    }
                    if (splitMoney.compareTo(BigDecimal.ZERO) == 1) {
                        withdrawAmount.add(splitMoney);
                    }
                }
            }
        } else {
            withdrawAmount.add(amount);
        }

        int index = withdrawAmount.size();
        if (index != 1) {
            // 拆分的最后一笔金额  不能小于提现最低限额
            if (withdrawAmount.get(index - 1).compareTo(proxyConfig.getMinLimit()) == -1) {
                withdrawAmount.set(index - 2, withdrawAmount.get(index - 1).add(withdrawAmount.get(index - 2)));
                withdrawAmount.remove(index - 1);
            }
        }
        //会员层级数据
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        //计算拆单批次号
        Integer splitTotalCount = glWithdrawSplitBusiness.getTodayWithdrawSplitCount(userDO.getId(), DateUtils.getStartOfDay(now), DateUtils.getEndOfDay(now));
        //计算手续费
        for (int i = 0; i < withdrawAmount.size(); i++) {
            String batchNumber = null;

            if (splitStatus == 1) {
                batchNumber = (splitTotalCount + 1) + "-" + (withdrawAmount.size()) + "-" + (i + 1);
            }

            BigDecimal withdrawFee = BigDecimal.ZERO;
            //计算手续费
            if ("fix".equals(proxyConfig.getFeeType())) {
                withdrawFee = proxyConfig.getFee();
            } else {
                withdrawFee = withdrawAmount.get(i).multiply(proxyConfig.getFee()).divide(BigDecimal.valueOf(100));
            }

            //用户提现记录
            GlWithdrawDO withdrawDO = new GlWithdrawDO();
            withdrawDO.setAmount(withdrawAmount.get(i));
            withdrawDO.setFee(withdrawFee);
            withdrawDO.setClientType(withdrawSubmitDO.getHeaderOsType());
            withdrawDO.setCreateDate(now);
            withdrawDO.setLastUpdate(now);
            withdrawDO.setFreeStatus(1);
            withdrawDO.setAisleType(3);
            withdrawDO.setSplitStatus(splitStatus);
            withdrawDO.setBatchNumber(batchNumber);
            withdrawDO.setIp(withdrawSubmitDO.getRequestIp());
            withdrawDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
            withdrawDO.setTotalAmount(amount);
            withdrawDO.setCoin(withdrawSubmitDO.getCoin());

            //提现数据风控判断 & 出款方式确认
            GlWithdraw glWithdraw = glWithdrawBusiness.doUserWithdraw(withdrawDO, userDO, userCard, usdtAddress, level);

            result.add(glWithdraw);
        }
        return result;
    }

    /**
     * 极速提现
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param config
     * @param userCard
     * @param now
     * @return
     * @throws GlobalException
     */
    private GlWithdraw doWithdrawForCToC(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, C2CConfigDO config,
                                         GlWithdrawUserBankCard userCard, GlWithdrawUserUsdtAddress usdtAddress, Date now) throws GlobalException {
        // 本次提现总金额
        BigDecimal amount = withdrawSubmitDO.getAmount().setScale(2, RoundingMode.DOWN);
        // 提现保证金（手续费）
        BigDecimal withdrawFee = withdrawSubmitDO.getAmount().subtract(withdrawSubmitDO.getArrivalAmount());

        //用户提现记录
        GlWithdrawDO withdrawDO = new GlWithdrawDO();
        withdrawDO.setAmount(amount);
        withdrawDO.setFee(withdrawFee);
        withdrawDO.setClientType(withdrawSubmitDO.getHeaderOsType());
        withdrawDO.setCreateDate(now);
        withdrawDO.setLastUpdate(now);
        withdrawDO.setFreeStatus(1);
        withdrawDO.setAisleType(4);
        withdrawDO.setSplitStatus(0);
        withdrawDO.setBatchNumber(null);
        withdrawDO.setIp(withdrawSubmitDO.getRequestIp());
        withdrawDO.setDeviceId(withdrawSubmitDO.getHeaderDeviceId());
        withdrawDO.setTotalAmount(amount);
        withdrawDO.setCoin(withdrawSubmitDO.getCoin());

        //会员层级数据
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());

        //提现数据风控判断 & 出款方式确认
        GlWithdraw glWithdraw = glWithdrawBusiness.doUserWithdraw(withdrawDO, userDO, userCard, usdtAddress, level);

        return glWithdraw;
    }

    private void checkBlackMonitor(GlUserDO userDO, List<String> bankCards, String ip, Integer behavior) {
        //V2 黑名单需求添加 2.异步校验是否监控
        BlackMonitorDO monitorDO = new BlackMonitorDO();
        monitorDO.setUser(userDO);
        monitorDO.setBankCards(bankCards);
        monitorDO.setRegisterIp(null);
        monitorDO.setLoginIp(ip);
        monitorDO.setBehaviorType(behavior);
        monitorDO.setOrderId(null);
        monitorDO.setAmount(null);
        blackService.checkBlackMonitor(monitorDO);
    }

    @Override
    public GlWithdrawDetailResult withdrawDetail(GlUserDO userDO, String orderId) throws GlobalException {
        GlWithdrawDetailResult result = new GlWithdrawDetailResult();

        GlWithdraw withdraw = glWithdrawBusiness.findById(orderId);
        if (withdraw == null || !withdraw.getUserId().equals(userDO.getId())) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "提现记录不存在");
        }
        result.setName(withdraw.getName());
        result.setAmount(withdraw.getAmount());
        result.setBankName(withdraw.getBankName());
        result.setCardNo(StringEncryptor.encryptBankCard(withdraw.getCardNo()));
        result.setOrderId(withdraw.getOrderId());
        result.setStatus(withdraw.getStatus());
        result.setAddress(withdraw.getAddress());
        result.setCreateDate(withdraw.getCreateDate());
        result.setFee(withdraw.getFee());
        result.setLastUpdate(withdraw.getLastUpdate());
        return result;
    }

    @Override
    public WithdrawResult getLastWithdrawDetail(GlUserDO userDO) throws GlobalException {
        WithdrawResult resultData = new WithdrawResult();

        List<GlWithdrawDetailResult> withdrawDetailResultList = new ArrayList<>();
        if (UserConstant.Type.PLAYER == userDO.getUserType()) {// 只有会员才进行手机号和银行卡绑定校验
            resultData.setTelephone(userDO.getTelephone());

            // 开关
            String onOff = redisService.get(RedisKeyHelper.CHECKED_USER_INFO_ONOFF);
            if (checkWhiteList(userDO.getId())) {
                resultData.setOnOff("0");
            } else {
                resultData.setOnOff(ObjectUtils.isEmpty(onOff) ? "0" : onOff);
            }

            // 银行卡信息
            List<GlWithdrawUserBankCard> glWithdrawUserBankCards = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
            for (GlWithdrawUserBankCard tempCard : glWithdrawUserBankCards) {
                tempCard.setName(StringEncryptor.encryptUsername(tempCard.getName()));
            }
            resultData.setBankCards(glWithdrawUserBankCards);
        }

        List<GlWithdraw> glWithdrawList = glWithdrawBusiness.getLastWithdrawList(userDO.getId());
        if (ObjectUtils.isEmpty(glWithdrawList)) {
            return resultData;
        }

        for (GlWithdraw glWithdraw : glWithdrawList) {
            GlWithdrawDetailResult result = new GlWithdrawDetailResult();
            result.setOrderId(glWithdraw.getOrderId());
            result.setStatus(glWithdraw.getStatus() == -4 ? -3 : glWithdraw.getStatus());//兼容审核搁置状态-4转换为待审核-3
            result.setAmount(glWithdraw.getAmount());
            result.setCoin(glWithdraw.getCoin());
            result.setCreateDate(glWithdraw.getCreateDate());
            result.setFee(glWithdraw.getFee());
            result.setLastUpdate(glWithdraw.getLastUpdate());
            result.setType(glWithdraw.getAisleType());

            if (glWithdraw.getBankId() == FundConstant.PaymentType.DIGITAL_PAY) {//USDT提现
                resultData.setShowType("DIGITAL");
                result.setShowType("DIGITAL");
                result.setNickName(glWithdraw.getName());
                result.setProtocol(glWithdraw.getCardNo());
                result.setUsdtAddress(glWithdraw.getAddress());
                result.setRate(BigDecimal.ZERO);
                result.setUsdtAmount(BigDecimal.ZERO);
            } else {
                resultData.setShowType("BANK");
                result.setShowType("BANK");
                result.setName(glWithdraw.getName());
                result.setBankName(glWithdraw.getBankName());
                result.setCardNo(StringEncryptor.encryptBankCard(glWithdraw.getCardNo()));
                result.setAddress(glWithdraw.getAddress());
            }
            GlWithdrawMerchantAccount merchantAccount = glWithdrawMerchantAccountBusiness.findById(glWithdraw.getMerchantId());
            if (merchantAccount != null && merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
                C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
                if (glWithdraw.getStatus() == FundConstant.WithdrawStatus.CONFIRM_PENDING) {
                    String key = String.format(KeyConstant.C2C.C2C_WITHDRAW_TTL, glWithdraw.getOrderId());
                    log.info("key:{}",key);
                    result.setExpiredTtl(redisService.getTTL(key));
                }
                result.setAlertTime(org.apache.commons.lang3.time.DateUtils.addMinutes(glWithdraw.getLastUpdate(), configDO.getWithdrawReceiveConfirmAlertTime()));
                C2COrderDetailResult detailResult = c2COrderHandler.getByWithdrawOrderId(glWithdraw.getOrderId(), glWithdraw.getThirdOrderId());
                Optional.ofNullable(detailResult).ifPresent(obj -> {
                    result.setPaymentDate(obj.getPaymentDate());
                });
                result.setReceiveTimeout(configDO.getWithdrawReceiveConfirmAlertTimeout());
            }
            //提现活动奖励金额
            RPCResponse<BigDecimal> response =
                    rechargeWithdrawTempleService.withdrawRate(FundConstant.PaymentChannel.C2CPay, userDO.getVipLevel() == null ? 0 : userDO.getVipLevel());
            if (ObjectUtils.isNotEmpty(response) && ObjectUtils.isNotEmpty(response.getData())) {
                result.setAwardAmount(response.getData().multiply(glWithdraw.getAmount()).setScale(0,RoundingMode.DOWN));
            }
            withdrawDetailResultList.add(result);
        }
        resultData.setWithdraws(withdrawDetailResultList);
        return resultData;
    }


    @Override
    public WithdrawNotifyResponse withdrawNotify(Integer merchantId, HttpServletRequest request, HttpServletResponse response) throws GlobalException {
        Map<String, String[]> params = request.getParameterMap();
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<String, String[]> each : params.entrySet()) {
            resMap.put(each.getKey(), each.getValue()[0]);
        }
        WithdrawNotifyResponse notify = new WithdrawNotifyResponse();
        try {
            String body = GlRequestUtil.inputStream2String(request.getInputStream());
            resMap.put("reqBody", body);
            log.info("withdraw_notification: {}", JSON.toJSONString(resMap));
            GlWithdrawMerchantAccount merchant = glWithdrawMerchantAccountBusiness.findById(merchantId);
            if (merchant == null) {
                notify.setContent("invalid payment");
                return notify;
            }
            WithdrawNotify withdrawNotify = glWithdrawBusiness.doWithdrawNotify(merchant, resMap);
            if (withdrawNotify == null || withdrawNotify.getStatus() == 2) {
                log.warn("withdraw_notify_error: {}", null != withdrawNotify ? JSON.toJSONString(withdrawNotify) : JSON.toJSONString(resMap));
                notify.setContent("FAILED");
                return notify;
            }
            // 提交到账事务
            glWithdrawTransactionalBusiness.doWithdrawNofity(withdrawNotify);
            // 回调响应信息
            if (StringUtils.isNotBlank(withdrawNotify.getRsp())) {
                notify.setContent(withdrawNotify.getRsp());
            } else {
                String message = glWithdrawHandlerManager.withdrawOKNotifyResponse(merchant.getChannelId());
                notify.setContent(message);
            }
            return notify;
        } catch (Exception e) {
            log.error("withdrawNotify_error:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        }
    }


    @Override
    public WithdrawNotifyResponse notifyForStormPay(Integer merchantId, HttpServletRequest request) throws GlobalException {
        Map<String, String[]> params = request.getParameterMap();
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<String, String[]> each : params.entrySet()) {
            resMap.put(each.getKey(), each.getValue()[0]);
        }
        try {
            WithdrawNotifyResponse notify = new WithdrawNotifyResponse();
            String headSign = request.getHeader("Content-Hmac");
            String body = GlRequestUtil.inputStream2String(request.getInputStream());
            resMap.put("reqBody", body);
            resMap.put("headSign", headSign);
            JSONObject json = JSON.parseObject(resMap.get("reqBody"));
            log.info("notifyForStormPay_notify_json:{}", json);
            if (null == json) {
                notify.setContent("param error");
                return notify;
            }
            // direction = (in:充值回调、out:提现回调)
            String direction = json.getString("direction");
            if (StringUtils.isEmpty(direction)) {
                notify.setContent("direction error");
                return notify;
            }
            /**
             * 提现订单回调
             */
            if (direction.equals("out")) {

                GlWithdrawMerchantAccount merchant = glWithdrawMerchantAccountBusiness.findById(merchantId);
                if (merchant == null) {
                    notify.setContent("invalid payment");
                    return notify;
                }

                WithdrawNotify withdrawNotify = glWithdrawBusiness.doWithdrawNotify(merchant, resMap);
                if (withdrawNotify == null || withdrawNotify.getStatus() == 2) {
                    log.error("withdraw notify error: {}", JSON.toJSONString(resMap));
                    notify.setContent("FAILED");
                    return notify;
                }
                // 提交到账事务
                glWithdrawTransactionalBusiness.doWithdrawNofity(withdrawNotify);
                notify.setContent("true");
                return notify;
            }
        } catch (Exception e) {
            log.error("notifyForTransfer_error:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Boolean withdrawConfirm(WithdrawConfirmDto confirm) {
        GlWithdraw withdraw = glWithdrawBusiness.findById(confirm.getOrderId());
        if (withdraw != null) {
            if (withdraw.getAmount().subtract(withdraw.getFee()).compareTo(confirm.getAmount()) != 0 ||
                    withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_PENDING) {
                return false;
            }
            GlWithdrawUserBankCard userBankCard = glWithdrawUserBankCardBusiness.findByCardNo(withdraw.getCardNo());
            if (null == userBankCard || userBankCard.getUserId().intValue() != withdraw.getUserId()
                    || !userBankCard.getCardNo().substring(userBankCard.getCardNo().length() - 4).equals(confirm.getCardNo())
                    || !userBankCard.getName().equals(confirm.getName())) {
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * 三方出款订单查询商户后台状态
     * 步骤：
     * *  1.校验 避免重复
     * *  2.从三方查询订单回调，如果成功或失败，执行相应的逻辑
     *
     * @param orderId
     * @return
     * @throws GlobalException
     */
    @Override
    public WithdrawNotify withdrawStatusConfirm(String orderId) throws GlobalException {
        if (StringUtils.isEmpty(orderId)) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "订单号为空");
        }
        GlWithdraw glWithdraw = glWithdrawBusiness.findById(orderId);
        //避免重复订单
        if (glWithdraw == null || glWithdraw.getStatus().equals(FundConstant.WithdrawStatus.SUCCESS)) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "订单已成功到账或者不存在");
        }
        if (!glWithdraw.getCoin().equals(DigitalCoinEnum.CNY.getCode())) {
            WithdrawNotify notify =  new WithdrawNotify();
            notify.setOrderId(glWithdraw.getOrderId());
            notify.setMerchantCode(glWithdraw.getMerchantCode());
            notify.setMerchantId(glWithdraw.getMerchantId());
            notify.setMerchantName(glWithdraw.getMerchant());
            notify.setStatus(2);
            return notify;
        }

        if (null == glWithdraw.getMerchantId()) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "出款商户异常");
        }

        GlWithdrawMerchantAccount merchantAccount = glWithdrawMerchantAccountBusiness.findById(glWithdraw.getMerchantId());
        if (null == merchantAccount) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "出款商户不存在");
        }
        //风云聚合
        if (merchantAccount.getChannelId().equals(FundConstant.PaymentChannel.STORMPAY)) {
            orderId = orderId.substring(2);
        }
        if (merchantAccount.getChannelId().equals(FundConstant.PaymentChannel.MACHI)) {
            orderId = orderId + "-" + orderId.substring(2, 10);
        }
        WithdrawNotify notify = glWithdrawBusiness.artificialDoWithdrawQuery(merchantAccount, orderId);
        if (notify == null) {
            throw new GlobalException(ResultCode.DATA_ERROR, "三方查询异常，请稍后再试");
        }
        // 提交到账事务
        if (notify.getStatus() == 0 || notify.getStatus() == 1) {//明确成功/失败的提交到账事务
            try {
                glWithdrawTransactionalBusiness.doWithdrawNofity(notify);
            } catch (GlobalException e) {
                log.debug(e.getExtraMessage());
            }
        }
        return notify;
    }

    @Override
    public Result loadUsdtRate(GlUserDO userDO) {
        Result.Builder newBuilder = Result.newBuilder();
//        String rate = null;
//        if (UserConstant.UserType.PLAYER == userDO.getUserType()) {
//            rate = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE);
//        } else if (UserConstant.UserType.PROXY == userDO.getUserType()) {
//            rate = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE_PROXY);
//        }
//        if (StringUtils.isEmpty(rate)) {
//            return newBuilder.fail().setMessage("汇率未配置").build();
//        }
//        BigDecimal result = StringUtils.isNotEmpty(rate) ? new BigDecimal(rate).setScale(4, RoundingMode.DOWN) : BigDecimal.valueOf(6.8945);
        return newBuilder.success().addData(glWithdrawBusiness.getWithdrawRate(userDO.getUserType())).build();
    }

    @Override
    public void reSynchronize(String... orderIds) {
        try {
            for (String orderId : orderIds) {
                GlWithdraw withdraw = glWithdrawBusiness.findById(orderId);
                if (org.springframework.util.ObjectUtils.isEmpty(withdraw)) {
                    log.error("交易单号{}在提现记录表中未查询到数据", orderId);
                    continue;
                }
                log.info("提现处理中数据修复：正在处理单号{}", orderId);
                WithdrawReport report = new WithdrawReport();
                report.setUuid(withdraw.getOrderId());
                report.setCreateTime(withdraw.getCreateDate());
                report.setFinishTime(withdraw.getCreateDate());
                report.setTimestamp(withdraw.getCreateDate());
                report.setOrderId(withdraw.getOrderId());
                report.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setFee(withdraw.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setStatus(WithdrawStatusEnum.valueOf(withdraw.getStatus()));
                report.setApproveTime(withdraw.getApproveTime());
                report.setUid(withdraw.getUserId());
                report.setLastUpdate(withdraw.getLastUpdate());
                reportService.withdrawReport(report);
                log.debug("结束-重新上报的订单号是{}", orderId);
            }
        } catch (Exception ex) {
            log.error("重新同步提现订单发生异常", ex);
        }
    }

}
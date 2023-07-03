package com.seektop.fund.handler.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.seektop.agent.service.CommCommissionService;
import com.seektop.common.http.HttpUtils;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.GlRequestUtil;
import com.seektop.common.utils.OrderPrefix;
import com.seektop.common.utils.RegexValidator;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.data.result.recharge.RechargeRecordDetailDO;
import com.seektop.data.service.RechargeService;
import com.seektop.data.service.UserService;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.PlatformEnum;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.digital.DigitalProtocolEnum;
import com.seektop.enumerate.fund.BankEnum;
import com.seektop.enumerate.fund.RechargePaymentEnum;
import com.seektop.enumerate.fund.RechargeStatusEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.GlPaymentUserCardBusiness;
import com.seektop.fund.business.recharge.*;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.common.C2COrderDetailResult;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeCommonAmountDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeCreateDO;
import com.seektop.fund.controller.backend.result.GlPaymentMerchantResult;
import com.seektop.fund.controller.backend.result.GlPaymentNewResult;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.forehead.param.recharge.*;
import com.seektop.fund.controller.forehead.result.RechargeBankInfo;
import com.seektop.fund.controller.forehead.result.RechargeDigitalInfo;
import com.seektop.fund.controller.forehead.result.RechargeInfoResult;
import com.seektop.fund.controller.forehead.result.RechargeSettingResult;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.enums.UseModeEnum;
import com.seektop.fund.handler.C2COrderHandler;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.handler.ReportExtendHandler;
import com.seektop.fund.handler.event.OrderNotifyEvent;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.mapper.GlRechargeReceiveInfoMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import com.seektop.fund.payment.niubipay.PaymentInfo;
import com.seektop.report.fund.RechargeReport;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component("rechargeHandler")
public class RechargeHandlerImpl implements RechargeHandler {

    private static DecimalFormat decimalFormat = new DecimalFormat("0.00");

    @Resource
    private RedisService redisService;
    @Resource
    private GlRechargeHandlerManager glRechargeHandlerManager;
    @Resource
    private GlRechargeBusiness glRechargeBusiness;
    @Resource
    private GlRechargePayBusiness glRechargePayBusiness;
    @Resource
    private GlPaymentBusiness glPaymentBusiness;
    @Resource
    private GlPaymentUserCardBusiness glPaymentUserCardBusiness;
    @Resource
    private ReportService reportService;
    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;
    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;
    @Resource
    private GlPaymentMerchantFeeBusiness glPaymentMerchantFeeBusiness;
    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantaccountBusiness;
    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;
    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Resource
    private ReportExtendHandler reportExtendHandler;
    @Resource
    private GlRechargeTransactionBusiness glRechargeTransactionBusiness;

    @DubboReference(retries = 3, timeout = 3000)
    private GlUserService glUserService;

    @DubboReference(retries = 3, timeout = 3000)
    private RechargeService rechargeService;

    @DubboReference(retries = 2, timeout = 3000)
    private CommCommissionService commCommissionService;

    @Value("${check.user.bank.whitelist:}")
    private String whitelistCfg;

    @Resource
    private GlRechargeReceiveInfoMapper glRechargeReceiveInfoMapper;
    @Autowired
    private ApplicationContext applicationContext;

    @Resource
    private UserVipUtils userVipUtils;

    @Resource
    private GlRechargeMapper glRechargeMapper;

    @Resource(name = "c2CPaymentIds")
    private List<Integer> c2CPaymentIds;

    @DubboReference(timeout = 60000 , retries = 1)
    private UserService userService;

    @Resource
    private C2COrderHandler c2COrderHandler;

    /**
     * 清除用户充值使用姓名
     *
     * @param userDO
     * @throws GlobalException
     */
    @Override
    public void clearName(GlUserDO userDO) throws GlobalException {
        glPaymentUserCardBusiness.deleteByUserId(userDO.getId());
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
     * 获取最后一笔充值记录
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @Override
    public RechargeInfoResult getLastRecharge(GlUserDO userDO) {
        RechargeInfoResult info = new RechargeInfoResult();
        info.setTelephone(userDO.getTelephone());

        // 校验用户是否完善信息功能开关 0-关闭、1-开启
        // 特定层级不需要完善信息
        if (checkWhiteList(userDO.getId())) {
            info.setOnOff("0");
        } else {
            String onOff = redisService.get(RedisKeyHelper.CHECKED_USER_INFO_ONOFF);
            info.setOnOff(ObjectUtils.isEmpty(onOff) ? "0" : onOff);
        }

        // 银行卡信息
        List<GlWithdrawUserBankCard> bankCards = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
        if (!CollectionUtils.isEmpty(bankCards)) {
            bankCards.forEach(c -> {
                c.setName(StringEncryptor.encryptUsername(c.getName()));
                c.setCardNo(StringEncryptor.encryptBankCard(c.getCardNo()));
            });
        }
        info.setBankCards(bankCards);

        // 获取用户最后一条订单记录，按照时间排序只获取最新的一条
        GlRecharge lastRecharge = glRechargeBusiness.getLastRecharge(userDO.getId());
        GlRechargeDO result = DtoUtils.transformBean(lastRecharge, GlRechargeDO.class);
        // 包含30分钟内未支付的订单、充值待审核订单、极速充值待支付订单
        if (result != null) {
            if ((result.getStatus() == FundConstant.RechargeStatus.PENDING
                    && result.getCreateDate().after(DateUtils.addMinutes(new Date(), -35)))
                    //极速充值
                    || (result.getStatus() == FundConstant.RechargeStatus.PENDING
                    && result.getChannelId() == FundConstant.PaymentChannel.C2CPay)
                    //补单审核
                    || result.getStatus() == FundConstant.RechargeStatus.REVIEW) {

                info.setOrderId(lastRecharge.getOrderId());
                info.setExistRecharge(true);
                info.setCoin(lastRecharge.getCoin());
                info.setAmount(lastRecharge.getAmount());
                info.setPaymentId(lastRecharge.getPaymentId());
                info.setPaymentName(FundConstant.paymentTypeMap.get(lastRecharge.getPaymentId()));
                info.setStatus(lastRecharge.getStatus());
                info.setShowType("NORMAL");
                info.setCreateDate(lastRecharge.getCreateDate());
                info.setExpiredDate(DateUtils.addMinutes(lastRecharge.getCreateDate(), 30));
                info.setAgentType(lastRecharge.getAgentType());
                info.setSubStatus(lastRecharge.getSubStatus());
                GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(lastRecharge.getOrderId());
                if (null != receiveInfo) {
                    info.setAmount(receiveInfo.getAmount());
                    info.setShowType(receiveInfo.getShowType());
                    info.setExpiredDate(receiveInfo.getExpiredDate());
                    if (receiveInfo.getShowType().equals("DETAIL")) { //显示收款账户详情
                        RechargeBankInfo bankInfo = new RechargeBankInfo();
                        bankInfo.setOrderId(lastRecharge.getOrderId());
                        bankInfo.setOwner(receiveInfo.getOwner());
                        bankInfo.setBankcardId(receiveInfo.getBankcardId());
                        bankInfo.setBankcardName(receiveInfo.getBankcardName());
                        bankInfo.setBankcardBranch(receiveInfo.getBankcardBranch());
                        bankInfo.setBankcardNo(receiveInfo.getBankcardNo());
                        bankInfo.setKeyword(receiveInfo.getKeyword());
                        if (lastRecharge.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
                            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
                            Date expiredDate = DateUtils.addMinutes(lastRecharge.getCreateDate(), configDO.getRechargePaymentTimeout());
                            info.setExpiredDate(expiredDate);
                            String key = String.format(KeyConstant.C2C.C2C_RECHARGE_TTL,lastRecharge.getOrderId());
                            info.setExpiredTtl(redisService.getTTL(key));
                            info.setAlertTime(DateUtils.addMinutes(lastRecharge.getCreateDate(), configDO.getRechargeAlertTime()));
                            Optional.ofNullable(lastRecharge.getKeyword()).ifPresent(obj -> {
                                String[] arr = obj.split("||");
                                bankInfo.setKeyword(arr == null || arr.length != 2 ? "" : arr[1]);

                            });
                            C2COrderDetailResult detailResult = c2COrderHandler.getByRechargeOrderId(lastRecharge.getOrderId());
                            Optional.ofNullable(detailResult).ifPresent(obj -> {
                                info.setPaymentDate(obj.getPaymentDate());
                            });
                        }
                        info.setBankInfo(bankInfo);
                    } else if (receiveInfo.getShowType().equals("DIGITAL")) { //显示数字货币收款详情
                        RechargeDigitalInfo digitalInfo = new RechargeDigitalInfo();
                        digitalInfo.setOrderId(lastRecharge.getOrderId());
                        digitalInfo.setOwner(receiveInfo.getOwner());
                        digitalInfo.setAmount(receiveInfo.getAmount());
                        digitalInfo.setDigitalAmount(receiveInfo.getDigitalAmount());
                        DigitalProtocolEnum protocolEnum = DigitalProtocolEnum.getDigitalProtocol(receiveInfo.getProtocol());
                        digitalInfo.setProtocol(ObjectUtils.isEmpty(protocolEnum) ? receiveInfo.getProtocol() : protocolEnum.getName());
                        digitalInfo.setBlockAddress(receiveInfo.getBlockAddress());
                        digitalInfo.setRate(receiveInfo.getRate());
                        info.setDigitalInfo(digitalInfo);
                    }

                }
            }
        }
        return info;
    }


    /**
     * 用户撤销充值订单
     *
     * @param userDO
     * @param orderId
     * @throws GlobalException
     */
    @Override
    public void doCancel(GlUserDO userDO, String orderId) throws GlobalException {
        GlRecharge glRecharge = glRechargeBusiness.findById(orderId);
        if (glRecharge == null || !glRecharge.getUserId().equals(userDO.getId())) {
            throw new GlobalException(ResultCode.DATA_ERROR, "订单异常请联系客服.");
        }
        // 充值订单状态处理
        glRechargeTransactionBusiness.doRechargeCancel(orderId);
        // 调用三方撤销订单接口
        glRechargeBusiness.doRechargeCancel(glRecharge);
    }

    /**
     * 获取充值渠道 PaymentInfo
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @Override
    public GlPaymentNewResult paymentInfo(RechargePaymentInfoDO paymentInfoDO, GlUserDO userDO) throws GlobalException {
        // 用户层级
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        userDO.setUserFundLevelId(level.getLevelId());
        Optional.ofNullable(userVipUtils.getUserVIPCache(userDO.getId())).ifPresent(obj -> {
            userDO.setVipLevel(obj.getVipLevel());
        });
        GlPaymentNewResult result = new GlPaymentNewResult();

        //用户充值渠道失败记录
        RedisResult<Integer> failList = redisService.getListResult(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + userDO.getId(), Integer.class);

        List<GlPaymentResult> normal = null;
        List<GlPaymentResult> large = null;
        // 遍历普通&大额渠道
        for (Integer limitType : FundConstant.PAYMENT_CACHE_LIST) {
            if (limitType == FundConstant.PaymentCache.NORMAL) {
                normal = glPaymentMerchantAppBusiness.getPaymentCache(level.getLevelId(),
                        paymentInfoDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
                if (!ObjectUtils.isEmpty(normal)) {
                    normal = this.settingPaymentInfo(normal, failList, paymentInfoDO, userDO);
                }
            } else if (limitType == FundConstant.PaymentCache.LARGE) {
                large = glPaymentMerchantAppBusiness.getPaymentLargeCache(level.getLevelId(),
                        paymentInfoDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
                if (!ObjectUtils.isEmpty(large)) {
                    large = this.settingPaymentInfo(large, failList, paymentInfoDO, userDO);
                }
                // 大额充值
                if (!ObjectUtils.isEmpty(large)) {
                    large.forEach(r -> r.setLimitType(FundConstant.PaymentCache.LARGE));
                }
            }
        }
        // 排序
        glPaymentBusiness.sort(normal);
        glPaymentBusiness.sort(large);
        result.setNormal(normal);
        result.setLarge(large);
        return result;
    }

    /**
     * 获取充值渠道 PaymentInfo
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @Override
    public GlPaymentNewResult proxyPaymentInfo(RechargePaymentInfoDO paymentInfoDO, GlUserDO userDO) throws GlobalException {
        // 用户层级
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        userDO.setUserFundLevelId(level.getLevelId());
        GlPaymentNewResult result = new GlPaymentNewResult();

        //用户充值渠道失败记录
        RedisResult<Integer> failList = redisService.getListResult(RedisKeyHelper.PAYMENT_MERCHANT_APP_FAIL_LIST + userDO.getId(), Integer.class);

        List<GlPaymentResult> large = null;
        // 遍历大额渠道
        large = glPaymentMerchantAppBusiness.getPaymentLargeCache(level.getLevelId(),
                paymentInfoDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
        log.info("large = {}", JSON.toJSONString(large));
        if (!ObjectUtils.isEmpty(large)) {
            large = this.settingPaymentInfo(large, failList, paymentInfoDO, userDO);
        }
        // 大额充值
        if (!ObjectUtils.isEmpty(large)) {
            large.forEach(r -> r.setLimitType(FundConstant.PaymentCache.LARGE));
        }
        result.setLarge(large);
        return result;
    }


    private List<GlPaymentResult> settingPaymentInfo(List<GlPaymentResult> paymentCacheList,
                                                     RedisResult<Integer> failList,
                                                     RechargePaymentInfoDO paymentInfoDO, GlUserDO userDO) throws GlobalException {

        // 充值渠道要求设置:卡号、姓名
        for (GlPaymentResult payment : paymentCacheList) {
            glRechargeHandlerManager.paymentSetting(payment);
        }
        //充值渠道最小值&最大值
        BigDecimal minAmount = BigDecimal.ZERO;
        BigDecimal maxAmount = BigDecimal.ZERO;

        List<GlPaymentResult> results = new ArrayList<>();
        List<GlPaymentResult> thridPayment = new ArrayList<>();

        for (GlPaymentResult paymentResult : paymentCacheList) {

            if (userDO.getUserType() == UserConstant.UserType.PROXY) {
                log.info("paymentId = {}", paymentResult.getPaymentName());
                if (!paymentResult.getPaymentId().equals(FundConstant.PaymentType.BANKCARD_TRANSFER) && !paymentResult.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {
                    continue;
                }
            }
            //是否按照充值金额过滤渠道
            if (paymentInfoDO.getAmountFilter()) {
                List<GlPaymentMerchantResult> newArrayList = Lists.newArrayList();
                for (GlPaymentMerchantResult payment : paymentResult.getMerchantList()) {
                    if (null != payment.getMinAmount() && null != payment.getMaxAmount()) {
                        // 1.符合充值金额的通道
                        if (paymentInfoDO.getAmount().compareTo(payment.getMinAmount()) != -1 && paymentInfoDO.getAmount().compareTo(payment.getMaxAmount()) != 1) {
                            newArrayList.add(payment);
                        } else {
                            // 充值金额小于最低值
                            if (paymentInfoDO.getAmount().compareTo(payment.getMinAmount()) == -1) {
                                if (minAmount.compareTo(BigDecimal.ZERO) == 0) {
                                    minAmount = payment.getMinAmount();
                                } else {
                                    minAmount = minAmount.compareTo(payment.getMinAmount()) == -1 ? minAmount : payment.getMinAmount();
                                }
                            }
                            // 充值金额大于最高值
                            if (paymentInfoDO.getAmount().compareTo(payment.getMaxAmount()) == 1) {
                                if (maxAmount.compareTo(BigDecimal.ZERO) == 0) {
                                    maxAmount = payment.getMaxAmount();
                                } else {
                                    maxAmount = maxAmount.compareTo(payment.getMaxAmount()) == 1 ? maxAmount : payment.getMaxAmount();
                                }
                            }
                        }
                    }
                }
                // 该充值方式没有符合金额范围的充值通道
                if (ObjectUtils.isEmpty(newArrayList)) {
                    continue;
                }
                paymentResult.setMerchantList(newArrayList);
            }
            // 银行卡转账放置第一位
            if (glRechargeHandlerManager.isBankcardTransfer(paymentResult.getPaymentId())) {
                results.add(paymentResult);
            } else {
                thridPayment.add(paymentResult);
            }
        }
        // 银行卡转账排在最前面
        results.addAll(thridPayment);

        // 默认过滤极速类型支付渠道
        if (paymentInfoDO.getQuickFilter()) {
            results = results.stream().filter(v -> !glRechargeHandlerManager.isQuickPay(v.getPaymentId())).collect(Collectors.toList());
        }

        //充值渠道轮替-> 通道排序
        results = glRechargeBusiness.rechargeRotation(results, userDO, paymentInfoDO);
        if (ObjectUtils.isEmpty(results)) {
            String message = null;
            if (minAmount.compareTo(BigDecimal.ZERO) != 0) {
                message = String.format("单笔最小充值金额为 %s 元", minAmount.setScale(0).toString());
                throw new GlobalException(ResultCode.DATA_ERROR, message);
            }
            if (maxAmount.compareTo(BigDecimal.ZERO) != 0) {
                message = String.format("单笔最大充值金额为 %s 元", maxAmount.setScale(0).toString());
                throw new GlobalException(ResultCode.DATA_ERROR, message);
            }
        }
        return results;
    }


    /**
     * 获取快捷金额设置
     *
     * @param paramBaseDO
     * @param userDO
     * @return
     */
    @Override
    public RechargeSettingResult getFastAmountList(ParamBaseDO paramBaseDO, GlUserDO userDO) {

        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        //普通渠道快捷金额

        RechargeCommonAmountDO normalCommon = redisService.get(RedisKeyHelper.PAYMENT_NORMAL_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        RechargeCommonAmountDO largeCommon = redisService.get(RedisKeyHelper.PAYMENT_LARGE_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        RechargeCommonAmountDO proxyCommon = redisService.get(RedisKeyHelper.PAYMENT_PROXY_COMMON_AMOUNT, RechargeCommonAmountDO.class);
        if (ObjectUtils.isEmpty(normalCommon) || ObjectUtils.isEmpty(largeCommon) || ObjectUtils.isEmpty(proxyCommon)) {
            log.error("redis获取通用金额设置异常");
        }

        Boolean showMerchant = true;
        String setting = redisService.get(RedisKeyHelper.SHOW_MERCHANT_SETTING);
        if (StringUtils.isNotEmpty(setting) && setting.equals("false")) {
            showMerchant = false;
        }
        //是否显示大额充值渠道
        Boolean largeMerchant = true;
        List<GlPaymentResult> paymentCacheList = glPaymentMerchantAppBusiness.getPaymentLargeCache(level.getLevelId(),
                paramBaseDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
        if (ObjectUtils.isEmpty(paymentCacheList)) {
            largeMerchant = false;
        }

        RechargeSettingResult result = new RechargeSettingResult();

        result.setShowMerchant(showMerchant);
        result.setLargeMerchant(largeMerchant);

        if (userDO.getUserType() == UserConstant.Type.PROXY) {
            result.setProxy(proxyCommon.getAmounts());
        } else {
            result.setCommon(normalCommon.getAmounts());
            result.setLarge(largeCommon.getAmounts());
        }
        return result;
    }


    /**
     * 提交充值
     *
     * @param rechargeSubmitDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public GlRechargeResult doRechargeSubmit(RechargeSubmitDO rechargeSubmitDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException {
        GlRechargeResult result = new GlRechargeResult();
        /**
         * 1.充值数据校验
         */

        // 虚拟用户直接抛出
        if (userDO.getIsFake().equals("1")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("网络故障无法打开页面");
            return result;
        }

        //防止重复提交  ----> 请求频率限制
        String rechargeLock = RedisKeyHelper.BALLBET_RECHARGE_USER_LOCK + userDO.getId();
        if (redisService.incrBy(rechargeLock, 1) > 1) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("操作频率太快,请稍后.");
            return result;
        }
        redisService.setTTL(rechargeLock, 15);

        // 用户账户验证  ----> 非正常状态
        if (userDO.getStatus() == UserConstant.Status.NEW) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("请修改初始密码,保证资金安全.");
            return result;
        }

        if (userDO.getStatus() != UserConstant.Status.NORMAL) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("账号异常,请联系客服.");
            return result;
        }

        //最后一笔充值订单 ----> 是否待审核状态
        GlRecharge lastRecharge = glRechargeBusiness.getLastRecharge(userDO.getId());
        if (null != lastRecharge && (FundConstant.RechargeStatus.PENDING == lastRecharge.getStatus()
                || FundConstant.RechargeStatus.REVIEW == lastRecharge.getStatus())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("账户存在待支付订单：" + lastRecharge.getOrderId());
            return result;
        }

        // 用户手机号码和银行卡绑定验证
        if (UserConstant.Type.PLAYER == userDO.getUserType()) {
            String onOff = redisService.get(RedisKeyHelper.CHECKED_USER_INFO_ONOFF);
            if (StringUtils.isNotEmpty(onOff) && "1".equals(onOff)) {//开关开启
                if (!checkWhiteList(userDO.getId())) {
                    if (StringUtils.isEmpty(userDO.getTelephone())) {
                        result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                        result.setErrorMsg("请完善个人信息：绑定电话号码");
                        return result;
                    }

                    List<GlWithdrawUserBankCard> glWithdrawUserBankCards = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
                    if (ObjectUtils.isEmpty(glWithdrawUserBankCards)) {
                        result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                        result.setErrorMsg("请完善个人信息：绑定银行卡");
                        return result;
                    }
                }

            }
        }

        //充值金额验证  ---->  正整数
        if (new BigDecimal(rechargeSubmitDO.getAmount().intValue()).compareTo(rechargeSubmitDO.getAmount()) == -1) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值金额错误:请输入整数金额");
            return result;
        }
        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        Optional.ofNullable(userVipUtils.getUserVIPCache(userDO.getId())).ifPresent(obj -> {
            userDO.setVipLevel(obj.getVipLevel());
        });

        //根据三方商户应用优化配置选择出款商户应用
        RechargeMerchangAppDO merchangAppDO = DtoUtils.transformBean(rechargeSubmitDO, RechargeMerchangAppDO.class);
        merchangAppDO.setLevelId(level.getLevelId());
        merchangAppDO.setInnerPay(false);
        merchangAppDO.setVipLevel(userDO.getVipLevel());
        merchangAppDO.setUserId(userDO.getId());
        if (FundConstant.AGENT_TYPE != rechargeSubmitDO.getPayType()) {
            Integer merchantAppId = glRechargeBusiness.getMerchantAppId(merchangAppDO);
            if (ObjectUtils.isEmpty(merchantAppId)) {
                log.warn("三方商户应用优化级获取失败 :{} ", JSON.toJSONString(rechargeSubmitDO));
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                result.setErrorMsg("充值失败,请刷新页面重新充值.");
                return result;
            } else {
                rechargeSubmitDO.setMerchantAppId(merchantAppId);
            }
        }


        // 三方商户应用 GlPaymentMerchantApp判断
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.findById(rechargeSubmitDO.getMerchantAppId());

        if (!merchantApp.getCoin().equals(rechargeSubmitDO.getCoin())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("支付方式与币种不符");
            return result;
        }

        if (null == merchantApp || merchantApp.getStatus() != ProjectConstant.CommonStatus.NORMAL
                || merchantApp.getOpenStatus() != ProjectConstant.CommonStatus.NORMAL
                || (merchantApp.getClientType() == ProjectConstant.ClientType.PC && rechargeSubmitDO.getHeaderOsType() != ProjectConstant.OSType.PC)
                || (merchantApp.getClientType() == ProjectConstant.ClientType.APP && rechargeSubmitDO.getHeaderOsType() == ProjectConstant.OSType.PC)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值渠道应用异常,请联系客服.");
            return result;
        }

        if (merchantApp.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)
                && StringUtils.isEmpty(rechargeSubmitDO.getProtocol())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值参数异常:请选择区块协议");
            return result;
        }


        if (!Arrays.asList(merchantApp.getLevelId().split(",")).stream().anyMatch(i -> i.equals(level.getLevelId().toString()))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值通道已下架.请更换充值金额或方式");
            return result;
        }

        // 三方商户配置 GlPaymentMerchantaccount判断
        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(merchantApp.getMerchantId());

        if (null == merchantAccount) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值渠道配置异常,请联系客服.");
            return result;
        }

        //今日已收款金额
        Long succAmount = merchantAccount.getSuccessAmount();
        if (succAmount == null) {
            succAmount = 0L;
        }
        succAmount = succAmount + rechargeSubmitDO.getAmount().longValue();

        Boolean accountFlat = false;
        // 允许1.5倍超收
        if (merchantAccount.getDailyLimit() != null && succAmount > merchantAccount.getDailyLimit() * 1.5) {
            accountFlat = true;
        }
        //防止超额充值
        if (accountFlat) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("金额超限,请更换充值渠道.");
            return result;
        }

        GlPaymentHandler paymentHandler = glRechargeHandlerManager.getPaymentHandler(merchantAccount);
        if (null != paymentHandler) {
            //检查姓名
            if (paymentHandler.needName(merchantAccount, merchantApp.getPaymentId())) {
                if (StringUtils.isEmpty(rechargeSubmitDO.getName()) || !RegexValidator.isNameV2(rechargeSubmitDO.getName())) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("请填写付款人姓名");
                    return result;
                }
            }

            //检验卡号
            if (paymentHandler.needCard(merchantAccount, merchantApp.getPaymentId())) {
                if (StringUtils.isEmpty(rechargeSubmitDO.getCardNo()) || !RegexValidator.isBankCard(rechargeSubmitDO.getCardNo())) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("请填写转账银行卡号");
                    return result;
                }
            }
        }


        GlPaymentChannelBank channelBank = null;
        // 网银支付 如果设置银行卡限额。只验证银行卡限额
        if (merchantApp.getPaymentId() == FundConstant.PaymentType.ONLINE_PAY) {
            if (null == rechargeSubmitDO.getBankId()) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                result.setErrorMsg("充值参数异常:请选择银行.");
                return result;
            }
            channelBank = glPaymentChannelBankBusiness.getBankInfo(merchantApp.getChannelId(), rechargeSubmitDO.getBankId(), ProjectConstant.CommonStatus.NORMAL);
            if (null == channelBank) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                result.setErrorMsg("银行通道维护,请尝试其他充值方式");
                return result;
            }
            // 验证充值金额   未设置限额时：minAmount maxAmount = 0
            if (channelBank.getMinAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeSubmitDO.getAmount().compareTo(channelBank.getMinAmount()) == -1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额低于最低金额");
                    return result;
                }
            }
            if (channelBank.getMaxAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeSubmitDO.getAmount().compareTo(channelBank.getMaxAmount()) == 1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额超出最高金额");
                    return result;
                }
            }
        }

        GlPaymentMerchantFee merchantFee = glPaymentMerchantFeeBusiness.findFee(rechargeSubmitDO.getLimitType(), merchantApp.getMerchantId(),
                merchantApp.getPaymentId());

        if (null != merchantFee) {
            if (merchantFee.getMinAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeSubmitDO.getAmount().compareTo(merchantFee.getMinAmount()) == -1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额低于最低金额");
                    return result;
                }
            }

            if (merchantFee.getMaxAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeSubmitDO.getAmount().compareTo(merchantFee.getMaxAmount()) == 1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额超出最高金额");
                    return result;
                }
            }
        }

        GlPaymentRechargeHandler handler = glRechargeHandlerManager.getRechargeHandler(merchantAccount);
        if (null == handler) {
            log.error("no_handler_for_recharge_channel {}.", merchantAccount.getChannelName());
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值通道异常(2),请联系客服.");
            return result;
        }

        userDO.setDeviceId(rechargeSubmitDO.getHeaderDeviceId());

        //V2 黑名单需求添加 2.异步校验是否监控
        glRechargeBusiness.checkBlackMonitor(userDO, rechargeSubmitDO.getRequestIp());

        /**
         * 组装充值请求数据
         */
        Date now = new Date();
        RechargePrepareDO rechargePrepareDO = new RechargePrepareDO();
        rechargePrepareDO.setOrderId(redisService.getTradeNo(OrderPrefix.CZ.getCode()));
        rechargePrepareDO.setUserId(userDO.getId());
        rechargePrepareDO.setUsername(userDO.getUsername());
        rechargePrepareDO.setUserLevel(level.getLevelId().toString());
        rechargePrepareDO.setBankId(rechargeSubmitDO.getBankId());
        rechargePrepareDO.setAmount(rechargeSubmitDO.getAmount());
        rechargePrepareDO.setClientType(rechargeSubmitDO.getHeaderOsType());
        rechargePrepareDO.setIp(rechargeSubmitDO.getRequestIp());
        rechargePrepareDO.setCreateDate(now);
        rechargePrepareDO.setPaymentTypeId(rechargeSubmitDO.getPaymentTypeId());
        //快捷支付：付款银行卡号
        if (StringUtils.isNotEmpty(rechargeSubmitDO.getCardNo())) {
            rechargePrepareDO.setFromCardNo(rechargeSubmitDO.getCardNo());
        }
        //实名转账
        if (StringUtils.isNotEmpty(rechargeSubmitDO.getName())) {
            rechargePrepareDO.setFromCardUserName(rechargeSubmitDO.getName());
        }

        try {
            result.setTradeId(rechargePrepareDO.getOrderId());

            //调用三方充值接口
            handler.prepare(merchantApp, merchantAccount, rechargePrepareDO, result);

            //充值记录
            GlRecharge recharge = glRechargeBusiness.prepareRecharge(rechargePrepareDO, rechargeSubmitDO, userDO, merchantApp, merchantFee, merchantAccount);

            GlRechargeReceiveInfo receiveInfo = this.prepareRechargeReceiveInfo(result, recharge);

            //保存充值记录&上报
            glRechargeTransactionBusiness.doRechargeCreate(recharge, merchantApp, userDO, receiveInfo, rechargeSubmitDO, result);

        } catch (Exception e) {
            log.info("GlRecharge_doSubmit_errorInfo:{}", e);
            throw new GlobalException(e.getMessage(), e);
        } finally {
            redisService.delete(rechargeLock);
        }
        return result;
    }

    public PaymentInfo payments(RechargePaymentDo paymentDo) throws GlobalException {

        PaymentInfo result = new PaymentInfo();
        // 三方商户配置 GlPaymentMerchantaccount判断
        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(paymentDo.getMerchantId());

        if (null == merchantAccount) {
            result.setErrosMessage("商户不存在");
            result.setLocalKeyConfig(FundLanguageMvcEnum.RECHARGE_PAYMENT_MECHANT_NOT_EXIST);
            return result;
        }
        GlPaymentHandler handler = glRechargeHandlerManager.getPaymentHandler(merchantAccount);
        if (null == handler) {
            log.error("no_handler_for_recharge_channel {}.", merchantAccount.getChannelName());
            result.setErrosMessage("充值通道异常(2),请联系客服.");
            result.setLocalKeyConfig(FundLanguageMvcEnum.RECHARGE_PAYMENT_HANDLER_ERROR);
            return result;
        }

        try {
            //调用三方充值接口
            result = handler.payments( merchantAccount, paymentDo.getAmount());
        } catch (Exception e) {
            log.info("GlRecharge_doSubmit_errorInfo:{}", e);
            throw new GlobalException(e.getMessage(), e);
        }
        return result;
    }


    /**
     * 后台创建订单
     *
     * @param createDO
     * @param adminDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public GlRechargeResult doRechargeForBackend(RechargeCreateDO createDO, GlAdminDO adminDO, HttpServletRequest request) throws GlobalException {
        GlRechargeResult result = new GlRechargeResult();

        GlRecharge req = glRechargeBusiness.findById(createDO.getRelationOrderId());

        //充值订单验证
        if (req == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(createDO.getRelationOrderId() + "订单号不存在");
            return result;
        }

        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(req.getMerchantId());

        if (null == merchantAccount) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("商户配置不存在");
            return result;
        }

        // 三方商户启用状态判断
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.selectOneByEntity(req.getPaymentId(),
                merchantAccount.getMerchantId(), UseModeEnum.APP.getCode());
        if (null == merchantApp) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值渠道应用不存在");
            return result;
        }

        GlPaymentRechargeHandler handler = glRechargeHandlerManager.getRechargeHandler(merchantAccount);

        if (null == handler) {
            log.error("no_handler_for_recharge_channel {}.", merchantAccount.getChannelName());
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("获取handler异常");
            return result;
        }

        // 会员信息
        GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(req.getUserId()));

        // 会员层级信息
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(req.getUserId());

        Date now = new Date();
        String orderId = redisService.getTradeNo(OrderPrefix.CZ.getCode());

        // 组装充值请求数据
        RechargePrepareDO rechargePrepareDO = new RechargePrepareDO();
        rechargePrepareDO.setOrderId(orderId);
        rechargePrepareDO.setUserId(userDO.getId());
        rechargePrepareDO.setUsername(userDO.getUsername());
        rechargePrepareDO.setUserLevel(userlevel.getLevelId().toString());
        rechargePrepareDO.setBankId(req.getBankId());
        rechargePrepareDO.setAmount(createDO.getAmount());
        rechargePrepareDO.setClientType(req.getClientType());
        rechargePrepareDO.setIp(req.getIp());
        rechargePrepareDO.setCreateDate(now);


        if (StringUtils.isNotEmpty(req.getKeyword())) {
            rechargePrepareDO.setFromCardUserName(req.getKeyword().split("\\|\\|")[0]);
            // 附言
            if (merchantAccount.getChannelId().equals(FundConstant.PaymentChannel.STORMPAY)
                    || merchantAccount.getChannelId().equals(FundConstant.PaymentChannel.STPAYER)) {
                String keyword = glRechargeBusiness.getKeyword(userDO.getUsername());
                rechargePrepareDO.setKeyword(keyword);
            }
        }

        try {
            //调用三方充值接口
            handler.prepare(merchantApp, merchantAccount, rechargePrepareDO, result);
            result.setTradeId(orderId);

            //充值记录
            GlRecharge recharge = new GlRecharge();
            BeanUtils.copyProperties(req, recharge);
            recharge.setOrderId(rechargePrepareDO.getOrderId());
            recharge.setStatus(FundConstant.RechargeStatus.PENDING);
            recharge.setAmount(createDO.getAmount());
            recharge.setCreateDate(now);
            recharge.setLastUpdate(now);


            ParamBaseDO paramBaseDO = new ParamBaseDO();
            paramBaseDO.setHeaderDeviceId(FundConstant.AGENCY_DEVICE_UUID);
            paramBaseDO.setRequestUrl("fundmng.com");
            paramBaseDO.setRequestIp("127.0.0.1");

            GlRechargeRelation relation = this.createRechargeRelation(orderId, createDO, adminDO);

            GlRechargeReceiveInfo receiveInfo = this.prepareRechargeReceiveInfo(result, recharge);

            //保存充值记录&上报
            glRechargeTransactionBusiness.doRechargeForBackend(recharge, merchantApp, userDO, receiveInfo, paramBaseDO, relation, result);

        } catch (Exception e) {
            log.info("GlRecharge_doSubmit_errorInfo:{}", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
        return result;
    }

    /**
     * 封装关联充值订单
     *
     * @param orderId
     * @param createDO
     * @param adminDO
     * @return
     */
    private GlRechargeRelation createRechargeRelation(String orderId, RechargeCreateDO createDO, GlAdminDO adminDO) {
        GlRechargeRelation rechargeRelation = new GlRechargeRelation();
        rechargeRelation.setOrderId(orderId);
        rechargeRelation.setRelationOrderId(createDO.getRelationOrderId());
        rechargeRelation.setRemark(createDO.getRemark());
        String imgPath = "";
        if (createDO.getAttachments() != null && createDO.getAttachments().size() > 0) {
            imgPath = imgPath + createDO.getAttachments().get(0);
            for (int i = 1; i < createDO.getAttachments().size(); i++) {
                imgPath = imgPath + "|" + createDO.getAttachments().get(i);
            }
        }
        rechargeRelation.setImg(imgPath);
        rechargeRelation.setCreator("admin");
        rechargeRelation.setCreateDate(new Date());
        return rechargeRelation;
    }

    /**
     * 提交充值:InnerPay = true
     *
     * @param rechargeTransferDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public GlRechargeTransferResult doRechargeTransfer(RechargeTransferDO rechargeTransferDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException {
        GlRechargeTransferResult result = new GlRechargeTransferResult();

        if (userDO.getIsFake().equals("1")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(ResultCode.VIRTUAL_REQUEST_ERROR.getMessage());
            return result;
        }

        //防止重复提交
        String rechargeLock = RedisKeyHelper.BALLBET_RECHARGE_USER_LOCK + userDO.getId();
        if (redisService.incrBy(rechargeLock, 1) > 1) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(ResultCode.TOOMANY_REQUEST.getMessage());
            return result;
        }
        redisService.setTTL(rechargeLock, 15);

        // 最后一笔充值订单验证
        GlRecharge lastRecharge = glRechargeBusiness.getLastRecharge(userDO.getId());
        if (null != lastRecharge && (FundConstant.RechargeStatus.PENDING == lastRecharge.getStatus()
                || FundConstant.RechargeStatus.REVIEW == lastRecharge.getStatus())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("账户存在待支付订单:" + lastRecharge.getOrderId());
            return result;
        }

        //充值金额验证
        if (new BigDecimal(rechargeTransferDO.getAmount().intValue()).compareTo(rechargeTransferDO.getAmount()) == -1) {
            result.setErrorMsg("充值金额错误:请输入整数金额");
            return result;
        }

        // 会员状态验证
        if (userDO.getStatus() == UserConstant.Status.NEW) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("请修改初始密码,保证资金安全.");
            return result;
        }

        if (userDO.getStatus() != UserConstant.Status.NORMAL) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(ResultCode.USER_LOCKING.getMessage());
            return result;
        }

        GlFundUserlevel level = glFundUserlevelBusiness.getUserLevel(userDO.getId());
        userDO.setUserFundLevelId(level.getLevelId());
        Optional.ofNullable(userVipUtils.getUserVIPCache(userDO.getId())).ifPresent(obj -> {
            userDO.setVipLevel(obj.getVipLevel());
        });
        // 用户手机号码和银行卡绑定验证
        if (UserConstant.Type.PLAYER == userDO.getUserType()) {
            String onOff = redisService.get(RedisKeyHelper.CHECKED_USER_INFO_ONOFF);
            if (StringUtils.isNotEmpty(onOff) && "1".equals(onOff)) {//开关开启
                if (!checkWhiteList(userDO.getId())) {
                    if (StringUtils.isEmpty(userDO.getTelephone())) {
                        result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                        result.setErrorMsg("请完善个人信息:绑定电话号码");
                        return result;
                    }

                    List<GlWithdrawUserBankCard> glWithdrawUserBankCards = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
                    if (ObjectUtils.isEmpty(glWithdrawUserBankCards)) {
                        result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                        result.setErrorMsg("请完善个人信息:绑定银行卡");
                        return result;
                    }
                }

            }
        }
        //撮合系统订单校验配置信息
        if (rechargeTransferDO.getChangePayType() || c2CPaymentIds.stream().anyMatch(id -> id.equals(rechargeTransferDO.getPaymentId()))) {
            if (rechargeTransferDO.getChangePayType()) {
                rechargeTransferDO.setPaymentId(glRechargeBusiness.getPaymentId(rechargeTransferDO.getPaymentId()));
            } else {
                String message = glRechargeBusiness.validC2C(userDO);
                if (StringUtils.isNotEmpty(message)) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg(message);
                    return result;
                }
            }
        }

        //根据三方商户应用优化配置选择出款商户应用
        RechargeMerchangAppDO merchangAppDO = DtoUtils.transformBean(rechargeTransferDO, RechargeMerchangAppDO.class);
        merchangAppDO.setLevelId(level.getLevelId());
        merchangAppDO.setInnerPay(true);
        merchangAppDO.setVipLevel(userDO.getVipLevel());
        merchangAppDO.setUserId(userDO.getId());
        if (FundConstant.AGENT_TYPE != rechargeTransferDO.getPayType()) {
            Integer merchantAppId = glRechargeBusiness.getMerchantAppId(merchangAppDO);
            if (ObjectUtils.isEmpty(merchantAppId)) {
                log.warn("三方商户应用优化级获取失败 :{} ", JSON.toJSONString(rechargeTransferDO));
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                result.setErrorMsg("充值失败,请刷新页面重新充值.");
                return result;
            } else {
                rechargeTransferDO.setMerchantAppId(merchantAppId);
            }
        }
        // 三方商户应用判断
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.findById(rechargeTransferDO.getMerchantAppId());

        if (null == merchantApp || merchantApp.getStatus() != ProjectConstant.CommonStatus.NORMAL ||
                merchantApp.getOpenStatus() != ProjectConstant.CommonStatus.NORMAL) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(ResultCode.RECHARGE_CHANNEL_CLOSED.getMessage());
            return result;
        }
        if (!merchantApp.getCoin().equals(rechargeTransferDO.getCoin())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("支付方式与币种不符");
            return result;
        }

        if (merchantApp.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)
                && StringUtils.isEmpty(rechargeTransferDO.getProtocol())) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("请选择区块协议");
            return result;
        }

        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(merchantApp.getMerchantId());
        if (null == merchantAccount) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("充值渠道配置异常,请联系客服.");
            return result;
        }

        //今日已收款金额
        Long succAmount = merchantAccount.getSuccessAmount();
        if (succAmount == null) {
            succAmount = 0L;
        }
        succAmount = succAmount + rechargeTransferDO.getAmount().longValue();

        Boolean accountFlat = false;
        // 允许1.5倍超收
        if (merchantAccount.getDailyLimit() != null && succAmount > merchantAccount.getDailyLimit() * 1.5) {
            accountFlat = true;
        }
        //防止超额充值
        if (accountFlat) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg("金额超限,请更换充值渠道.");
            return result;
        }

        GlPaymentHandler paymentHandler = glRechargeHandlerManager.getPaymentHandler(merchantAccount);
        if (null != paymentHandler) {
            //检查姓名
            if (paymentHandler.needName(merchantAccount, merchantApp.getPaymentId())) {
                if (StringUtils.isEmpty(rechargeTransferDO.getName()) || !RegexValidator.isNameV2(rechargeTransferDO.getName())) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("请填写付款人姓名");
                    return result;
                }
            }

            //检验卡号
            if (paymentHandler.needCard(merchantAccount, merchantApp.getPaymentId())) {
                if (StringUtils.isEmpty(rechargeTransferDO.getCardNo()) || !RegexValidator.isBankCard(rechargeTransferDO.getCardNo())) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("请填写正确的转账银行信息");
                    return result;
                }
            }
        }


        // 充值通道手续费配置
        GlPaymentMerchantFee merchantFee = glPaymentMerchantFeeBusiness.findFee(rechargeTransferDO.getLimitType(), merchantApp.getMerchantId(),
                merchantApp.getPaymentId());
        if (null != merchantFee) {
            if (merchantFee.getMinAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeTransferDO.getAmount().compareTo(merchantFee.getMinAmount()) == -1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额不能低于最低金额:" + merchantFee.getMinAmount());
                    return result;
                }
            }
            if (merchantFee.getMaxAmount().compareTo(BigDecimal.ZERO) == 1) {
                if (rechargeTransferDO.getAmount().compareTo(merchantFee.getMaxAmount()) == 1) {
                    result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
                    result.setErrorMsg("充值金额不能超出最高金额:" + merchantFee.getMaxAmount());
                    return result;
                }
            }
        }

        GlPaymentRechargeHandler handler = glRechargeHandlerManager.getTransferHandler(merchantAccount);
        if (null == handler) {
            log.error("no_handler_for_recharge_channel:{}.", merchantAccount.getChannelName());
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM);
            result.setErrorMsg(ResultCode.RECHARGE_CHANNEL_CLOSED.getMessage());
            return result;
        }

        //V2 黑名单需求添加 2.异步校验是否监控
        userDO.setDeviceId(rechargeTransferDO.getHeaderDeviceId());
        glRechargeBusiness.checkBlackMonitor(userDO, rechargeTransferDO.getRequestIp());

        String ip = HttpUtils.getRequestIp(request);
        Date now = new Date();

        String orderId = redisService.getTradeNo(OrderPrefix.CZ.getCode());

        /**
         * 组装充值请求数据
         */
        RechargePrepareDO rechargePrepareDO = new RechargePrepareDO();
        rechargePrepareDO.setOrderId(orderId);
        rechargePrepareDO.setUserId(userDO.getId());
        rechargePrepareDO.setUsername(userDO.getUsername());
        rechargePrepareDO.setAmount(rechargeTransferDO.getAmount());
        rechargePrepareDO.setClientType(rechargeTransferDO.getHeaderOsType());
        rechargePrepareDO.setIp(ip);
        rechargePrepareDO.setUserLevel(level.getLevelId().toString());
        rechargePrepareDO.setCreateDate(now);
        rechargePrepareDO.setBankId(rechargeTransferDO.getBankId());
        rechargePrepareDO.setFromCardUserName(rechargeTransferDO.getName());
        rechargePrepareDO.setProtocol(rechargeTransferDO.getProtocol());
        rechargePrepareDO.setPaymentTypeId(rechargeTransferDO.getPaymentTypeId());
        // 生成附言
        if (StringUtils.isNotEmpty(rechargeTransferDO.getName())) {
            String keyword = glRechargeBusiness.getKeyword(userDO.getUsername());
            rechargePrepareDO.setKeyword((rechargeTransferDO.getName() == null ? "" : rechargeTransferDO.getName()) + "||" + keyword);
        }
        try {

            GlRechargeResult rechargeResult = new GlRechargeResult();
            rechargeResult.setTradeId(rechargePrepareDO.getOrderId());
            //调用三方商户接口
            handler.prepare(merchantApp, merchantAccount, rechargePrepareDO, rechargeResult);

            //充值记录
            GlRecharge recharge = glRechargeBusiness.prepareTransfer(rechargePrepareDO, rechargeTransferDO, userDO, merchantApp, merchantFee, merchantAccount);

            GlRechargeReceiveInfo receiveInfo = prepareRechargeReceiveInfo(rechargeResult, recharge);

            //保存充值记录&上报
            glRechargeTransactionBusiness.doRechargeCreate(recharge, merchantApp, userDO, receiveInfo, rechargeTransferDO, rechargeResult);

            if (rechargeResult.getErrorCode() != FundConstant.RechargeErrorCode.NORMAL) {
                result.setErrorCode(rechargeResult.getErrorCode());
                result.setErrorMsg(rechargeResult.getErrorMsg());
                return result;
            }
            result.setTradeNo(orderId);
            result.setAmount(recharge.getAmount());
            Optional.ofNullable(rechargeResult.getBankInfo())
                    .ifPresent(info -> {
                        result.setBankId(info.getBankId());
                        result.setBankName(info.getBankName());
                        result.setBankBranchName(info.getBankBranchName());
                        result.setCardNo(info.getCardNo());
                        result.setName(info.getName());
                        result.setKeyword(info.getKeyword());
                    });
            Optional.ofNullable(rechargeResult.getBlockInfo())
                    .ifPresent(info -> {
                        RechargeDigitalInfo digitalInfo = new RechargeDigitalInfo();
                        digitalInfo.setBlockAddress(info.getBlockAddress());
                        digitalInfo.setDigitalAmount(info.getDigitalAmount());
                        digitalInfo.setProtocol(info.getProtocol());
                        digitalInfo.setRate(info.getRate());
                        result.setDigitalInfo(digitalInfo);
                    });
            result.setPaymentName(FundConstant.paymentTypeMap.get(rechargeTransferDO.getPaymentId()));
            return result;
        } catch (GlobalException e) {
            log.error("doTransfer_err:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        } finally {
            redisService.delete(rechargeLock);
        }
    }

    public GlRechargeReceiveInfo prepareRechargeReceiveInfo(GlRechargeResult result, GlRecharge recharge) {
        //初始化
        GlRechargeReceiveInfo receiveInfo = new GlRechargeReceiveInfo();
        receiveInfo.setOrderId(recharge.getOrderId());
        receiveInfo.setAmount(null == result.getAmount() ? recharge.getAmount() : result.getAmount());
        receiveInfo.setCreateDate(recharge.getCreateDate());
        receiveInfo.setExpiredDate(DateUtils.addMinutes(recharge.getCreateDate(), 30));
        receiveInfo.setShowType("NORMAL");
        receiveInfo.setThirdOrderId(result.getThirdOrderId());

        if (null != result.getBankInfo()) {
            BankInfo bankInfo = result.getBankInfo();
            receiveInfo.setOwner(bankInfo.getName());
            receiveInfo.setBankcardId(bankInfo.getBankId());
            receiveInfo.setBankcardName(bankInfo.getBankName());
            receiveInfo.setBankcardBranch(bankInfo.getBankBranchName());
            receiveInfo.setBankcardNo(bankInfo.getCardNo());
            receiveInfo.setKeyword(bankInfo.getKeyword());
            if (null != bankInfo.getExpiredDate()) {
                receiveInfo.setExpiredDate(bankInfo.getExpiredDate());
            }
            receiveInfo.setShowType("DETAIL");

            //保存收款账户信息
            recharge.setCardUsername(bankInfo.getName());
            recharge.setBankId(null != bankInfo.getBankId() ? bankInfo.getBankId() : -1);
            recharge.setBankName(StringUtils.isNotEmpty(bankInfo.getBankName()) ? bankInfo.getBankName() : "其他");
            recharge.setCardNo(bankInfo.getCardNo());
        } else if (null != result.getBlockInfo()) {
            BlockInfo blockInfo = result.getBlockInfo();
            receiveInfo.setOwner(blockInfo.getOwner());
            receiveInfo.setDigitalAmount(blockInfo.getDigitalAmount());
            receiveInfo.setProtocol(blockInfo.getProtocol());
            receiveInfo.setBlockAddress(blockInfo.getBlockAddress());
            receiveInfo.setRate(blockInfo.getRate());

            if (null != blockInfo.getExpiredDate()) {
                receiveInfo.setExpiredDate(blockInfo.getExpiredDate());
            }
            receiveInfo.setShowType("DIGITAL");
            if (!ObjectUtils.isEmpty(recharge.getPaymentId()) && recharge.getPaymentId() == FundConstant.PaymentType.RMB_PAY) {
                receiveInfo.setShowType("NORMAL");
            }
        }
        return receiveInfo;
    }

    @Override
    public GlRechargeTransferResult doRechargeTransferForBackend(RechargeCreateDO createDO, GlAdminDO adminDO, HttpServletRequest request) throws GlobalException {
        GlRechargeTransferResult result = new GlRechargeTransferResult();

        GlRecharge dbRecharge = glRechargeBusiness.findById(createDO.getRelationOrderId());
        if (null == dbRecharge) {
            String message = LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_ERROR).withDefaultValue("订单号异常").parse(createDO.getLanguage());
            result.setErrorMsg(message +  createDO.getRelationOrderId());
            return result;
        }

        // 三方商户启用状态判断
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.selectOneByEntity(dbRecharge.getPaymentId(),
                dbRecharge.getMerchantId(), UseModeEnum.APP.getCode());
        if (null == merchantApp || merchantApp.getStatus() != ProjectConstant.CommonStatus.NORMAL ||
                merchantApp.getOpenStatus() != ProjectConstant.CommonStatus.NORMAL) {
            result.setErrorMsg("充值渠道不存在或已关闭");
            result.setKeyConfig(FundLanguageMvcEnum.RECHARGE_MERCHANT_OFF);
            return result;
        }

        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(merchantApp.getMerchantId());
        if (null == merchantAccount) {
            String message = LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_MERCHANT_NOT_EXIST).withDefaultValue("充值渠道配置不存在").parse(createDO.getLanguage());
            result.setErrorMsg(message + merchantAccount.getChannelName());
            return result;
        }

        GlPaymentRechargeHandler handler = glRechargeHandlerManager.getTransferHandler(merchantAccount);
        if (null == handler) {
            String message = LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_MERCHANT_HANDLER_EXIST).withDefaultValue("获取handler异常").parse(createDO.getLanguage());
            result.setErrorMsg(message + merchantAccount.getChannelName());
            return result;
        }

        String ip = HttpUtils.getRequestIp(request);
        Date now = new Date();

        String orderId = redisService.getTradeNo(OrderPrefix.CZ.getCode());

        // 会员信息
        GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(dbRecharge.getUserId()));
        // 会员层级信息
        GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(dbRecharge.getUserId());

        RechargePrepareDO rechargePrepareDO = new RechargePrepareDO();
        rechargePrepareDO.setOrderId(orderId);
        rechargePrepareDO.setUserId(userDO.getId());
        rechargePrepareDO.setUsername(userDO.getUsername());
        rechargePrepareDO.setAmount(createDO.getAmount());
        rechargePrepareDO.setClientType(dbRecharge.getClientType());
        rechargePrepareDO.setIp(ip);
        rechargePrepareDO.setBankId(dbRecharge.getBankId());
        rechargePrepareDO.setBankName(dbRecharge.getBankName());
        rechargePrepareDO.setUserLevel(userlevel.getLevelId().toString());

        GlRechargeReceiveInfo reInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(dbRecharge.getOrderId());
        if (null != reInfo) {
            rechargePrepareDO.setProtocol(reInfo.getProtocol());
        }

        //附言&付款人姓名
        if (StringUtils.isNotEmpty(dbRecharge.getKeyword())) {
            rechargePrepareDO.setKeyword(dbRecharge.getKeyword());
            rechargePrepareDO.setFromCardUserName(dbRecharge.getKeyword().split("\\|\\|")[0]);
        }

        rechargePrepareDO.setCreateDate(now);
        try {

            GlRechargeResult rechargeResult = new GlRechargeResult();
            rechargeResult.setTradeId(orderId);
            //调用三方商户接口
            handler.prepare(merchantApp, merchantAccount, rechargePrepareDO, rechargeResult);

            if (rechargeResult.getErrorCode() != FundConstant.RechargeErrorCode.NORMAL) {
                result.setErrorMsg(rechargeResult.getErrorMsg());
                return result;
            }

            //充值记录
            GlRecharge recharge = new GlRecharge();
            BeanUtils.copyProperties(dbRecharge, recharge);
            recharge.setOrderId(orderId);
            recharge.setAmount(createDO.getAmount());
            recharge.setStatus(FundConstant.RechargeStatus.PENDING);
            recharge.setCreateDate(now);
            recharge.setLastUpdate(now);

            GlRechargeReceiveInfo receiveInfo = prepareRechargeReceiveInfo(rechargeResult, recharge);

            //保存充值记录&上报
            ParamBaseDO paramBaseDO = new ParamBaseDO();
            paramBaseDO.setHeaderDeviceId(FundConstant.AGENCY_DEVICE_UUID);
            paramBaseDO.setRequestUrl("fundmng.com");
            paramBaseDO.setRequestIp("127.0.0.1");

            GlRechargeRelation relation = this.createRechargeRelation(orderId, createDO, adminDO);

            glRechargeTransactionBusiness.doRechargeForBackend(recharge, merchantApp, userDO, receiveInfo, paramBaseDO, relation, rechargeResult);

            result.setTradeNo(orderId);
            result.setAmount(recharge.getAmount());
            return result;
        } catch (GlobalException e) {
            log.error("doTransfer_err:{}", e.toString());
            throw e;
        }
    }

    /**
     * 充值订单回调接口
     *
     * @param merchantAppId
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public RechargeNotifyResponse notify(Integer merchantAppId, HttpServletRequest request) throws GlobalException {
        Map<String, String[]> params = request.getParameterMap();
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<String, String[]> each : params.entrySet()) {
            resMap.put(each.getKey(), each.getValue()[0]);
        }

        RechargeNotifyResponse rechargeNotifyResponse = new RechargeNotifyResponse();
        try {
            String headSign = request.getHeader("Content-Hmac");
            String body = GlRequestUtil.inputStream2String(request.getInputStream());
            resMap.put("reqBody", body);
            resMap.put("headSign", headSign);
            log.info("recharge_notify_resMap{}", JSON.toJSONString(resMap));
            GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.findById(merchantAppId);
            if (merchantApp == null) {
                rechargeNotifyResponse.setContent("invalid payment");
                return rechargeNotifyResponse;
            }

            GlPaymentMerchantaccount payment = glPaymentMerchantaccountBusiness.findById(merchantApp.getMerchantId());
            if (payment == null) {
                rechargeNotifyResponse.setContent("invalid merchant");
                return rechargeNotifyResponse;
            }

            RechargeNotify notify = glRechargeBusiness.doPaymentNotify(merchantApp, payment, resMap);
            log.info("recharge_notify:{}", JSON.toJSONString(notify));
            if (notify == null) {
                // 回调验证失败
                rechargeNotifyResponse = glRechargeHandlerManager.rechargeFailNotifyResponse();
                return rechargeNotifyResponse;
            }

            GlRecharge recharge = glRechargeBusiness.findById(notify.getOrderId());
            if (recharge == null || !recharge.getChannelId().equals(payment.getChannelId())) {
                rechargeNotifyResponse.setContent("invalid merchant[1]");
                return rechargeNotifyResponse;
            }

            //充值记录修改、增加用户余额
            glRechargeTransactionBusiness.doNotifySuccess(notify);
            // 回调验证成功，响应信息封装
            if (StringUtils.isNotBlank(notify.getRsp())) {
                rechargeNotifyResponse.setContent(notify.getRsp());
            } else {
                rechargeNotifyResponse = glRechargeHandlerManager.rechargeOKNotifyResponse(payment.getChannelId());
            }
            // 代客充值回调事件处理
            applicationContext.publishEvent(new OrderNotifyEvent(notify.getOrderId()));
            return rechargeNotifyResponse;
        } catch (Exception e) {
            log.error("notify_error:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 定制充值订单回调接口:风云聚合
     *
     * @param merchantAppId
     * @param request
     * @return
     * @throws GlobalException
     */
    @Override
    public RechargeNotifyResponse notifyForStormPay(Integer merchantAppId, HttpServletRequest request) throws GlobalException {
        Map<String, String[]> params = request.getParameterMap();
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<String, String[]> each : params.entrySet()) {
            resMap.put(each.getKey(), each.getValue()[0]);
        }
        try {
            RechargeNotifyResponse notifyResponse = new RechargeNotifyResponse();
            String headSign = request.getHeader("Content-Hmac");
            String body = GlRequestUtil.inputStream2String(request.getInputStream());
            resMap.put("reqBody", body);
            resMap.put("headSign", headSign);
            log.info("notifyForStormPay_notify_resMap:{}", resMap);
            JSONObject json = JSON.parseObject(resMap.get("reqBody"));
            log.info("notifyForStormPay_notify_json:{}", json);
            if (null == json) {
                notifyResponse.setContent("param error");
                return notifyResponse;
            }
            // direction = (in:充值回调、out:提现回调)
            String direction = json.getString("direction");
            if (StringUtils.isEmpty(direction)) {
                notifyResponse.setContent("direction error");
                return notifyResponse;
            }
            /**
             * 充值订单回调
             */
            if (direction.equals("in")) {
                GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.findById(merchantAppId);
                if (merchantApp == null) {
                    notifyResponse.setContent("invalid payment");
                    return notifyResponse;
                }

                GlPaymentMerchantaccount payment = glPaymentMerchantaccountBusiness.findById(merchantApp.getMerchantId());

                if (payment == null || payment.getChannelId() != FundConstant.PaymentChannel.STORMPAY) {
                    notifyResponse.setContent("invalid merchant");
                    return notifyResponse;
                }
                RechargeNotify notify = glRechargeBusiness.doPaymentNotify(merchantApp, payment, resMap);
                if (notify == null) {
                    notifyResponse.setContent("pay failed");
                    return notifyResponse;
                }

                GlRecharge recharge = glRechargeBusiness.findById(notify.getOrderId());
                if (recharge == null || !recharge.getMerchantName().equals(payment.getChannelName())) {
                    notifyResponse.setContent("invalid merchant[1]");
                    return notifyResponse;
                }

                //充值记录修改、增加用户余额
                glRechargeTransactionBusiness.doNotifySuccess(notify);
                notifyResponse.setContent("true");
                return notifyResponse;
            }
        } catch (Exception e) {
            log.error("notifyForTransfer_error:{}", e.toString());
            throw new GlobalException(e.getMessage(), e);
        }
        return null;
    }

    @Override
    public Result submitSynchronize(GlAdminDO adminDO, String orderId) {
        Result.Builder newBuilder = Result.newBuilder();
        try {
            reSynchronize(orderId);
            return newBuilder.success().build();
        } catch (Exception ex) {
            log.error("{}同步充值订单{}的状态时发生异常", adminDO.getUsername(), orderId, ex);
            return newBuilder.fail().build();
        }
    }

    @Override
    public void reSynchronize(String... orderIds) {
        try {
            for (String orderId : orderIds) {
                log.debug("开始-重新上报的订单号是{}", orderId);
                GlRecharge recharge = glRechargeBusiness.findById(orderId);
                if (ObjectUtils.isEmpty(recharge)) {
                    continue;
                }
                GlUserDO userDO = RPCResponseUtils.getData(glUserService.findById(recharge.getUserId()));
                if (ObjectUtils.isEmpty(userDO)) {
                    continue;
                }

                RechargeReport report = new RechargeReport();
                report.setUserId(recharge.getUserId());
                report.setPayment(RechargePaymentEnum.valueOf(recharge.getPaymentId()));
                report.setStatus(RechargeStatusEnum.valueOf(recharge.getStatus()));
                report.setMerchant(recharge.getMerchantId());
                report.setMerchantName(recharge.getMerchantName());
                report.setMerchantCode(recharge.getMerchantCode());
                report.setChannelId(recharge.getChannelId());
                report.setChannelName(recharge.getChannelName());
                report.setBank(BankEnum.valueOf(recharge.getBankId()));
                report.setBankName(recharge.getBankName());
                report.setBankNo(recharge.getCardNo());

                report.setLimitType(recharge.getLimitType());
                report.setAmount(recharge.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setFee(recharge.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setCreateTime(recharge.getCreateDate());
                report.setSubType(FundConstant.paymentTypeMap.get(recharge.getPaymentId()));
                report.setKeyword(recharge.getKeyword());

                // 获取付款信息
                GlRechargePay rechargePay = glRechargePayBusiness.findById(orderId);
                if (rechargePay != null) {
                    report.setPayAmount(rechargePay.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                    report.setPayTime(rechargePay.getPayDate());
                    report.setFinishTime(rechargePay.getPayDate());
                    // 充值佣金手续费
                    RPCResponse<Long> response = commCommissionService.calcRechargeFee(userDO.getParentId(), rechargePay.getAmount(),recharge.getCoin());
                    report.setCommFee(RPCResponseUtils.getData(response));
                }
                // USDT付款信息
                GlRechargeReceiveInfo receiveInfo = glRechargeReceiveInfoMapper.selectByPrimaryKey(recharge.getOrderId());
                if (null != receiveInfo && recharge.getPaymentId().equals(FundConstant.PaymentType.DIGITAL_PAY)) {
                    BigDecimal usdtAmount = receiveInfo.getDigitalAmount();
                    if (null != usdtAmount) {
                        usdtAmount = usdtAmount.multiply(BigDecimal.valueOf(100000000));
                    } else {
                        usdtAmount = BigDecimal.ZERO;
                    }
                    BigDecimal rate = receiveInfo.getRate();
                    if (null != rate) {
                        rate = rate.multiply(BigDecimal.valueOf(100000000));
                    } else {
                        rate = BigDecimal.ZERO;
                    }
                    report.setUsdtAmount(usdtAmount.longValue());
                    report.setRate(rate.longValue());
                }
                // 首存标记
                Integer isFirst = 0;
                if (glRechargeMapper.isFirstForFix(recharge.getUserId(), recharge.getCreateDate())) {
                    isFirst = 1;
                }
                report.setFirst(isFirst);

                report.setUuid(recharge.getOrderId());
                report.setUid(recharge.getUserId());
                report.setTimestamp(recharge.getCreateDate());
                report.setPlatform(PlatformEnum.valueOf(recharge.getClientType()));
                report.setUserName(recharge.getUsername());
                report.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
                report.setParentName(userDO.getParentName());
                report.setParentId(userDO.getParentId());
                report.setRegTime(userDO.getRegisterDate());
                report.setIsFake(userDO.getIsFake());
                report.setCoin(recharge.getCoin());
                // VIP等级
                if (userDO.getUserType().equals(UserConstant.UserType.PLAYER)) {
                    UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
                    report.setVipLevel(null == vipCache ? 0 : vipCache.getVipLevel());
                } else {
                    report.setVipLevel(-1);
                }
                // 用户层级
                GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
                if (userlevel != null) {
                    report.setUserLevel(userlevel.getLevelId());
                    report.setUserLevelName(userlevel.getName());
                }
                // 处理充值订单的账变后金额(如果订单是成功状态，账变后金额=历史账变前金额+充值金额)
                if (recharge.getStatus() == FundConstant.RechargeStatus.SUCCESS) {
                    RPCResponse<RechargeRecordDetailDO> rechargeRecordRpcResponse = rechargeService.getRechargeDetail(recharge.getOrderId());
                    if (RPCResponseUtils.isSuccess(rechargeRecordRpcResponse)) {
                        RechargeRecordDetailDO rechargeRecordDetailDO = rechargeRecordRpcResponse.getData();
                        if (ObjectUtils.isEmpty(rechargeRecordDetailDO) == false) {
                            report.setBalanceAfter(rechargeRecordDetailDO.getBalanceBefore() + report.getAmount());
                        }
                    }
                }
                reportService.rechargeReport(report);
                log.debug("结束-重新上报的订单号是{}", orderId);
            }
        } catch (Exception ex) {
            log.error("重新同步充值订单发生异常", ex);
        }
    }

    @Override
    @Async
    public void synchronize(Date startDate, Date endDate) {
        log.info("[充值订单重新上报]任务开始执行");
        int page = 1;
        int success = 0;
        while (true) {
            List<GlRecharge> rechargeList = glRechargeBusiness.findRechargeRecord(startDate, endDate, page, 200);
            if (CollectionUtils.isEmpty(rechargeList)) {
                break;
            }
            for (GlRecharge recharge : rechargeList) {
                reSynchronize(recharge.getOrderId());
                success++;
            }
            page++;
        }
        log.info("[充值订单重新上报]任务执行完成，成功数量是{}", success);
    }

    /**
     * 回调信息乱码处理
     *
     * @param params
     * @return
     * @throws Exception
     */
    private Map<String, String> parseParams(Map<String, String[]> params) throws Exception {
        Map<String, String> params1 = new HashMap<>();
        for (Iterator iter = params.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) params.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            // 乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
            valueStr = new String(valueStr.getBytes("ISO-8859-1"), "UTF-8");
            params1.put(name, valueStr);
        }
        return params1;
    }

}

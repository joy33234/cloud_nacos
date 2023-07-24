package com.seektop.fund.business.recharge;

import com.alibaba.fastjson.JSON;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.google.common.collect.Lists;
import com.seektop.activity.service.RechargeWithdrawTempleService;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.*;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.data.service.RechargeService;
import com.seektop.data.service.SeoTeamService;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.fund.NameTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.GlPaymentChannelBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawMerchantAccountBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.common.C2CRechargeOrderMatchResult;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.param.recharge.account.MerchantAccountListDO;
import com.seektop.fund.controller.backend.param.recharge.account.MerchantAccountMonitorDO;
import com.seektop.fund.controller.backend.result.GlPaymentMerchantResult;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.backend.result.recharge.RechargeMonitorRetResult;
import com.seektop.fund.controller.backend.result.recharge.RechargeSuccessRateResult;
import com.seektop.fund.controller.forehead.param.recharge.*;
import com.seektop.fund.controller.forehead.result.RechargeAmountResult;
import com.seektop.fund.handler.C2COrderCallbackHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import com.seektop.fund.vo.GlRechargeAllCollect;
import com.seektop.fund.vo.GlRechargeCollect;
import com.seektop.fund.vo.RechargeMonitorResult;
import com.seektop.fund.vo.RechargeQueryDto;
import com.seektop.risk.dto.param.BlackMonitorDO;
import com.seektop.risk.service.BlackService;
import com.seektop.user.dto.GlUserLockDo;
import com.seektop.user.service.GlUserRechargeService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Slf4j
@Component
public class GlRechargeBusiness extends AbstractBusiness<GlRecharge> {

    @Reference(retries = 2, timeout = 3000)
    private BlackService blackService;

    @Autowired
    private GlRechargeMapper glRechargeMapper;

    @Resource
    private RedisService redisService;

    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantaccountBusiness;

    @Resource
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;

    @Resource
    private GlRechargeHandlerManager glPaymentManagerHandle;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Resource
    private GlRechargeErrorBusiness glRechargeErrorBusiness;
    @Resource
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;
    @Resource
    private GlPaymentChannelBusiness glPaymentChannelBusiness;
    @Resource(name = "randomAmountPaymentIds")
    private List<Integer> randomAmountPaymentIds;
    @Resource(name = "c2CPaymentIds")
    private List<Integer> c2CPaymentIds;
    @Autowired
    private GlPaymentMerchantFeeBusiness paymentMerchantFeeBusiness;
    @DubboReference
    private RechargeService rechargeService;
    @Autowired
    private Map<String, GlPaymentHandler> glPaymentHandlerMap;


    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;
    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;
    @Resource
    private C2COrderCallbackHandler c2COrderCallbackHandler;
    @DubboReference(retries = 2, timeout = 3000)
    private GlUserRechargeService userRechargeService;
    @DubboReference(retries = 2, timeout = 3000)
    private RechargeWithdrawTempleService rechargeWithdrawTempleService;
    @DubboReference(retries = 2, timeout = 3000)
    private SeoTeamService seoTeamService;
    @Resource
    private UserVipUtils userVipUtils;
    @Resource
    private GlFundBusiness glFundBusiness;

    public List<GlRecharge> findRechargeRecord(Date startDate, Date endDate, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(GlRecharge.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andBetween("createDate", startDate, endDate);
        return findByCondition(condition);
    }

    public Boolean hasRecharge(Integer userId) {
        return glRechargeMapper.hasRecharge(userId);
    }

    public Integer countUserSuccessTimes(Integer userId) {
        return glRechargeMapper.countUserSuccessTimes(userId);
    }

    /**
     * 获取指定用户指定时间段内的充值金额
     *
     * @param userId
     * @param startTime
     * @param endTime
     * @return
     */
    public BigDecimal getRechargeTotal(Integer userId, Date startTime, Date endTime) {
        return glRechargeMapper.getRechargeTotal(userId, startTime, endTime);
    }

    public GlRecharge prepareRecharge(RechargePrepareDO prepareDO, RechargeSubmitDO rechargeSubmitDO,
                                      GlUserDO userDO, GlPaymentMerchantApp merchantApp, GlPaymentMerchantFee merchantFee,
                                      GlPaymentMerchantaccount merchantAccount) {
        // 手续费
        BigDecimal fee = BigDecimal.ZERO;
        if (null != merchantFee && BigDecimal.ZERO.compareTo(merchantFee.getFeeRate()) == -1) {
            fee = rechargeSubmitDO.getAmount().multiply(merchantFee.getFeeRate()).divide(BigDecimal.valueOf(100));
            if (fee.compareTo(merchantFee.getMaxFee()) == 1) {
                fee = merchantFee.getMaxFee();
            }
        }
        GlRecharge recharge = new GlRecharge();
        recharge.setOrderId(prepareDO.getOrderId());
        recharge.setUserId(userDO.getId());
        recharge.setUserType(userDO.getUserType());
        recharge.setUsername(userDO.getUsername());
        recharge.setUserLevel(glFundUserlevelBusiness.getUserLevelId(userDO.getId()).toString());
        recharge.setAmount(prepareDO.getAmount());
        recharge.setFee(fee);
        recharge.setKeyword(prepareDO.getKeyword());
        recharge.setPaymentId(merchantApp.getPaymentId());
        recharge.setChannelId(merchantApp.getChannelId());
        recharge.setChannelName(merchantApp.getChannelName());
        recharge.setMerchantId(merchantAccount.getMerchantId());
        recharge.setMerchantCode(merchantAccount.getMerchantCode());
        recharge.setMerchantName(merchantAccount.getChannelName());
        if (rechargeSubmitDO.getBankId() == null) {
            recharge.setBankId(-1);
            recharge.setBankName("其他");
        } else {
            recharge.setBankId(rechargeSubmitDO.getBankId());
            recharge.setBankName(glPaymentChannelBankBusiness.getBankName(rechargeSubmitDO.getBankId(), merchantApp.getChannelId()));
        }
        if (StringUtils.isNotEmpty(rechargeSubmitDO.getName())) {
            recharge.setKeyword(rechargeSubmitDO.getName() + "||" + getKeyword(userDO.getUsername()));
        }
        recharge.setClientType(rechargeSubmitDO.getHeaderOsType());
        recharge.setAppType(rechargeSubmitDO.getHeaderAppType());
        recharge.setIp(rechargeSubmitDO.getRequestIp());
        recharge.setStatus(FundConstant.RechargeStatus.PENDING);
        recharge.setLimitType(rechargeSubmitDO.getLimitType());
        recharge.setAgentType(rechargeSubmitDO.getPayType());

        recharge.setCreateDate(prepareDO.getCreateDate());
        recharge.setLastUpdate(prepareDO.getCreateDate());
        recharge.setCoin(rechargeSubmitDO.getCoin());
        return recharge;
    }


    public GlRecharge prepareTransfer(RechargePrepareDO prepareDO, RechargeTransferDO rechargeTransferDO,
                                      GlUserDO userDO, GlPaymentMerchantApp merchantApp, GlPaymentMerchantFee merchantFee,
                                      GlPaymentMerchantaccount merchantAccount) {
        // 手续费
        BigDecimal fee = BigDecimal.ZERO;
        if (null != merchantFee && BigDecimal.ZERO.compareTo(merchantFee.getFeeRate()) == -1) {
            fee = rechargeTransferDO.getAmount().multiply(merchantFee.getFeeRate()).divide(BigDecimal.valueOf(100));
            if (fee.compareTo(merchantFee.getMaxFee()) == 1) {
                fee = merchantFee.getMaxFee();
            }
        }
        GlRecharge recharge = new GlRecharge();
        recharge.setOrderId(prepareDO.getOrderId());
        recharge.setUserId(userDO.getId());
        recharge.setUserType(userDO.getUserType());
        recharge.setUsername(userDO.getUsername());
        recharge.setUserLevel(glFundUserlevelBusiness.getUserLevelId(userDO.getId()).toString());
        recharge.setAmount(rechargeTransferDO.getAmount());
        recharge.setFee(fee);
        recharge.setKeyword(prepareDO.getKeyword());
        recharge.setPaymentId(merchantApp.getPaymentId());
        recharge.setChannelId(merchantApp.getChannelId());
        recharge.setChannelName(merchantApp.getChannelName());
        recharge.setMerchantId(merchantAccount.getMerchantId());
        recharge.setMerchantCode(merchantAccount.getMerchantCode());
        recharge.setMerchantName(merchantAccount.getChannelName());
        if (rechargeTransferDO.getBankId() == null) {
            recharge.setBankId(-1);
            recharge.setBankName("其他");
        } else {
            recharge.setBankId(rechargeTransferDO.getBankId());
            recharge.setBankName(glPaymentChannelBankBusiness.getBankName(rechargeTransferDO.getBankId(), merchantApp.getChannelId()));
        }
        recharge.setClientType(rechargeTransferDO.getHeaderOsType());
        recharge.setAppType(rechargeTransferDO.getHeaderAppType());
        recharge.setIp(rechargeTransferDO.getRequestIp());
        recharge.setStatus(FundConstant.RechargeStatus.PENDING);
        recharge.setLimitType(rechargeTransferDO.getLimitType());
        recharge.setAgentType(rechargeTransferDO.getPayType());
        if (c2CPaymentIds.stream().anyMatch(id -> id.equals(recharge.getPaymentId()))) {
            recharge.setAgentType(2);//极速转卡
        }

        recharge.setCreateDate(prepareDO.getCreateDate());
        recharge.setLastUpdate(prepareDO.getCreateDate());
        recharge.setCoin(rechargeTransferDO.getCoin());
        return recharge;
    }

    /**
     * 获取用户最后一条充值记录
     *
     * @param userId
     * @return
     */
    public GlRecharge getLastRecharge(Integer userId) {
        return glRechargeMapper.findLastRecharge(userId);
    }

    /**
     * 调用三方订单撤销接口
     *
     * @param glRecharge
     */
    public void doRechargeCancel(GlRecharge glRecharge) {
        GlPaymentMerchantaccount payment = glPaymentMerchantaccountBusiness.findById(glRecharge.getMerchantId());
        if (null == payment) {
            return;
        }
        GlRechargeCancelHandler handler = glPaymentManagerHandle.getRechargeCancelHandler(payment);
        if (null == handler) {
            log.info("doRechargeCancel_handler:{}", payment.getChannelId());
            return;
        }
        handler.cancel(payment, glRecharge);
    }

    /**
     * 充值渠道轮替
     */
    public List<GlPaymentResult> rechargeRotation(List<GlPaymentResult> paymentResults, GlUserDO userDO, RechargePaymentInfoDO paymentInfoDO) throws GlobalException {
        Date now = new Date();
        //充值轮训周期时间（单位为分）
        Integer cycleTime = redisService.get(RedisKeyHelper.RECHARGE_CYCLE_TIME,Integer.class);
        List<GlPaymentResult> results = new ArrayList<>();

        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        //当前商户当前周期内订单数量是否超过商户充值周期处理量
        Date cycleStart = getCycleStart(cycleTime, now);
        //支付方式
        for (GlPaymentResult paymentResult : paymentResults) {
            List<GlPaymentMerchantResult> resultList = paymentResult.getMerchantList();
            if (ObjectUtils.isEmpty(resultList)) {
                continue;
            }
            //过滤币种
            resultList = resultList.stream().filter(item -> item.getCoin().equals(paymentInfoDO.getCoin())).collect(Collectors.toList());
            //极速支付校验
            if (c2CPaymentIds.stream().anyMatch(id -> id.equals(paymentResult.getPaymentId()))) {
                if (StringUtils.isNotEmpty(validC2C(userDO))) {
                    log.info("极速支付:{}不满足充值条件",paymentResult.getPaymentName());
                    continue;
                }
            }
            //支付方式对应所有商户
            for (GlPaymentMerchantResult payment : resultList) {
                GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(payment.getMerchantId());
                payment.setSuccessRate(0);
                payment.setLeftAmount(0L);
                payment.setIsFull(false);
                if (merchantaccount != null) {
                    // 剩余收款金额
                    payment.setLeftAmount(merchantaccount.getDailyLimit().longValue());
                    if (null != merchantaccount.getSuccessAmount()) {
                        payment.setLeftAmount(merchantaccount.getDailyLimit() - merchantaccount.getSuccessAmount());
                    }
                    String key = String.format(KeyConstant.FUND_RECHARGE_ROTATION, payment.getAppId(), cycleStart.getTime());
                    String count = redisService.get(key);
                    if (StringUtils.isNotEmpty(count) && Integer.parseInt(count) >= payment.getCycleCount()) {
                        payment.setIsFull(true);
                    }
                    //极速充值设置快捷金额
                    if (merchantaccount.getChannelId().equals(FundConstant.PaymentChannel.C2CPay)) {
                        payment.setQuickAmount(configDO.getChooseAmounts());
                    }
                }
                //过滤商户余额超过收款限额
                if (checkLimitAmount(payment.getMerchantCode(), payment.getLimitAmount())) {
                    payment.setIsFull(true);
                }
                //过滤VIP等级
                if (!CollectionUtils.isEmpty(payment.getVipLevel()) && !ObjectUtils.isEmpty(userDO.getVipLevel())
                        && !payment.getVipLevel().contains(userDO.getVipLevel().toString())) {
                    payment.setIsFull(true);
                }
                //轮询时间内已进单量
                String key = String.format(KeyConstant.FUND_RECHARGE_ROTATION, payment.getAppId(), cycleStart.getTime());
                String orderCount = redisService.get(key);
                payment.setActualOrder(orderCount == null ? 0: Integer.valueOf(orderCount));
            }
            // 按照充值优先级排序、过滤掉今日剩余收款金额小于0的通道,当前轮训周期内订单数量已满商户
            Function<GlPaymentMerchantResult, Integer> function = p -> -p.getCyclePriority();
            resultList = resultList.stream()
                    .filter(item -> item.getLeftAmount() > 0)
                    .filter(item ->  !item.getIsFull())
                    .sorted(Comparator.comparing(GlPaymentMerchantResult::getCyclePriority, Comparator.reverseOrder())
                            .thenComparing(GlPaymentMerchantResult::getActualOrder)
                            .thenComparing(GlPaymentMerchantResult::getLeftAmount,Comparator.reverseOrder())).collect(Collectors.toList());

            int i = 1;
            for (GlPaymentMerchantResult result : resultList) {
                result.setAisleName("通道" + StringEncryptor.toCH(i));
                i++;
            }
            paymentResult.setMerchantList(resultList);
            results.add(paymentResult);
        }
        //无商户的支付方式不返回
        results = results.stream().filter(item -> !CollectionUtils.isEmpty(item.getMerchantList())).collect(Collectors.toList());
        return results;
    }


    /**
     *     过滤商户余额超过收款限额
     */
    public boolean checkLimitAmount(String merchantCode, BigDecimal limitAmount) throws GlobalException {
        AtomicReference<Boolean> result = new AtomicReference<>(false);
        List<GlWithdrawMerchantAccount> accounts = glWithdrawMerchantAccountBusiness.findAllBy("merchantCode",merchantCode);
        accounts.stream().filter( obj -> obj.getStatus().intValue() == 0)
                .filter(obj -> obj.getOpenStatus().intValue() == 0)
                .forEach(obj -> {
                    BigDecimal balance = glWithdrawBusiness.queryAccountBalance(obj.getMerchantId());
                    if (balance.compareTo(limitAmount) > 0) {
                        result.set(true);
                    }
                });
        return result.get();
    }


    /**
     * 根据商户优化级配置获取商户应用id
     * @param merchangAppDO
     * @return
     */
    public Integer getMerchantAppId(RechargeMerchangAppDO merchangAppDO) {
        List<GlPaymentMerchantResult> resultList = new ArrayList<>();
        try {
            List<GlPaymentResult> paymentResults = new ArrayList<>();
            // 获取对应用户商户信息
            if (merchangAppDO.getLimitType() == FundConstant.PaymentCache.NORMAL) {
                paymentResults =
                        glPaymentMerchantAppBusiness.getPaymentCache(merchangAppDO.getLevelId(),merchangAppDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
            } else if (merchangAppDO.getLimitType() == FundConstant.PaymentCache.LARGE) {
                paymentResults = glPaymentMerchantAppBusiness.getPaymentLargeCache(merchangAppDO.getLevelId(),merchangAppDO.getHeaderOsType() == ProjectConstant.OSType.PC ? 0 : 1);
                // 大额充值
                if (!ObjectUtils.isEmpty(paymentResults)) {
                    paymentResults.forEach(r -> r.setLimitType(FundConstant.PaymentCache.LARGE));
                }
            }

            //充值轮训周期时间（单位为分）
            Integer cycleTime = redisService.get(RedisKeyHelper.RECHARGE_CYCLE_TIME,Integer.class);
            Date now = new Date();
            //当前商户当前周期内订单数量是否超过商户充值周期处理量
            Date cycleStart = getCycleStart(cycleTime, now);
            //付款人姓名类型
            NameTypeEnum nameTypeEnum = glFundBusiness.getNameType(merchangAppDO.getName());
            for (GlPaymentResult paymentResult : paymentResults) {
                if (ObjectUtils.isEmpty(paymentResult.getMerchantList()) || paymentResult.getPaymentId().intValue() != merchangAppDO.getPaymentId().intValue()) {
                    continue;
                } else {
                    paymentResult.getMerchantList().stream().forEach(item -> {
                        //轮询时间内已进单量
                        String key = String.format(KeyConstant.FUND_RECHARGE_ROTATION, item.getAppId(), cycleStart.getTime());
                        String orderCount = redisService.get(key);
                        item.setActualOrder(orderCount == null ? 0: Integer.valueOf(orderCount));

                        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.getMerchantAccountCache(item.getMerchantId());
                        if (Objects.equals(FUND_COMMON_ON, merchantAccount.getEnableScript())
                            || merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
                            GlPaymentHandler handler = getPaymentHandler(merchantAccount);
                            if (null != handler) {
                                item.setInnerPay(handler.innerPay(merchantAccount, paymentResult.getPaymentId()));
                            }
                        }
                        item.setSuccessRate(0);
                        item.setLeftAmount(0L);
                        item.setIsFull(false);
                        if (merchantAccount != null) {
                            // 剩余收款金额
                            item.setLeftAmount(merchantAccount.getDailyLimit().longValue());
                            if (null != merchantAccount.getSuccessAmount()) {
                                item.setLeftAmount(merchantAccount.getDailyLimit() - merchantAccount.getSuccessAmount());
                            }
                        }
                    });
                    //按优先级，实际进单量，剩余额度排序
                    resultList = paymentResult.getMerchantList().stream()
                            .filter(item -> item.getCoin().equals(merchangAppDO.getCoin()))
                            .filter(item ->  item.getInnerPay().toString().equals(merchangAppDO.getInnerPay().toString()))
                            .filter(item -> merchangAppDO.getAmount().compareTo(item.getMinAmount()) >= 0
                                    && merchangAppDO.getAmount().compareTo(item.getMaxAmount()) <= 0)
                            .filter(item ->  item.getLeftAmount() > 0)
                            .sorted(Comparator.comparing(GlPaymentMerchantResult::getCyclePriority, Comparator.reverseOrder())
                                    .thenComparing(GlPaymentMerchantResult::getActualOrder)
                                    .thenComparing(GlPaymentMerchantResult::getLeftAmount,Comparator.reverseOrder())).collect(Collectors.toList());


                    //过滤VIP等级
                    if (!ObjectUtils.isEmpty(merchangAppDO.getVipLevel())) {
                        resultList = resultList.stream().filter(item ->  item.getVipLevel().contains(merchangAppDO.getVipLevel().toString()))
                                .collect(Collectors.toList());
                    }
                }

                //是否选择商户
                Boolean flag = false;
                for (GlPaymentMerchantResult payment : resultList) {

                    //处理上线UAT环境缓存数据
                    if (ObjectUtils.isEmpty(payment.getCycleCount()) || ObjectUtils.isEmpty(payment.getCyclePriority())) {
                        payment.setCycleCount(0);
                        payment.setCyclePriority(0);
                    }
                    //过滤商户余额超过收款限额
                    if (checkLimitAmount(payment.getMerchantCode(), payment.getLimitAmount())) {
                        payment.setIsFull(true);
                    }
                    //过滤姓名类型
                    if (nameTypeEnum != NameTypeEnum.ALL
                            && !payment.getNameType().contains(NameTypeEnum.ALL.getType().toString())
                            && !payment.getNameType().contains(nameTypeEnum.getType().toString())) {
                        payment.setIsFull(true);
                    }
                    if (flag) {
                        continue;
                    }
                    String key = String.format(KeyConstant.FUND_RECHARGE_ROTATION, payment.getAppId(), cycleStart.getTime());
                    Long count = redisService.incrBy(key, 1);
                    if (count.intValue() > payment.getCycleCount().intValue()) {
                        payment.setIsFull(true);
                        count = (long)payment.getCycleCount();
                    } else {
                        flag = true;
                    }
                    int expireTime =cycleTime * 60 - (DateUtils.diffSecond(cycleStart, now));
                    log.info("getMerchantAppId_key:{},count:{},paymentCount:{},now:{},expireTime:{}",key,count,payment.getCycleCount(), DateUtils.format(now, DateUtils.YYYY_MM_DD_HH_MM_SS),expireTime);
                    redisService.set(key, count, expireTime);
                    payment.setActualOrder(count.intValue());
                }

                // 按照充值优先级排序、过滤掉今日剩余收款金额小于0的通道,当前轮训周期内订单数量已满商户
                resultList = resultList.stream().filter(item ->  !item.getIsFull()).collect(Collectors.toList());
            }
        } catch (Exception e) {
            log.error("find_Rotation_appId_error{}", e);
        }
        if (CollectionUtils.isEmpty(resultList)) {
            return null;
        } else {
            return resultList.get(0).getAppId();
        }
    }

    public String validC2C(GlUserDO userDO) throws GlobalException {
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (configDO == null) {
            return "极速转帐充值配置错误，请联系客服处理";
        }
        if (!configDO.getRechargeUserLevels().contains(userDO.getUserFundLevelId())) {
            return "您的层级等级不匹配，请选择其他充值方式";
        }
        RPCResponse<Boolean> rpcResponse = seoTeamService.seoUser(userDO.getId());
        if (rpcResponse == null || rpcResponse.getData() == null) {
            throw new GlobalException("查询用户是否为seo用户异常");
        }
        if (rpcResponse.getData() && !CollectionUtils.isEmpty(configDO.getRechargeSeoVipLevels())
            &&!configDO.getRechargeSeoVipLevels().contains(userDO.getVipLevel())) {
            return "您的VIP等级不匹配，请选择其他充值方式";
        } else if (rpcResponse.getData() == false && !configDO.getRechargeVipLevels().contains(userDO.getVipLevel())) {
            return "您的VIP等级不匹配，请选择其他充值方式";
        }
        RPCResponse<GlUserLockDo> userDetailResp = userRechargeService.findByUserId(userDO.getId());
        if (RPCResponseUtils.isFail(userDetailResp)) {
            throw new GlobalException("获取用户充值锁定状态异常");
        }
        GlUserLockDo rechargeDo = RPCResponseUtils.getData(userDetailResp);
        if (rechargeDo != null && rechargeDo.getLockStatus() == 1) {
            return "当前用户极速充值已锁定，请先解锁";
        }

        //过滤充值次数
        Integer failCount = 0;
        Date now = new Date();
        RechargeQueryDto queryDto = new RechargeQueryDto();
        queryDto.setChannelId(FundConstant.PaymentChannel.C2CPay);
        queryDto.setStartTime(com.seektop.common.utils.DateUtils.getMinTime(now));
        queryDto.setEndTime(com.seektop.common.utils.DateUtils.getMaxTime(now));
        queryDto.setUserId(userDO.getId());
        queryDto.setDateType(1);
        queryDto.setSize(100);
        List<GlRecharge> recharges = glRechargeMapper.findRechargePageList(queryDto);
        if (!CollectionUtils.isEmpty(recharges)) {
            Map<Integer, List<GlRecharge>> map = recharges.stream().filter(obj -> null != obj).collect(Collectors.groupingBy(GlRecharge::getStatus));
            failCount = map.get(new Integer(FundConstant.RechargeStatus.FAILED)) == null ? 0 : map.get(new Integer(FundConstant.RechargeStatus.FAILED)).size();
            if (configDO.getRechargeDailyCancelLimit() <= failCount.intValue()) {
                return "今日已取消的极速转帐的充值次数已达上限，请选择其他充值方式";
            }
            if (configDO.getRechargeDailyUseLimit() <= recharges.size()) {
                return "今日极速转帐的充值次数已达上限，请选择其他充值方式";
            }
        }
        return null;
    }



    /**
     * 充值handler
     *
     * @param paymentMerchantaccount 入款商户信息
     * @return
     */
    public GlPaymentHandler getPaymentHandler(GlPaymentMerchantaccount paymentMerchantaccount) {
        // 若入款商户没有启用动态脚本，按原流程执行
        if (Objects.equals(FUND_COMMON_ON, paymentMerchantaccount.getEnableScript())) {
            // 若入款商户启用了动态脚本，调用GroovyScriptPayer
            return glPaymentHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glPaymentHandlerMap.get(paymentMerchantaccount.getChannelId().toString());
        }
    }

    public static Date getCycleStart(Integer cycleTime, Date now) {
        Date startDate = null;
        try {
            Date dailyStartDate = DateUtils.getMinTime(now);
            Integer mins = DateUtils.diffMins(dailyStartDate, now) % cycleTime;
            if (mins.intValue() > 0) {
                startDate = org.apache.commons.lang3.time.DateUtils.addMinutes(now, -mins);
            } else {
                startDate = now;
            }
            startDate = DateUtils.toDate(DateUtils.format(startDate,"yyyy-MM-dd HH:mm"), "yyyy-MM-dd HH:mm");
        } catch (Exception e) {
            log.error("获取充值周期开始时间错误", e);
        }
        return startDate;
    }

    private static Date getCycleEnd(Integer cycleTime,Date now) {
        Date endDate = null;
        try {
            Date dailyStartDate = DateUtils.getMinTime(now);
            Integer diffTime = DateUtils.diffMins(dailyStartDate, now) % cycleTime;
            if (diffTime.intValue() > 0) {
                endDate = org.apache.commons.lang3.time.DateUtils.addMinutes(now, (cycleTime - diffTime - 1));
            } else {
                endDate = org.apache.commons.lang3.time.DateUtils.addMinutes(now, cycleTime - 1);
            }
            endDate = DateUtils.toDate(DateUtils.format(endDate, "yyyy-MM-dd HH:mm") + ":59", DateUtils.YYYY_MM_DD_HH_MM_SS);
        } catch (Exception e) {
            log.error("获取充值周期开始时间错误", e);
        }
        return endDate;
    }


    /**
     * 黑名单检测
     *
     * @param userDO
     * @param ip
     */
    public void checkBlackMonitor(GlUserDO userDO, String ip) {
        //获取用户所有的银行卡
        List<GlWithdrawUserBankCard> userCardList = glWithdrawUserBankCardBusiness.findUserCards(userDO.getId());
        List<String> bankcards = new ArrayList<>();
        for (GlWithdrawUserBankCard userBankCard : userCardList) {
            bankcards.add(userBankCard.getCardNo());
        }

        //V2 黑名单需求添加 2.异步校验是否监控
        String bannedOnOff = redisService.get(RedisKeyHelper.BLACK_ONOFF);
        if (StringUtils.isNotEmpty(bannedOnOff)) {
            //开关打开 进行黑名单监控功能
            if ("1".equals(bannedOnOff)) {
                BlackMonitorDO blackMonitorDO = new BlackMonitorDO();
                blackMonitorDO.setUser(userDO);
                blackMonitorDO.setBankCards(bankcards);
                blackMonitorDO.setLoginIp(ip);
                blackMonitorDO.setBehaviorType(ProjectConstant.BlackBehavior.RECHARGE_CHANGE_LEVEL);
                blackService.checkBlackMonitor(blackMonitorDO);
            }
        }
    }

    /**
     * 生成附言
     */
    public String getKeyword(String username) {
        Date now = new Date();
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        String redisKey = "keywords@" + sdf.format(now);
        String key = redisService.spop(redisKey);
        if (StringUtils.isEmpty(key)) {
            doKeywordsGeneration();
            key = redisService.spop(redisKey);
        }
        return NumStringUtils.getFixedLowerString(username, 1) + key;
    }

    private void doKeywordsGeneration() {
        int expire = 48 * 60 * 60;
        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMdd");
        Date now = new Date();
        Calendar c = org.apache.commons.lang3.time.DateUtils.toCalendar(now);
        int day = c.get(Calendar.DAY_OF_WEEK);
        String today = "keywords@" + sdf.format(now);
        String tomorrow = "keywords@" + sdf.format(org.apache.commons.lang3.time.DateUtils.addDays(now, 1));
        if (!redisService.exists(today)) {
            ArrayList<String> dataList = new ArrayList<>();
            for (int i = 100000 * day; i <= 100000 * day + 100000; i++) {
                dataList.add(NumStringUtils.numToFixedLowerString(i, 4));
            }
            Collections.shuffle(dataList);
            redisService.delete(today);
            for (String data : dataList) {
                redisService.sadd(today, data, expire);
            }
        }
        if (!redisService.exists(tomorrow)) {
            ArrayList<String> dataList = new ArrayList<>();
            for (int i = 100000 * (day == 7 ? 1 : day + 1); i <= 100000 * (day == 7 ? 1 : day + 1) + 100000; i++) {
                dataList.add(NumStringUtils.numToFixedLowerString(i, 4));
            }
            Collections.shuffle(dataList);
            redisService.delete(tomorrow);
            for (String data : dataList) {
                redisService.sadd(tomorrow, data, expire);
            }
        }
    }

    /**
     * 充值订单回调
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     * @throws GlobalException
     */
    public RechargeNotify doPaymentNotify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment,
                                          Map<String, String> resMap) throws GlobalException {
        try {
            GlPaymentRechargeHandler handler = glPaymentManagerHandle.getRechargeHandler(payment);
            if (handler == null) {
                log.warn("no_handler_for_recharge_channel:{}.", payment.getChannelName());
                return null;
            }
            RechargeNotify notify = handler.notify(merchant, payment, resMap);
            if (notify == null || StringUtils.isEmpty(notify.getOrderId())) {
                log.info("@@@@ tradeId[1]:" + (notify == null ? "pay is null" : JSON.toJSONString(notify)));
                return null;
            }
            GlRecharge req = findById(notify.getOrderId());
            if (req == null) {
                log.info("@@@@ tradeId[2]:" + (req == null ? "req is null" : JSON.toJSONString(req)));
                return null;
            }
            // 如果是优付支付，充值成功后金额从充值记录表取值
            if (FundConstant.PaymentChannel.YOMPAY == payment.getChannelId()
                    || FundConstant.PaymentChannel.JUHUIPAY == payment.getChannelId()
                    || FundConstant.PaymentChannel.JUHUIDALIYUAN == payment.getChannelId()) {
                notify.setAmount(req.getAmount());
            }
            if (notify.getAmount() == null) {
                log.info("@@@@ tradeId[3]:" + "payAmount is null");
                return null;
            }
            return notify;
        } catch (Exception e) {
            log.error("doPaymentNotify_error", e.getMessage());
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 查询充值订单
     *
     * @param recharge
     * @return
     * @throws GlobalException
     */
    public RechargeNotify doRechargeOrderQuery(GlRecharge recharge) throws GlobalException {
        GlPaymentMerchantaccount merchantaccount = glPaymentMerchantaccountBusiness.findById(recharge.getMerchantId());
        if (null == merchantaccount) {
            return null;
        }
        GlPaymentRechargeHandler handler = glPaymentManagerHandle.getRechargeHandler(merchantaccount);
        if (handler == null) {
            log.info("doRechargeAutoQuery_handler_isNull_orderId", recharge.getOrderId());
            return null;
        }
        String orderId = recharge.getOrderId();

        return handler.query(merchantaccount, orderId);
    }


    public List<GlRechargeCollect> getMemberRechargeTotal(RechargeQueryDto queryDto) {
        List<GlRechargeCollect> List = Lists.newArrayList();
        try {
            this.setStatus(queryDto);
            //Size = 14 查询全部充值方式
            if (null != queryDto.getPaymentIdList()
                    && queryDto.getPaymentIdList().size() == 14) {
                queryDto.setPaymentIdList(null);
            }

            //Size = 4 查询全部应用端充值数据
            if (null != queryDto.getClientTypeList() &&
                    queryDto.getClientTypeList().size() == 4) {
                queryDto.setClientTypeList(null);
            }
            List = glRechargeMapper.memberRechargeTotal(queryDto);

        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }
        return List;
    }

    private void setStatus(RechargeQueryDto queryDto) {
        if (null != queryDto.getOrderStatus()
                && queryDto.getOrderStatus().size() != 0
                && queryDto.getOrderStatus().size() != 8) {
            if (queryDto.getDateType() == 1) {//根据充值时间查询
                List<Integer> status = new ArrayList<>();
                List<Integer> subStatus = new ArrayList<>();
                queryDto.getOrderStatus().stream().forEach(item -> {
                    switch (item) {
                        case 0: // 待支付
                            status.add(ProjectConstant.RechargeStatus.PENDING);
                            break;
                        case 1: // 支付成功
//                            status.add(ProjectConstant.RechargeStatus.SUCCESS);
                            subStatus.add(1);
                            break;
                        case 2: // 补单审核成功
                            subStatus.add(2);
                            break;
                        case 3: // 补单审核拒绝
                            subStatus.add(3);
                            break;
                        case 4: //人工拒绝补单
                            subStatus.add(4);
                            break;
                        case 5: //补单审核中
                            status.add(ProjectConstant.RechargeStatus.REVIEW);
                            break;
                        case 6: //用户撤销
                            subStatus.add(5);
                            break;
                        case 7: //超时撤销
                            subStatus.add(6);
                            break;
                    }
                });

                // 单独过滤支付成功
                if (status.size() == 1 && status.get(0) == ProjectConstant.RechargeStatus.SUCCESS
                        && subStatus.size() == 1 && subStatus.get(0) == 1) {
                    queryDto.setSubStatus(subStatus);
                } else {
                    queryDto.setStatus(status);
                    queryDto.setSubStatus(subStatus);
                }
            } else if (queryDto.getDateType() == 2) { //根据到账时间查询
                queryDto.setSuccStatus(-1);
                if (queryDto.getOrderStatus().contains(ProjectConstant.RechargeStatus.SUCCESS) &&
                        !queryDto.getOrderStatus().contains(2)) { //单独查询支付成功
                    queryDto.setSuccStatus(1);
                } else if (!queryDto.getOrderStatus().contains(ProjectConstant.RechargeStatus.SUCCESS) &&
                        queryDto.getOrderStatus().contains(2)) {//单独查询补单审核成功
                    queryDto.setSuccStatus(2);
                }
            }
        }
    }

//    public boolean isFirst(Integer userId) {
//        return glRechargeMapper.isFirst(userId);
//    }

    public boolean hasDigitalPaySuccess(Integer userId) {
        return glRechargeMapper.hasDigitalPaySuccess(userId);
    }




    public List<RechargeMonitorRetResult> rechargeMonitor(MerchantAccountMonitorDO monitorDO) throws GlobalException {
        //设置接口同一ip 30S 内只能请求一次
        if (!ObjectUtils.isEmpty(redisService.get("RECHARGE_STATUS_MONITOR_LIMIT" + monitorDO.getIp()))) {
            throw new GlobalException("访问频率超限，请稍后再试");
        } else {
            redisService.set("RECHARGE_STATUS_MONITOR_LIMIT" + monitorDO.getIp(), "1", 30);
        }
        String key = "f22fd80be7ce4208b110ea9afd0c8e99";
        String verifySign = MD5.md5(key + monitorDO.getTimeStamp());

        if (!verifySign.equals(monitorDO.getSign())) {
            throw new GlobalException("验签失败");
        }
        if (monitorDO.getMinuteDiff() < 0) {
            throw new GlobalException("minuteDiff参数错误");
        }

        List<GlPaymentChannel> channelList = glPaymentChannelBusiness.findAll();
        if (channelList == null ) {
            return Collections.emptyList();
        }
        Map<Integer,String> channelMap = new HashMap<>(channelList.size());
        channelList.stream().forEach( a -> {
            channelMap.put(a.getChannelId(),a.getChannelName());
        });
        List<Integer> channelIdList = channelList.stream().map( channel -> channel.getChannelId()).collect(Collectors.toList());


        List<GlPaymentMerchantApp> merchantAppList = glPaymentMerchantAppBusiness.getActivateMerchant();
        List<Integer> channelIdActiveList = new ArrayList<>();
        if (merchantAppList != null && merchantAppList.size() > 0) {
            channelIdActiveList = merchantAppList.stream().map(app -> app.getChannelId()).collect(Collectors.toList());
        }

        Date now = new Date();
        Date beforeDiff = null;
        try {
            beforeDiff = DateUtils.addMin(-monitorDO.getMinuteDiff(), now);
        } catch (ParseException e) {
            throw new GlobalException(e.getMessage(), e);
        }
        List<RechargeMonitorRetResult> retList = new ArrayList<>();
        if (channelIdActiveList != null && channelIdActiveList.size() > 0) {
            for (int i = 0; i < channelIdActiveList.size(); i++) {
                Integer channelId = channelIdActiveList.get(i);
                List<RechargeMonitorResult> orderFailList = glRechargeErrorBusiness.getRecentHundredErrorOrder(channelId, beforeDiff);

                MerchantAccountListDO dto = new MerchantAccountListDO();
                dto.setChannelId(channelId);
                dto.setPage(1);
                dto.setSize(100);
                PageInfo<GlPaymentMerchantaccount> result = glPaymentMerchantaccountBusiness.findSuccessRateList(dto);
                List<GlPaymentMerchantaccount> cacheInfoList = result.getList();
                RechargeMonitorRetResult ret = new RechargeMonitorRetResult();
                ret.setChannelId(channelId);
                ret.setChannelName(channelMap.get(channelId));
                //开启状态
                ret.setOpenStatus(1);
                if (!ObjectUtils.isEmpty(orderFailList)) {
                    Integer failOrderCount = orderFailList.size();
                    Integer failOrderThird = 0;
                    Integer failOrderSystem = 0;
                    for (int j = 0; j < orderFailList.size(); j++) {
                        RechargeMonitorResult monitor = orderFailList.get(j);
                        if (monitor.getRetStatus() == FundConstant.RechargeErrorStatus.PAYMENT) {
                            failOrderThird++;
                        } else if (monitor.getRetStatus() == FundConstant.RechargeErrorStatus.SYSTEM) {
                            failOrderSystem++;
                        }
                    }
                    ret.setFailOrderCount(failOrderCount);
                    ret.setFailOrderSystem(failOrderSystem);
                    ret.setFailOrderThird(failOrderThird);
                }
                if (!ObjectUtils.isEmpty(cacheInfoList)) {
                    List<RechargeSuccessRateResult> successRateList = new ArrayList<>();
                    for (int k = 0; k < cacheInfoList.size(); k++) {
                        RechargeSuccessRateResult sucRateObj = new RechargeSuccessRateResult();
                        GlPaymentMerchantaccount merchantaccount = cacheInfoList.get(k);
                        sucRateObj.setMerchantCode(merchantaccount.getMerchantCode());
                        sucRateObj.setPayType(merchantaccount.getLimitType());
                        sucRateObj.setSuccessRate(1.0 * merchantaccount.getSuccessRate() / 10000);
                        successRateList.add(sucRateObj);
                    }
                    ret.setSuccessRateList(successRateList);
                }
                retList.add(ret);
            }
        }
        //未启用通道
        for (int i = 0; i < channelIdList.size(); i++) {
            if (!channelIdActiveList.contains(channelIdList.get(i))) {
                Integer channelId = channelIdList.get(i);
                MerchantAccountListDO dto = new MerchantAccountListDO();
                dto.setChannelId(channelId);
                dto.setPage(1);
                dto.setSize(100);
                PageInfo<GlPaymentMerchantaccount> result = glPaymentMerchantaccountBusiness.findSuccessRateList(dto);
                List<GlPaymentMerchantaccount> cacheInfoList = result.getList();
                RechargeMonitorRetResult ret = new RechargeMonitorRetResult();
                ret.setChannelId(channelId);
                ret.setChannelName(channelMap.get(channelId));
                ret.setOpenStatus(2);
                List<RechargeSuccessRateResult> successRateList = new ArrayList<>();
                if (!ObjectUtils.isEmpty(cacheInfoList)) {
                    for (int j = 0; j < cacheInfoList.size(); j++) {
                        RechargeSuccessRateResult sucRateObj = new RechargeSuccessRateResult();
                        GlPaymentMerchantaccount merchantaccount = cacheInfoList.get(j);
                        sucRateObj.setMerchantCode(merchantaccount.getMerchantCode());
                        sucRateObj.setPayType(merchantaccount.getLimitType());
                        sucRateObj.setSuccessRate(1.0 * merchantaccount.getSuccessRate() / 10000);
                        successRateList.add(sucRateObj);
                    }
                    ret.setSuccessRateList(successRateList);
                }
                retList.add(ret);
            }
        }
        return retList;
    }

    /**
     * 根据支付方式，充值金额增加随机数
     * @return
     */
    public RechargeAmountResult getAmount(RechargeAmountForm form) {
        Integer paymentId = getPaymentId(form.getPaymentId());
        BigDecimal amount = form.getAmount();
        boolean exist = false;
        if (randomAmountPaymentIds.stream().anyMatch(id -> id.equals(paymentId))) {
            GlPaymentMerchantApp app = glPaymentMerchantAppBusiness.findById(form.getMerchantAppId());
            if (!ObjectUtils.isEmpty(app)) {
                Integer count = glRechargeMapper.queryPendingCount(paymentId, app.getMerchantId(), amount);
                if (count > 0) {
                    exist = true;
                    BigDecimal random = BigDecimal.valueOf(RandomUtils.nextInt(1, 11));
                    BigDecimal randomAmount = amount.add(random);
                    GlPaymentMerchantFee fee = paymentMerchantFeeBusiness.findFee(app.getLimitType(), app.getMerchantId(), paymentId);
                    if (!ObjectUtils.isEmpty(fee) && randomAmount.compareTo(fee.getMaxAmount()) > 0) {
                        randomAmount = amount.subtract(random);
                    }
                    if (randomAmount.compareTo(BigDecimal.ZERO) > 0) {
                        amount = randomAmount;
                    }
                }
            }
        }
        RechargeAmountResult result = new RechargeAmountResult();
        result.setExist(exist);
        result.setAmount(amount);
        return result;
    }

    /**
     * 撮合系统根据金额匹配订单
     * @return
     */
    public C2CRechargeOrderMatchResult getC2CAmount(RechargeAmountForm form, GlUserDO userDO) throws GlobalException {
        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);
        if (!configDO.getChooseAmounts().stream().anyMatch(obj -> BigDecimal.valueOf(obj).equals(form.getAmount()))) {
            throw new GlobalException("充值金额错误：请选择页面充值金额");
        }
        C2CRechargeOrderMatchResult result = new C2CRechargeOrderMatchResult();
        result.setMatchedResult(0);
        if (c2CPaymentIds.stream().anyMatch(id -> id.equals(form.getPaymentId()))) {
            GlPaymentMerchantApp app = glPaymentMerchantAppBusiness.findById(form.getMerchantAppId());
            if (!ObjectUtils.isEmpty(app)) {
                result = c2COrderCallbackHandler.rechargeMatch(userDO, form.getAmount(), form.getRequestIp());
                UserVIPCache userVIPCache = userVipUtils.getUserVIPCache(userDO.getId());
                RPCResponse<BigDecimal> response =
                        rechargeWithdrawTempleService.rechargeRate(form.getPaymentId(), userVIPCache == null ? 0 :userVIPCache.getVipLevel());
                if (response != null
                        && (result.getMatchedResult() == 1 || result.getMatchedResult() == 2)) {
                    BigDecimal awardAmount = response.getData().multiply(form.getAmount()).setScale(2, RoundingMode.DOWN);
                    if (result.getMatchedResult() == 2) {
                        awardAmount = response.getData().multiply(result.getRecommendAmount()).setScale(2, RoundingMode.DOWN);
                    }
                    result.setAwardAmount(awardAmount);
                }
            }
        }
        return result;
    }



    /**
     * 获取订单 补单时 实际汇率
     * @param recharge
     * @return
     */
    public BigDecimal getPaymentUSDTRate(GlRecharge recharge) {
        BigDecimal paymentUSDTRate = null;
        GlPaymentMerchantaccount merchantAccount = glPaymentMerchantaccountBusiness.findById(recharge.getMerchantId());
        if (Objects.equals(FUND_COMMON_ON, merchantAccount.getEnableScript()) || merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
            GlPaymentHandler handler = glPaymentManagerHandle.getPaymentHandler(merchantAccount);
            if (null != handler) {
                if (recharge.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY) {
                    //优化取渠道USDT汇率
                    paymentUSDTRate = handler.paymentRate(merchantAccount, recharge.getPaymentId());
                    if (ObjectUtils.isEmpty(paymentUSDTRate) || !BigDecimalUtils.moreThanZero(paymentUSDTRate)) {
                        paymentUSDTRate = glPaymentMerchantaccountBusiness.getPaymentUSDTRate();
                    }
                }
            }
        }
        return paymentUSDTRate;
    }

    /**
     * 极速支付方式转为普通充值方式
     * @param paymentId
     * @return
     */
    public Integer getPaymentId(Integer paymentId) {
        Integer result = paymentId;
        switch (paymentId){
            case FundConstant.PaymentType.CTOC_BANK_PAY:
                result = FundConstant.PaymentType.BANKCARD_TRANSFER;
                break;
            case FundConstant.PaymentType.CTOC_ALI_PAY:
                result = FundConstant.PaymentType.BANKCARD_TRANSFER;
                break;
            case FundConstant.PaymentType.CTOC_WECHAT_PAY:
                result = FundConstant.PaymentType.BANKCARD_TRANSFER;
                break;
            default:
                return result;
        }
        return result;
    }

}


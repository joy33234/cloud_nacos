package com.seektop.fund.business.withdraw;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.seektop.agent.dto.ValidWithdrawalDto;
import com.seektop.agent.service.CommCommissionService;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.PlatformEnum;
import com.seektop.enumerate.ResultCode;
import com.seektop.enumerate.common.TransactionStatusEnum;
import com.seektop.enumerate.common.TransactionTypeEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.BankEnum;
import com.seektop.enumerate.fund.WithdrawStatusEnum;
import com.seektop.enumerate.fund.WithdrawTypeEnum;
import com.seektop.enumerate.user.UserLockTypeEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlWithdrawEffectBetBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.backend.dto.NoticeFailDto;
import com.seektop.fund.controller.backend.dto.NoticeSuccessDto;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestApproveDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawRequestDO;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawSubmitDO;
import com.seektop.fund.customer.WithdrawSender;
import com.seektop.fund.dto.result.userLevel.FundUserLevelDO;
import com.seektop.fund.handler.C2COrderCallbackHandler;
import com.seektop.fund.handler.NoticeHandler;
import com.seektop.fund.handler.UserWithdrawEffectHandler;
import com.seektop.fund.mapper.*;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.GlWithdrawHandlerManager;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawResult;
import com.seektop.report.common.BalanceRecordReport;
import com.seektop.report.fund.WithdrawMessage;
import com.seektop.report.fund.WithdrawParentOrderReport;
import com.seektop.report.fund.WithdrawReport;
import com.seektop.report.fund.WithdrawReturnReport;
import com.seektop.user.dto.GlUserLockDo;
import com.seektop.user.service.GlUserService;
import com.seektop.user.service.GlUserWithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;

@Component
@Slf4j
public class GlWithdrawTransactionalBusiness {

    @DubboReference(retries = 2, timeout = 5000)
    private GlUserService glUserService;

    @DubboReference(retries = 2, timeout = 5000)
    private CommCommissionService commCommissionService;

    @Resource
    private RedisService redisService;

    @Resource
    private ReportService reportService;

    @Resource
    private GlWithdrawMapper glWithdrawMapper;

    @Resource
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;

    @Resource
    private GlWithdrawSplitBusiness glWithdrawSplitBusiness;

    @Resource
    private WithdrawAlarmBusiness withdrawAlarmBusiness;

    @Resource
    private GlWithdrawHandlerManager glWithdrawHandlerManager;

    @Resource
    private WithdrawApiRecordBusiness withdrawApiRecordBusiness;

    @Resource
    private GlWithdrawApiLogMapper glWithdrawApiLogMapper;

    @Resource
    private GlWithdrawReturnRequestMapper glWithdrawReturnRequestMapper;

    @Resource
    private GlWithdrawReturnRequestBusiness glWithdrawReturnRequestBusiness;

    @Resource
    private GlWithdrawReturnApproveMapper glWithdrawReturnApproveMapper;

    @Resource
    private GlWithdrawApproveBusiness withdrawApproveBusiness;

    @Resource(name = "withdrawNoticeHandler")
    private NoticeHandler noticeHandler;

    @Resource(name = "withdrawReturnNoticeHandler")
    private NoticeHandler withdrawReturnNoticeHandler;

    @Resource
    private UserWithdrawEffectHandler userWithdrawEffectHandler;

    @Resource
    private GlWithdrawMerchantAccountBusiness glWithdrawMerchantAccountBusiness;

    @Resource
    private FundProxyAccountMapper fundProxyAccountMapper;

    @Resource
    private GlWithdrawReceiveInfoBusiness glWithdrawReceiveInfoBusiness;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private UserVipUtils userVipUtils;

    @Resource
    private UserFundUtils userFundUtils;
    @Autowired
    private GlWithdrawEffectBetBusiness withdrawEffectBetBusiness;
    @Resource
    private C2COrderCallbackHandler c2COrderCallbackHandler;
    @DubboReference(retries = 2, timeout = 3000)
    private GlUserWithdrawService glUserWithdrawService;
    @Resource
    private WithdrawSender withdrawSender;
    /**
     * 发送提现通知
     *
     * @param withdraw
     */
    private void doWithdrawSuccessNotice(GlWithdraw withdraw) {
        NoticeSuccessDto successDto = new NoticeSuccessDto();
        successDto.setOrderId(withdraw.getOrderId());
        successDto.setUserId(withdraw.getUserId());
        successDto.setUserName(withdraw.getUsername());
        BigDecimal amount = withdraw.getAmount().subtract(withdraw.getFee());
        successDto.setAmount(amount);
        successDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
        noticeHandler.doSuccessNotice(successDto);
    }

    /**
     * 提现订单提交
     *
     * @param userDO
     * @param withdrawList
     * @param withdrawDO
     * @param splitRule
     * @return
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public boolean doWithdraw(GlUserDO userDO, List<GlWithdraw> withdrawList, WithdrawSubmitDO withdrawDO, String splitRule) throws GlobalException {
        log.info("withdrawList:{}",JSON.toJSONString(withdrawList));
        try {
            String parentId = redisService.getTradeNo("TX");

            BigDecimal totalAmount = BigDecimal.ZERO;
            BigDecimal totalFee = BigDecimal.ZERO;
            StringBuffer subStatus = new StringBuffer();
            //是否拆单
            boolean splitStatus = withdrawList.size() > 1;

            Date now = withdrawList.get(0).getCreateDate();

            for (GlWithdraw withdraw : withdrawList) {
                String parentOrderId = withdraw.getOrderId();
                DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(userDO.getId(), DigitalCoinEnum.CNY);

                BigDecimal balance = account.getBalance().subtract(withdraw.getAmount());
                if (balance.doubleValue() < 0) {
                    return false;
                }

                //扣除用户中心钱包余额
                glFundUserAccountBusiness.addBalance(userDO.getId(), withdraw.getAmount().negate(), DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()));
                //插入提现数据
                glWithdrawMapper.insert(withdraw);
                //保存USDT出款信息
                this.saveWithdrawReceiveInfo(withdraw, userDO);

                //保存拆单记录
                if (splitStatus) {
                    parentOrderId = parentId;
                    GlWithdrawSplit glWithdrawSplit = new GlWithdrawSplit();
                    glWithdrawSplit.setOrderId(withdraw.getOrderId());
                    glWithdrawSplit.setParentId(parentId);
                    glWithdrawSplit.setAmount(withdraw.getAmount());
                    glWithdrawSplit.setSplitConfig(splitRule);
                    glWithdrawSplit.setCreateTime(withdraw.getCreateDate());
                    glWithdrawSplit.setCreator("system");
                    glWithdrawSplitBusiness.save(glWithdrawSplit);
                    // 提现父订单-子状态
                    subStatus.append("0");
                    totalAmount = totalAmount.add(withdraw.getAmount());
                    totalFee = totalFee.add(withdraw.getFee());
                } else {
                    totalAmount = withdraw.getAmount();
                }

                log.info("withdrawDO-3");

                /**
                 * 上报提现操作
                 */
                this.withdrawReport(userDO, withdraw, withdrawDO, parentOrderId, account.getBalance(), balance);
            }
            log.info("withdrawDO-4");

            /**
             * 代理提现扣除可提现额度
             */
            if (UserConstant.UserType.PROXY == userDO.getUserType()) {
                FundProxyAccount proxyAccount = fundProxyAccountMapper.selectForUpdate(userDO.getId());
                BigDecimal validWithdraw = proxyAccount.getValidWithdrawal().subtract(totalAmount);

                proxyAccount.setValidWithdrawal(validWithdraw);
                proxyAccount.setLastUpdate(now);
                fundProxyAccountMapper.updateByPrimaryKeySelective(proxyAccount);
            }
            log.info("withdrawDO-5" + splitStatus);

            // 提现拆单后上报父单信息,供资金明细查询
            if (splitStatus) {
                WithdrawParentOrderReport parentOrderReport = new WithdrawParentOrderReport();
                parentOrderReport.setUuid(parentId);
                parentOrderReport.setUid(userDO.getId());
                parentOrderReport.setUserId(userDO.getId());
                parentOrderReport.setAmount(totalAmount.multiply(new BigDecimal(100000000)).longValue());
                parentOrderReport.setFee(totalFee.multiply(new BigDecimal(100000000)).longValue());
                parentOrderReport.setDeviceId(withdrawDO.getHeaderDeviceId());
                parentOrderReport.setParentName(userDO.getParentName());
                parentOrderReport.setParentId(userDO.getParentId());
                Integer status = FundConstant.WithdrawStatus.PENDING;
                parentOrderReport.setTimestamp(now);//发起时间
                parentOrderReport.setCreateTime(now);
                parentOrderReport.setFinishTime(now);//账变时间
                parentOrderReport.setStatus(WithdrawStatusEnum.valueOf(status));// 提现状态
                parentOrderReport.setSubOrderStatus(subStatus.toString());
                parentOrderReport.setDomain(withdrawDO.getRequestUrl());
                parentOrderReport.setUserName(userDO.getUsername());
                parentOrderReport.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
                parentOrderReport.setIp(withdrawDO.getRequestIp());
                parentOrderReport.setPlatform(PlatformEnum.valueOf(withdrawDO.getHeaderOsType()));
                parentOrderReport.setType(WithdrawTypeEnum.UNKNOWN);
                parentOrderReport.setSubType("出款");
                parentOrderReport.setRegTime(userDO.getRegisterDate());
                parentOrderReport.setOrderId(parentId);
                parentOrderReport.setSplitType("1");
                parentOrderReport.setIsFake(userDO.getIsFake());
                parentOrderReport.setSubOrderNum(subStatus.length());
                parentOrderReport.setCoin(DigitalCoinEnum.CNY.getCode());
                reportService.parentOrderReport(parentOrderReport);
            }
            log.info("withdrawDO-6");

            return true;
        } catch (Exception e) {
            log.error("doWithdraw error", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }

    public void withdrawReport(GlUserDO userDO, GlWithdraw withdraw, WithdrawSubmitDO submitDO,
                               String parentOrderId, BigDecimal balanceBefore, BigDecimal balanceAfter) throws GlobalException {
        WithdrawReport report = new WithdrawReport();
        report.setUuid(withdraw.getOrderId());
        report.setUid(withdraw.getUserId());
        report.setUserId(withdraw.getUserId());
        report.setUserName(userDO.getUsername());
        report.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
        report.setParentName(userDO.getParentName());
        report.setParentId(userDO.getParentId());
        // 用户VIP等级
        UserVIPCache vipCache = userVipUtils.getUserVIPCache(userDO.getId());
        if (ObjectUtils.isEmpty(vipCache) == false) {
            report.setVipLevel(vipCache.getVipLevel());
            report.set("vipLevel", vipCache.getVipLevel());
        }
        // 用户层级
        FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(userDO.getId());
        if (ObjectUtils.isEmpty(userLevel) == false) {
            report.setUserLevel(userLevel.getLevelId());
            report.setUserLevelName(userLevel.getLevelName());
        }

        report.setOrderId(withdraw.getOrderId());
        report.setParentOrderId(parentOrderId);
        report.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setFee(withdraw.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setBalanceBefore(balanceBefore.multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setBalanceAfter(balanceAfter.multiply(BigDecimal.valueOf(100000000)).longValue());
        report.setBank(BankEnum.valueOf(withdraw.getBankId()));
        report.setCardName(withdraw.getName());
        report.setCardNo(withdraw.getCardNo());
        report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.PENDING));

        report.setDeviceId(submitDO.getHeaderDeviceId());
        report.setDomain(submitDO.getRequestUrl());
        report.setIp(submitDO.getRequestIp());
        report.setPlatform(PlatformEnum.valueOf(submitDO.getHeaderOsType()));

        report.setType(WithdrawTypeEnum.valueOf(withdraw.getWithdrawType()));
        report.setSubType("出款");
        report.setRegTime(userDO.getRegisterDate());
        RPCResponse<Long> response = commCommissionService.calcWithdrawFee(userDO.getParentId(), withdraw.getAmount(),withdraw.getCoin());
        report.setCommFee(RPCResponseUtils.getData(response));
        report.setIsFake(userDO.getIsFake());

        report.setSplitType(withdraw.getSplitStatus().toString());
        report.setAmountNet(withdraw.getAmount().subtract(withdraw.getFee()).multiply(new BigDecimal(100000000)).longValue());
        report.setTimestamp(withdraw.getCreateDate());//发起时间
        report.setCreateTime(withdraw.getCreateDate());
        report.setFinishTime(withdraw.getCreateDate());//账变时间
        report.setLastUpdate(withdraw.getLastUpdate());
        report.setCoin(withdraw.getCoin());
        //USDT提现上报参考汇率
        if (withdraw.getBankId() == FundConstant.PaymentType.DIGITAL_PAY) {
//            String result = redisService.get(RedisKeyHelper.WITHDRAW_USDT_RATE);
//            BigDecimal rate = BigDecimal.valueOf(6.8945);
//            if (StringUtils.isNotEmpty(result)) {
//                rate = new BigDecimal(result).setScale(4, RoundingMode.DOWN);
//            }
            BigDecimal rate = glWithdrawBusiness.getWithdrawRate(userDO.getUserType());

            BigDecimal userAmount = withdraw.getAmount().divide(rate, 4, RoundingMode.DOWN);

            report.setRate(rate.multiply(BigDecimal.valueOf(100000000)).longValue());
            report.setUsdtAmount(userAmount.multiply(BigDecimal.valueOf(100000000)).longValue());
        }
        reportService.withdrawReport(report);
    }

    public void saveWithdrawReceiveInfo(GlWithdraw withdraw, GlUserDO userDO) {
        if (withdraw.getBankId() != FundConstant.PaymentType.DIGITAL_PAY) {
            //银行卡提现
            return;
        }

        //USDT提现
        GlWithdrawReceiveInfo receiveInfo = new GlWithdrawReceiveInfo();
        receiveInfo.setOrderId(withdraw.getOrderId());
        receiveInfo.setProtocol(withdraw.getCardNo());
        receiveInfo.setAddress(withdraw.getAddress());
        receiveInfo.setAmount(withdraw.getAmount());
        receiveInfo.setFee(withdraw.getFee());

        BigDecimal rate = glWithdrawBusiness.getWithdrawRate(userDO.getUserType());
        if (rate.compareTo(BigDecimal.ZERO) == 1) {
            receiveInfo.setRate(rate.setScale(4, RoundingMode.DOWN));
            receiveInfo.setUsdtAmount(withdraw.getAmount().subtract(withdraw.getFee()).divide(receiveInfo.getRate(), 2, RoundingMode.DOWN));
        }
        receiveInfo.setCreateDate(withdraw.getCreateDate());
        receiveInfo.setUpdateDate(withdraw.getCreateDate());
        glWithdrawReceiveInfoBusiness.save(receiveInfo);
    }

    /**
     * 提现订单回调事物处理
     *
     * @param notify
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doWithdrawNofity(WithdrawNotify notify) throws GlobalException {
        try {
            GlWithdraw withdraw = glWithdrawMapper.selectForUpdate(notify.getOrderId());
            //对状态进行校验
            if (withdraw == null || (withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_PENDING
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.CONFIRM_PENDING
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.RECHARGE_PENDING)
                    || !notify.getMerchantId().equals(withdraw.getMerchantId())) {
                throw new GlobalException("提现订单状态异常，拒绝回调") ;
            }
            Date now = new Date();

            // 更新提现订单记录
            withdraw.setOrderId(notify.getOrderId());
            withdraw.setStatus(notify.getStatus() == 0 ? FundConstant.WithdrawStatus.SUCCESS : FundConstant.WithdrawStatus.AUTO_FAILED);
            withdraw.setLastUpdate(now);
            // 风云聚合、ST出款卡信息
            if (StringUtils.isNotEmpty(notify.getOutCardName()) && StringUtils.isNotEmpty(notify.getOutCardNo())) {
                withdraw.setTransferBankCardNo(notify.getOutCardNo());
                withdraw.setTransferBankName(notify.getOutBankFlag());
                withdraw.setTransferName(notify.getOutCardName());
            }

            withdraw.setThirdOrderId(notify.getThirdOrderId());
            glWithdrawMapper.updateWithdraw(withdraw);

            this.updateWithdrawReceice(withdraw, notify);

            GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(withdraw.getUserId()));
            if (notify.getStatus() == 0) {
                //增加商户出款金额
                addMerchantSuccess(notify.getMerchantId(), withdraw.getAmount().subtract(withdraw.getFee()));
                // 提现成功后，设置重新计算输光
                withdrawEffectBetBusiness.resetLose(withdraw.getUserId(),DigitalCoinEnum.CNY.getCode());
                //出款成功，发起通知(按照提现金额通知)
                doWithdrawSuccessNotice(withdraw);

                /**
                 * 上报提现
                 */
                WithdrawReport report = new WithdrawReport();
                report.setUuid(withdraw.getOrderId());
                report.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setCreateTime(withdraw.getCreateDate());
                report.setFinishTime(withdraw.getCreateDate());
                report.setLastUpdate(withdraw.getLastUpdate());

                report.setFee(withdraw.getFee().multiply(BigDecimal.valueOf(100000000)).longValue());
                report.setStatus(WithdrawStatusEnum.WITHDRAWN_SUCCESS);
                report.setApproveTime(withdraw.getApproveTime());
                report.setSubType("出款");
                report.setUid(glUser.getId());
                report.setUserId(glUser.getId());
                report.setUserName(glUser.getUsername());
                report.setParentId(glUser.getParentId());
                report.setParentName(glUser.getParentName());
                report.setRegTime(glUser.getRegisterDate());
                report.setUserType(UserTypeEnum.valueOf(glUser.getUserType()));
                RPCResponse<Long> response = commCommissionService.calcWithdrawFee(glUser.getParentId(), withdraw.getAmount(),withdraw.getCoin());
                report.setCommFee(RPCResponseUtils.getData(response));
                report.setTimestamp(withdraw.getCreateDate());
                report.setIsFake(glUser.getIsFake());
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(glUser.getId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(glUser.getId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                report.setCoin(withdraw.getCoin());

                reportService.withdrawReport(report);
                // 上报流水提现流水记录
                userWithdrawEffectHandler.reportWithdrawBettingBalance(withdraw.getUserId(), withdraw.getOrderId(), withdraw.getAmount(),DigitalCoinEnum.CNY.getCode());
                // 拆单父单状态上报
                updateParentOrderStatus(withdraw);


                //退回保证金
                if (withdraw.getAisleType() == FundConstant.AisleType.C2C) {
                    // 提现金额退回中心钱包
                    BigDecimal afterAmount =  glFundUserAccountBusiness.addBalance(withdraw.getUserId(), withdraw.getFee(), DigitalCoinEnum.CNY);
                    //上报ES
                    reportBalanceRecord(withdraw,afterAmount.subtract(withdraw.getFee()),  afterAmount, glUser);
                }
            }

            if (notify.getStatus() == 1) {
                //出款失败
                withdrawApiRecordBusiness.deleteById(withdraw.getOrderId());
            }
        } catch (Exception e) {
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 增加商户出款金额
     *
     * @param merchantId
     * @param amount
     */
    private void addMerchantSuccess(Integer merchantId, BigDecimal amount) {
        Date now = new Date();
        String day = DateUtils.format(now, DateUtils.YYYYMMDD);
        String key = RedisKeyHelper.WITHDRAW_SUCCESS_AMOUNT + day + "_" + merchantId;
        redisService.incrBy(key, amount.longValue());
        redisService.setTTL(key, (int) (org.apache.commons.lang3.time.DateUtils.MILLIS_PER_DAY / 1000));
    }

    private void updateParentOrderStatus(GlWithdraw withdraw) {
        if (ObjectUtils.isEmpty(withdraw) || ObjectUtils.isEmpty(withdraw.getSplitStatus())) {
            return;
        }
        if (withdraw.getSplitStatus() == 0) {
            return;
        }
        // 其他的中间条件不用上报父单最新状态
        if (withdraw.getStatus() == FundConstant.WithdrawStatus.REVIEW_HOLD
                || withdraw.getStatus() == FundConstant.WithdrawStatus.RISK_PENDING
                || withdraw.getStatus() == FundConstant.WithdrawStatus.SUCCESS_PENDING
                || withdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_FAILED
                || withdraw.getStatus() == FundConstant.WithdrawStatus.AUTO_PENDING
                || withdraw.getStatus() == FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                || withdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING
                || withdraw.getStatus() == FundConstant.WithdrawStatus.CONFIRM_PENDING
                ) {
            return;
        }
        // 获取提现拆单记录
        List<GlWithdrawSplit> glWithdrawSplits = glWithdrawSplitBusiness.findAllSplitOrderByOrderId(withdraw.getOrderId());

        if (ObjectUtils.isEmpty(glWithdrawSplits)) {
            return;
        }

        String parentOrderId = "";
        List<String> orderIds = new ArrayList<>();

        for (GlWithdrawSplit glWithdrawSplit : glWithdrawSplits) {
            parentOrderId = ObjectUtils.isEmpty(parentOrderId) ? glWithdrawSplit.getParentId() : parentOrderId;
            orderIds.add(glWithdrawSplit.getOrderId());
        }

        List<GlWithdraw> subWithdraws = glWithdrawMapper.selectByOrderIds(orderIds);

        if (ObjectUtils.isEmpty(subWithdraws)) {
            return;
        }

        StringBuffer subStatus = new StringBuffer();

        Date createDate = null;

        for (GlWithdraw subWithdraw : subWithdraws) {
            if (FundConstant.WithdrawStatus.SUCCESS == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.FORCE_SUCCESS == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.RETURN_PART == subWithdraw.getStatus()) {
                subStatus.append(1);//成功
            } else if (2 == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.RISK_REJECT  == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.RETURN_PENDING  == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.RETURN == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.RETURN_REJECT == subWithdraw.getStatus()
                    || FundConstant.WithdrawStatus.FORCE_SUCCESS_REJECT == subWithdraw.getStatus()
                    ) {
                subStatus.append(2);//失败
            } else {
                subStatus.append(0);//未处理
            }

            createDate = subWithdraw.getCreateDate();
        }

        WithdrawParentOrderReport parentOrderReport = new WithdrawParentOrderReport();
        parentOrderReport.setUuid(parentOrderId);
        parentOrderReport.setSubOrderStatus(subStatus.toString());
        parentOrderReport.setTimestamp(createDate);
        WithdrawStatusEnum status = null;

        // 父单计算提现状态
        if (subStatus.toString().contains("0")) {
            status = WithdrawStatusEnum.valueOf(0);
        } else if (subStatus.toString().contains("1") && !subStatus.toString().contains("2") && !subStatus.toString().contains("0")) {
            status = WithdrawStatusEnum.valueOf(1);
        } else if (!subStatus.toString().contains("1") && subStatus.toString().contains("2") && !subStatus.toString().contains("0")) {
            status = WithdrawStatusEnum.valueOf(2);
        } else {
            status = WithdrawStatusEnum.valueOf(3);
        }

        parentOrderReport.setStatus(status);
        reportService.parentOrderReport(parentOrderReport);
    }


    private void reportBalanceRecord(GlWithdraw withdraw, BigDecimal amountBefore, BigDecimal amountAfter, GlUserDO userDO) {
        try {
            String returnId = redisService.getTradeNo("BZJ");
            Date now =new Date();

            BalanceRecordReport recordReport = new BalanceRecordReport();
            recordReport.setOrderId(returnId);
            recordReport.setStatus(TransactionStatusEnum.SUCCESS.getValue());
            recordReport.setTransactionDate(now);
            recordReport.setTransactionType(TransactionTypeEnum.ReturnFee.getValue());
            recordReport.setTransactionAmount(withdraw.getFee().movePointRight(4).longValue());
            recordReport.setBeforeBalance(amountBefore.movePointRight(4).longValue());
            recordReport.setAfterBalance(amountAfter.movePointRight(4).longValue());
            recordReport.setFee(Long.valueOf(0));
            recordReport.setActualAmount(recordReport.getTransactionAmount());
            // 用户及基础信息
            recordReport.setUuid(returnId);
            recordReport.setUid(userDO.getId());
            recordReport.setUserName(userDO.getUsername());
            recordReport.setUserType(UserTypeEnum.valueOf(userDO.getUserType()));
            recordReport.setRegTime(userDO.getRegisterDate());
            recordReport.setIsFake(userDO.getIsFake());
            recordReport.setTimestamp(now);
            recordReport.setFinishTime(now);
            recordReport.setParentId(userDO.getParentId());
            recordReport.setParentName(userDO.getParentName());
            // 新增用户层级补充字段
            recordReport.setUserLevel(userDO.getUserFundLevelId());
            // 新增用户VIP等级补充字段
            recordReport.setVipLevel(userDO.getVipLevel());
            reportService.balanceRecordReport(recordReport);
        } catch (Exception ex) {
            log.error("reportBalanceRecord error", ex);
        }
    }

    /**
     * 调用出款API
     *
     * @param withdraw
     * @param merchantAccount
     * @param approve
     * @param remark
     */
    @Transactional(rollbackFor = Exception.class)
    public void doWithdrawApi(GlWithdraw withdraw, GlWithdrawMerchantAccount merchantAccount, String approve, String remark) throws GlobalException {
        GlWithdrawAlarm alarm = withdrawAlarmBusiness.doWithdrawCheck(withdraw);
        if (alarm != null) {
            log.error("doWithdrawApi_alarm_info:{}", JSON.toJSONString(alarm));
            alarm.setCreateTime(new Date());
            withdrawAlarmBusiness.save(alarm);
        }

        GlPaymentWithdrawHandler handler = glWithdrawHandlerManager.getPaymentWithdrawHandler(merchantAccount);
        if (handler == null) {
            log.error("no_handler_for_withdraw_channel_{}.", merchantAccount.getChannelName());
            return;
        }

        // 出款API调用记录-避免重复出款
        GlWithdrawApiRecord record = withdrawApiRecordBusiness.findById(withdraw.getOrderId());
        if (record == null) {
            record = new GlWithdrawApiRecord();
            record.setOrderId(withdraw.getOrderId());
            record.setUsername(approve);
            record.setRemark(remark);
            record.setCreateDate(new Date());
            withdrawApiRecordBusiness.save(record);
        } else {
            log.error("this_withdraw_order_has_do_transfer:{}", JSON.toJSONString(record));
            return;
        }

        WithdrawResult apiResult = new WithdrawResult();
        try {
            // 调用三方API出款
            apiResult = handler.doTransfer(merchantAccount, withdraw);
        } catch (Exception e) {
            apiResult.setValid(false);
            apiResult.setOrderId(withdraw.getOrderId());
            apiResult.setMessage("出款异常:请联系出款商户确认出款订单");
            apiResult.setReqData(merchantAccount.getChannelName() + "/" + merchantAccount.getMerchantCode());
            if (e.getMessage().length() > 65000) {
                apiResult.setResData(e.getMessage().substring(0, 65000));
            } else {
                apiResult.setResData(e.getMessage());
            }
        }

        this.doWithdrawApiTransaction(withdraw.getOrderId(), merchantAccount, approve, apiResult,withdraw.getWithdrawType());
    }

    @Transactional(rollbackFor = Exception.class)
    public void doWithdrawApiTransaction(String orderId, GlWithdrawMerchantAccount merchantAccount,
                                         String approve, WithdrawResult apiResult, Integer withdrawType) {
        log.info("doWithdrawApiTransaction_apiResult:{}", JSON.toJSONString(apiResult));

        Date now = new Date();
        GlWithdraw withdraw = glWithdrawMapper.selectForUpdate(orderId);

        // 出款调用记录
        GlWithdrawApiLog apiLog = new GlWithdrawApiLog();
        apiLog.setCreateDate(now);
        apiLog.setOrderId(withdraw.getOrderId());
        apiLog.setReqData(apiResult.getReqData());
        apiLog.setResData(apiResult.getResData());
        apiLog.setCreator(approve);
        glWithdrawApiLogMapper.insert(apiLog);

        // 调用API失败
        if (apiResult.isValid()) {
            // 出款API请求成功
            GlWithdraw glWithdraw = new GlWithdraw();
            glWithdraw.setOrderId(withdraw.getOrderId());
            glWithdraw.setApprover(approve);
            glWithdraw.setApproveTime(now);
            glWithdraw.setLastUpdate(now);
            glWithdraw.setMerchant(merchantAccount.getChannelName());
            glWithdraw.setMerchantCode(merchantAccount.getMerchantCode());
            glWithdraw.setMerchantId(merchantAccount.getMerchantId());
            glWithdraw.setStatus(FundConstant.WithdrawStatus.AUTO_PENDING);
            glWithdraw.setRemark("代付申请成功");
            if (withdraw.getAisleType() == FundConstant.AisleType.C2C) {
                glWithdraw.setRemark("待撮合");
            }
            glWithdraw.setThirdOrderId(apiResult.getThirdOrderId());
            glWithdraw.setWithdrawType(withdrawType);
            glWithdraw.setCoin(withdraw.getCoin());

            Integer count = glWithdrawMapper.updateWithdraw(glWithdraw);
            log.info("定位提现订单是否更新count:{},withdraw:{}",count,JSON.toJSONString(glWithdraw));

            //USDT提现更新
            GlWithdrawReceiveInfo receiveInfo = this.updateWithdrawReceice(withdraw, apiResult);

            // 开始上报
            WithdrawReport report = new WithdrawReport();
            report.setUuid(withdraw.getOrderId());
            report.setOrderId(withdraw.getOrderId());
            report.setMerchantCode(merchantAccount.getMerchantCode());
            report.setChannelId(merchantAccount.getChannelId());
            report.setChannelName(merchantAccount.getChannelName());
            report.setTimestamp(withdraw.getCreateDate());
            report.setLastUpdate(withdraw.getLastUpdate());
            // 用户VIP等级
            UserVIPCache vipCache = userVipUtils.getUserVIPCache(withdraw.getUserId());
            if (ObjectUtils.isEmpty(vipCache) == false) {
                report.setVipLevel(vipCache.getVipLevel());
                report.set("vipLevel", vipCache.getVipLevel());
            }
            // 用户层级
            FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(withdraw.getUserId());
            if (ObjectUtils.isEmpty(userLevel) == false) {
                report.setUserLevel(userLevel.getLevelId());
                report.setUserLevelName(userLevel.getLevelName());
            }
            if (null != receiveInfo) {
                BigDecimal usdtAmount = receiveInfo.getUsdtAmount();
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
            reportService.withdrawReport(report);

            if (DigitalCoinEnum.getCoinList().contains(glWithdraw.getCoin())) {
                //发送自动出款消息
                try {
                    WithdrawMessage message = new WithdrawMessage();
                    message.setTradeId(withdraw.getOrderId());
                    message.setMerchantId(withdraw.getMerchantId());
                    try {
                        withdrawSender.sendWithdrawMsg(message);
                    } catch (Exception e) {
                        log.error(String.format("订单号：{}", withdraw.getOrderId()), e);
                        throw new GlobalException(ResultCode.SERVER_ERROR);
                    }
                } catch (GlobalException e) {
                    e.printStackTrace();
                }
            }
        } else {
            // 删除API调用记录
            withdrawApiRecordBusiness.deleteById(withdraw.getOrderId());

            GlWithdraw dbWithdraw = new GlWithdraw();
            dbWithdraw.setOrderId(withdraw.getOrderId());
            dbWithdraw.setApprover(approve);
            dbWithdraw.setApproveTime(now);
            dbWithdraw.setLastUpdate(now);
            dbWithdraw.setMerchant(merchantAccount.getChannelName());
            dbWithdraw.setMerchantCode(merchantAccount.getMerchantCode());
            dbWithdraw.setMerchantId(merchantAccount.getMerchantId());
            dbWithdraw.setStatus(FundConstant.WithdrawStatus.AUTO_FAILED);
            dbWithdraw.setRemark(apiResult.getMessage());
            dbWithdraw.setWithdrawType(withdrawType);
            glWithdrawMapper.updateWithdraw(dbWithdraw);
        }
    }

    private GlWithdrawReceiveInfo updateWithdrawReceice(GlWithdraw withdraw, WithdrawResult apiResult) {
        if (withdraw.getBankId() != FundConstant.PaymentType.DIGITAL_PAY) {
            return null;
        }
        GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoBusiness.findById(withdraw.getOrderId());
        if (null == receiveInfo) {
            return null;
        }
        receiveInfo.setThirdOrderId(apiResult.getThirdOrderId());
        receiveInfo.setUsdtAmount(apiResult.getUsdtAmount());
        receiveInfo.setRate(apiResult.getRate());
        receiveInfo.setUpdateDate(new Date());
        glWithdrawReceiveInfoBusiness.updateByPrimaryKey(receiveInfo);
        return receiveInfo;
    }

    private GlWithdrawReceiveInfo updateWithdrawReceice(GlWithdraw withdraw, WithdrawNotify notify) {
        if (withdraw.getBankId() != FundConstant.PaymentType.DIGITAL_PAY) {
            return null;
        }
        GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoBusiness.findById(withdraw.getOrderId());
        if (null == receiveInfo) {
            return null;
        }
        receiveInfo.setThirdOrderId(notify.getThirdOrderId());
        receiveInfo.setActualUsdtAmount(notify.getActualAmount());
        receiveInfo.setActualRate(notify.getActualRate());
        receiveInfo.setUpdateDate(new Date());
        glWithdrawReceiveInfoBusiness.updateByPrimaryKey(receiveInfo);
        return receiveInfo;
    }

    /**
     * 提现订单强制成功审核
     *
     * @param approveDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doWithdrawForceSuccess(WithdrawRequestApproveDO approveDO, GlAdminDO admin) throws GlobalException {
        String orderId = approveDO.getOrderId();
        Integer status = approveDO.getStatus();
        String remark = approveDO.getRemark();
        String username = admin.getUsername();
        Date now = new Date();

        /**
         * 更新提现订单记录
         */
        GlWithdraw dbWithdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
        if (null == dbWithdraw || dbWithdraw.getStatus() != FundConstant.WithdrawStatus.SUCCESS_PENDING) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }

        try {
            dbWithdraw.setStatus(status == 1 ? FundConstant.WithdrawStatus.FORCE_SUCCESS : FundConstant.WithdrawStatus.FORCE_SUCCESS_REJECT);
            dbWithdraw.setLastUpdate(now);
            glWithdrawMapper.updateByPrimaryKeySelective(dbWithdraw);

            /**
             * 更新收款信息
             */
            if (dbWithdraw.getBankId() == FundConstant.PaymentType.DIGITAL_PAY) {
                this.updateWithdrawReceice(dbWithdraw);
            }


            /**
             * 更新提现(申请退回/强制成功)申请记录
             */
            GlWithdrawReturnRequest request = new GlWithdrawReturnRequest();
            request.setOrderId(orderId);
            request.setStatus(status == 1 ? 1 : 2);
            request.setApprover(username);
            request.setApproveTime(now);
            request.setApproveRemark(remark);
            glWithdrawReturnRequestMapper.updateByPrimaryKeySelective(request);

            /**
             * 提现审核记录入库
             */
            GlWithdrawReturnApprove approve = new GlWithdrawReturnApprove();
            approve.setCreateTime(now);
            approve.setCreator(username);
            approve.setOrderId(orderId);
            approve.setStatus(status == 1 ? 1 : 2);
            approve.setRemark(remark);
            glWithdrawReturnApproveMapper.insertSelective(approve);
            if (1 == status) {
                // 提现成功后，设置重新计算输光
                withdrawEffectBetBusiness.resetLose(dbWithdraw.getUserId(), dbWithdraw.getCoin());
                //提现成功通知
                doWithdrawSuccessNotice(dbWithdraw);
                //增加商户出款金额
                addMerchantSuccess(dbWithdraw.getMerchantId(), dbWithdraw.getAmount().subtract(dbWithdraw.getFee()));

                //自动出款强制申请成功，那么提现记录直接变为成功
                WithdrawReport report = new WithdrawReport();
                report.setUuid(dbWithdraw.getOrderId());
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.SUCCESS));
                report.setTimestamp(dbWithdraw.getCreateDate());
                report.setCreateTime(dbWithdraw.getCreateDate());
                report.setFinishTime(dbWithdraw.getCreateDate());
                report.setLastUpdate(dbWithdraw.getLastUpdate());

                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(dbWithdraw.getUserId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(dbWithdraw.getUserId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }

                GlWithdrawMerchantAccount merchantAccount = glWithdrawMerchantAccountBusiness.findById(dbWithdraw.getMerchantId());
                if (null != merchantAccount) {
                    report.setChannelName(merchantAccount.getChannelName());
                    report.setChannelId(merchantAccount.getChannelId());
                    report.setMerchant(merchantAccount.getMerchantId());
                    report.setMerchantCode(merchantAccount.getMerchantCode());
                    report.setMerchantFee(withdrawRate(merchantAccount, dbWithdraw.getAmount()));
                    report.setAmountNet(dbWithdraw.getAmount().subtract(dbWithdraw.getFee()).multiply(new BigDecimal(100000000)).longValue());
                }
                reportService.withdrawReport(report);
                // 上报流水提现流水记录
                userWithdrawEffectHandler.reportWithdrawBettingBalance(dbWithdraw.getUserId(), dbWithdraw.getOrderId(), dbWithdraw.getAmount(), dbWithdraw.getCoin());
                dbWithdraw.setStatus(FundConstant.WithdrawStatus.SUCCESS);
                // 拆单父单状态上报
                updateParentOrderStatus(dbWithdraw);

                //极速提现订单进入计数器
                if (dbWithdraw.getAisleType() == FundConstant.AisleType.C2C) {
                    countTime(dbWithdraw, now);
                }


            } else {
                //自动出款强制成功申请失败，那么提现记录直接变为失败
                WithdrawReport report = new WithdrawReport();
                report.setUuid(dbWithdraw.getOrderId());
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.FAILED));
                report.setTimestamp(dbWithdraw.getCreateDate());
                report.setCreateTime(dbWithdraw.getCreateDate());
                report.setFinishTime(dbWithdraw.getCreateDate());
                report.setLastUpdate(dbWithdraw.getLastUpdate());
                dbWithdraw.setStatus(FundConstant.WithdrawStatus.FAILED);
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(dbWithdraw.getUserId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(dbWithdraw.getUserId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                reportService.withdrawReport(report);
                // 拆单父单状态上报
                updateParentOrderStatus(dbWithdraw);
                //提现失败通知
                doWithdrawFailNotice(dbWithdraw, "请联系客服");
                //TODO 申请强制成功-拒绝申请；提现失败，资金应该退回中心钱包。暂时通过财务客服资金调整加币处理

            }
        } catch (Exception e) {
            log.error("doWithdrawForceSuccess_error:", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }

    private void doWithdrawReturnNotice(GlWithdraw withdraw, int type, BigDecimal amount) {
        if (ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN == type) {
            NoticeSuccessDto successDto = new NoticeSuccessDto();
            successDto.setAmount(amount);
            successDto.setOrderId(withdraw.getOrderId());
            successDto.setUserId(withdraw.getUserId());
            successDto.setUserName(withdraw.getUsername());
            successDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
            withdrawReturnNoticeHandler.doSuccessNotice(successDto);
        } else if (ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN_FAIL == type) {
            NoticeFailDto failDto = new NoticeFailDto();
            failDto.setOrderId(withdraw.getOrderId());
            failDto.setUserId(withdraw.getUserId());
            failDto.setUserName(withdraw.getUsername());
            failDto.setAmount(amount);
            failDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
            withdrawReturnNoticeHandler.doFailNotice(failDto);
        }
    }

    private Long withdrawRate(GlWithdrawMerchantAccount withdrawAccount, BigDecimal amount) {
        if (null != withdrawAccount.getMerchantFeeType()) {
            if (0 == withdrawAccount.getMerchantFeeType()) {
                if (amount.compareTo(withdrawAccount.getMerchantFee()) < 0) {
                    return amount.multiply(new BigDecimal(100000000))
                            .setScale(0, RoundingMode.DOWN).longValue();
                } else {
                    return withdrawAccount.getMerchantFee().multiply(new BigDecimal(100000000)).setScale(0, RoundingMode.DOWN).longValue();
                }
            } else {
                return amount.multiply(withdrawAccount.getMerchantFee().divide(new BigDecimal(100)))
                        .multiply(new BigDecimal(100000000)).setScale(0, RoundingMode.DOWN).longValue();
            }
        }
        return 0L;
    }

    /**
     * 提现订单强制成功（待出款，三方出款中，三方出款失败才能强制成功） 更新收款信息
     * @param glwithdraw
     * @return
     */
    private void updateWithdrawReceice(GlWithdraw glwithdraw) {
        if (glwithdraw == null) {
            return;
        }
        GlWithdrawReceiveInfo receiveInfo = glWithdrawReceiveInfoBusiness.findById(glwithdraw.getOrderId());
        if (null == receiveInfo) {
            return ;
        }
        //取平台汇率
        BigDecimal rate = glWithdrawBusiness.getWithdrawRate(glwithdraw.getUserType());
        //优先使用出款信息中汇率和待出款USDT数量
        if (receiveInfo.getRate() != null) {
            receiveInfo.setActualRate(receiveInfo.getRate());
        } else {
            receiveInfo.setActualRate(rate);
        }
        if (receiveInfo.getUsdtAmount() != null) {
            receiveInfo.setActualUsdtAmount(receiveInfo.getUsdtAmount());
        } else {
            receiveInfo.setActualUsdtAmount(glwithdraw.getAmount().subtract(glwithdraw.getFee()).divide(rate, 4, RoundingMode.DOWN));
        }
        receiveInfo.setUpdateDate(new Date());
        glWithdrawReceiveInfoBusiness.updateByPrimaryKey(receiveInfo);
    }

    private void countTime(GlWithdraw glWithdraw, Date now) {
        String key = String.format(KeyConstant.C2C.C2C_WITHDRAW_FORCE_COUNT_USERID,glWithdraw.getUserId());
        Long count = redisService.incrBy(key,1);
        Date end = DateUtils.getLastDayOfThisMonth();
        int diffSecond = DateUtils.diffSecond(now, end);
        redisService.setTTL(key, diffSecond);


        C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);

        if (count > configDO.getWithdrawForceSuccessTime().intValue()) {
            GlUserLockDo lockDo =
                    new GlUserLockDo(glWithdraw.getUserId(), glWithdraw.getUsername(),1,"系统|System", "极速提现当月超过人工成功数", UserLockTypeEnum.SYSTEM.getStatus());
            glUserWithdrawService.updateStatus(lockDo);
        }
    }


    /**
     * 提现订单退回审核
     *
     * @param approveDO
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = GlobalException.class)
    public void doWithdrawReturnApprove(WithdrawRequestApproveDO approveDO, GlAdminDO admin) throws GlobalException {
        String orderId = approveDO.getOrderId();
        Integer status = approveDO.getStatus();
        String remark = approveDO.getRemark();
        String username = admin.getUsername();
        try {
            Date now = new Date();
            //更新提现申请记录
            GlWithdraw withdraw = glWithdrawMapper.selectByPrimaryKey(orderId);
            if (status == 1) {
                withdraw.setStatus(withdraw.getStatus() == FundConstant.WithdrawStatus.RETURN_PENDING ? FundConstant.WithdrawStatus.RETURN : FundConstant.WithdrawStatus.RETURN_PART);
            } else {
                withdraw.setStatus(FundConstant.WithdrawStatus.RETURN_REJECT);
            }
            withdraw.setLastUpdate(now);
            glWithdrawMapper.updateByPrimaryKeySelective(withdraw);

            //更新提现退回申请记录
            GlWithdrawReturnRequest dbRequest = glWithdrawReturnRequestMapper.selectByPrimaryKey(orderId);
            GlWithdrawReturnRequest request = new GlWithdrawReturnRequest();
            request.setOrderId(orderId);
            request.setStatus(status == 1 ? 1 : 2);
            request.setApprover(username);
            request.setApproveTime(now);
            request.setApproveRemark(remark);
            request.setWithdrawStatus(status == 1 ? FundConstant.WithdrawStatus.RETURN : FundConstant.WithdrawStatus.RETURN_REJECT);
            if (dbRequest.getWithdrawStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING) {
                request.setWithdrawStatus(status == 1 ? FundConstant.WithdrawStatus.RETURN_PART : FundConstant.WithdrawStatus.RETURN_REJECT);
            }
            glWithdrawReturnRequestMapper.updateByPrimaryKeySelective(request);

            /**
             * 更新提现退回审核人记录
             */
            GlWithdrawReturnApprove approve = new GlWithdrawReturnApprove();
            approve.setCreateTime(now);
            approve.setCreator(username);
            approve.setOrderId(orderId);
            approve.setStatus(status == 1 ? 1 : 2);
            approve.setRemark(remark);
            glWithdrawReturnApproveMapper.insertSelective(approve);

            GlUserDO user = RPCResponseUtils.getData(glUserService.findById(withdraw.getUserId()));

            DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(withdraw.getUserId(), DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()));

            String returnId = redisService.getTradeNo("TH");

            Date returnTime = new Date(now.getTime() - 1000);
            Integer type;
            if (1 == status) {
                // 提现金额退回中心钱包
                glFundUserAccountBusiness.addBalance(withdraw.getUserId(), dbRequest.getAmount(), DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()));

                /**
                 * 代理提现扣除可提现额度
                 */
                if (UserConstant.Type.PROXY == user.getUserType()) {
                    ValidWithdrawalDto validWithdrawalDto = new ValidWithdrawalDto();
                    validWithdrawalDto.setUserId(user.getId());
                    validWithdrawalDto.setAmount(dbRequest.getAmount());
                    fundProxyAccountBusiness.addValidWithdrawal(validWithdrawalDto);
                }

                // 上报 提现退回
                WithdrawReturnReport returnReport = new WithdrawReturnReport();
                returnReport.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setBalanceAfter(account.getBalance().add(withdraw.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setSubType("提现退回");
                if (dbRequest.getWithdrawStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING) {
                    returnReport.setAmount(dbRequest.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                    returnReport.setBalanceAfter(account.getBalance().add(dbRequest.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
                    returnReport.setSubType("提现部分退回");

                    //部分提现成功通知
                    NoticeSuccessDto successDto = new NoticeSuccessDto();
                    successDto.setOrderId(withdraw.getOrderId());
                    successDto.setUserId(withdraw.getUserId());
                    successDto.setUserName(withdraw.getUsername());
                    BigDecimal amount = withdraw.getAmount().subtract(dbRequest.getAmount());
                    successDto.setAmount(amount);
                    successDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
                    noticeHandler.doSuccessNotice(successDto);

                    // 上报流水提现流水记录
                    userWithdrawEffectHandler.reportWithdrawBettingBalance(withdraw.getUserId(), withdraw.getOrderId(), amount, withdraw.getCoin());
                }
                returnReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setCreateTime(returnTime);
                returnReport.setTimestamp(returnTime);
                returnReport.setFinishTime(returnTime);
                returnReport.setParentName(user.getParentName());
                returnReport.setParentId(user.getParentId());
                returnReport.setUid(user.getId());
                returnReport.setUserName(user.getUsername());
                returnReport.setUserType(UserTypeEnum.valueOf(user.getUserType()));
                returnReport.setWithdrawId(withdraw.getOrderId());
                returnReport.setStatus(status);
                returnReport.setUuid(returnId);
                returnReport.setIsFake(user.getIsFake());
                returnReport.setCoin(withdraw.getCoin());
                reportService.reportWithdrawReturn(returnReport);

                //发送提现退回成功通知
                type = ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN;
                doWithdrawReturnNotice(withdraw, type, dbRequest.getAmount());
                // 上报提现订单
                WithdrawReport report = new WithdrawReport();
                report.setUuid(withdraw.getOrderId());
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.FAILED));
                report.setTimestamp(withdraw.getCreateDate());
                report.setFinishTime(withdraw.getCreateDate());
                report.setLastUpdate(withdraw.getLastUpdate());
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(withdraw.getUserId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(withdraw.getUserId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                if (dbRequest.getWithdrawStatus() == FundConstant.WithdrawStatus.RETURN_PART_PENDING) {
                    report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.SUCCESS));
                    BigDecimal amount = withdraw.getAmount().subtract(dbRequest.getAmount());
                    report.setAmount(withdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                    report.setFee(BigDecimal.ZERO.longValue());
                    report.setSubType("部分出款");
                    report.setAmountNet(amount.multiply(BigDecimal.valueOf(100000000)).longValue());
                }
                reportService.withdrawReport(report);

                updateParentOrderStatus(withdraw);
            } else {
                //提现退回申请失败，那么提现记录直接变为失败
                //需要修改
                WithdrawReport report = new WithdrawReport();
                report.setUuid(withdraw.getOrderId());
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.FAILED));
                report.setTimestamp(withdraw.getCreateDate());
                report.setCreateTime(withdraw.getCreateDate());
                report.setFinishTime(withdraw.getCreateDate());
                report.setLastUpdate(withdraw.getLastUpdate());
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(withdraw.getUserId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(withdraw.getUserId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                reportService.withdrawReport(report);

                // 拆单父单状态上报
                updateParentOrderStatus(withdraw);

                /**
                 * 上报提现退回失败,前后余额一致
                 */
                //需要修改
                WithdrawReturnReport returnReport = new WithdrawReturnReport();
                returnReport.setUuid(returnId);
                returnReport.setAmount(dbRequest.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setBalanceAfter(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setCreateTime(returnTime);
                returnReport.setTimestamp(returnTime);
                returnReport.setFinishTime(returnTime);
                returnReport.setParentName(user.getParentName());
                returnReport.setParentId(user.getParentId());
                returnReport.setUid(user.getId());
                returnReport.setUserName(user.getUsername());
                returnReport.setUserType(UserTypeEnum.valueOf(user.getUserType()));
                returnReport.setWithdrawId(withdraw.getOrderId());
                returnReport.setStatus(status);
                returnReport.setSubType("提现退回");
                returnReport.setIsFake(user.getIsFake());
                returnReport.setCoin(withdraw.getCoin());
                reportService.reportWithdrawReturn(returnReport);

                //提现退回失败
                type = ProjectConstant.SystemNoticeTempleteId.WITHDRAWAL_RETURN_FAIL;
                doWithdrawReturnNotice(withdraw, type, dbRequest.getAmount());
            }
        } catch (Exception e) {
            log.error("doWithdrawReturnApprove_error.", e);
            throw new GlobalException(e.getMessage(), e);
        }
    }

    /**
     * 提现失败通知接口
     *
     * @param withdraw
     * @param rejectReason
     */
    private void doWithdrawFailNotice(GlWithdraw withdraw, String rejectReason) {
        NoticeFailDto failDto = new NoticeFailDto();
        failDto.setOrderId(withdraw.getOrderId());
        failDto.setUserId(withdraw.getUserId());
        failDto.setUserName(withdraw.getUsername());
        failDto.setAmount(withdraw.getAmount());
        failDto.setRejectReason(rejectReason);
        failDto.setCoin(DigitalCoinEnum.getDigitalCoin(withdraw.getCoin()).getDesc());
        noticeHandler.doFailNotice(failDto);
    }

    /**
     * 人工出款
     *
     * @param dto
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doWithdrawApprove(GlWithdraw req, WithdrawApproveDO dto, GlAdminDO admin) throws GlobalException {
        Integer adminUserId = admin.getUserId();
        String adminUsername = admin.getUsername();
        Date now = new Date();
        dto.setUpdateTime(now);
        try {
            //插入提现审核记录
            withdrawApproveBusiness.saveWithdrawApprove(req.getOrderId(), adminUserId, adminUsername, dto.getStatus(), ProjectConstant.BalanceType.MANUAL_TRANSFER);
            //Update 提现记录
            GlWithdraw glWithdraw = glWithdrawMapper.selectForUpdate(req.getOrderId());
            glWithdraw.setOrderId(req.getOrderId());
            glWithdraw.setStatus(dto.getStatus() == 1 ? FundConstant.WithdrawStatus.SUCCESS : FundConstant.WithdrawStatus.FAILED);
            glWithdraw.setApprover(adminUsername);
            glWithdraw.setApproveTime(now);
            glWithdraw.setTransferName(dto.getTransferName());
            glWithdraw.setTransferBankName(dto.getTransferBankName());
            glWithdraw.setLastUpdate(now);
            glWithdraw.setRemark(dto.getRemark());
            glWithdraw.setRejectReason(dto.getRejectReason());
            glWithdraw.setWithdrawType(req.getWithdrawType());
            glWithdrawMapper.updateByPrimaryKeySelective(glWithdraw);

            GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(glWithdraw.getUserId()));
            /**
             * 人工确认出款成功,更新提现订单状态
             */
            if (dto.getStatus() == 1) {
                // 提现成功后，设置重新计算输光
                withdrawEffectBetBusiness.resetLose(glWithdraw.getUserId(),req.getCoin());
                WithdrawReport report = new WithdrawReport();
                report.setUuid(req.getOrderId());
                report.setUid(glUser.getId());
                report.setTimestamp(glWithdraw.getCreateDate());
                report.setFinishTime(glWithdraw.getCreateDate());
                report.setLastUpdate(glWithdraw.getLastUpdate());
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(glUser.getId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(glUser.getId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                report.setStatus(WithdrawStatusEnum.valueOf(glWithdraw.getStatus()));
                // 人工出款 设置 Merchant 和 ChannelId 为-2
                report.setMerchant(-2);
                report.setChannelId(-2);
                report.setChannelName("人工出款");
                report.setMerchantFee(0L);
                report.setMerchantCode("");
                reportService.withdrawReport(report);
                // 上报提现流水变动数据
                userWithdrawEffectHandler.reportWithdrawBettingBalance(glWithdraw.getUserId(), glWithdraw.getOrderId(), glWithdraw.getAmount(), glWithdraw.getCoin());
                updateParentOrderStatus(glWithdraw);
                //提现成功通知
                doWithdrawSuccessNotice(req);

                //更新实际汇率和实际出款USDT数量
                if (glWithdraw.getBankId() == FundConstant.PaymentType.DIGITAL_PAY) {
                    this.updateWithdrawReceice(glWithdraw);
                }

            } else {
                /**
                 * 提现拒绝 - 提现金额退回中心余额、上报退回记录、创建退回申请-自动审批完成
                 */
                //创建退回申请
                glWithdrawReturnRequestBusiness.save(glWithdraw, dto, admin);

                //修改钱包余额
                DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(glWithdraw.getUserId(), DigitalCoinEnum.getDigitalCoin(glWithdraw.getCoin()));
                glFundUserAccountBusiness.addBalance(glWithdraw.getUserId(), glWithdraw.getAmount(),DigitalCoinEnum.getDigitalCoin(req.getCoin()));

                /**
                 * 代理提现扣除可提现额度
                 */
                if (UserConstant.Type.PROXY == glUser.getUserType()) {
                    ValidWithdrawalDto validWithdrawalDto = new ValidWithdrawalDto();
                    validWithdrawalDto.setUserId(glUser.getId());
                    validWithdrawalDto.setAmount(glWithdraw.getAmount());
                    fundProxyAccountBusiness.addValidWithdrawal(validWithdrawalDto);
                }

                /**
                 * 上报 提现失败
                 */
                WithdrawReport report = new WithdrawReport();
                report.setUuid(glWithdraw.getOrderId());
                report.setUid(glUser.getId());
                report.setTimestamp(glWithdraw.getCreateDate());
                report.setFinishTime(glWithdraw.getCreateDate());
                report.setLastUpdate(glWithdraw.getLastUpdate());
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(glUser.getId());
                if (ObjectUtils.isEmpty(vipCache) == false) {
                    report.setVipLevel(vipCache.getVipLevel());
                    report.set("vipLevel", vipCache.getVipLevel());
                }
                // 用户层级
                FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(glUser.getId());
                if (ObjectUtils.isEmpty(userLevel) == false) {
                    report.setUserLevel(userLevel.getLevelId());
                    report.setUserLevelName(userLevel.getLevelName());
                }
                report.setStatus(WithdrawStatusEnum.WITHDRAWN_FAILED);
                report.setRejectReason(dto.getRejectReason());
                reportService.withdrawReport(report);
                /**
                 * 上报 提现退回
                 */
                String returnId = redisService.getTradeNo("TH");
                WithdrawReturnReport returnReport = new WithdrawReturnReport();
                returnReport.setAmount(glWithdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
                //添加账变前后金额
                Date returnTime = new Date(now.getTime() - 1000);
                returnReport.setBalanceAfter(account.getBalance().add(glWithdraw.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
                returnReport.setCreateTime(returnTime);
                returnReport.setTimestamp(returnTime);
                returnReport.setFinishTime(returnTime);
                returnReport.setParentName(glUser.getParentName());
                returnReport.setParentId(glUser.getParentId());
                returnReport.setUid(glUser.getId());
                returnReport.setUserName(glUser.getUsername());
                returnReport.setUserType(UserTypeEnum.valueOf(glUser.getUserType()));
                returnReport.setWithdrawId(glWithdraw.getOrderId());
                returnReport.setStatus(1);
                returnReport.setUuid(returnId);
                returnReport.setSubType("提现退回");
                returnReport.setIsFake(glUser.getIsFake());
                returnReport.setCoin(glWithdraw.getCoin());
                reportService.reportWithdrawReturn(returnReport);

                updateParentOrderStatus(glWithdraw);

                //提现失败系统通知
                doWithdrawFailNotice(glWithdraw, dto.getRejectReason());
            }
        } catch (Exception e) {
            log.error("doWithdrawApprove_error", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void doSuccessRequest(WithdrawRequestDO requestDO, GlAdminDO adminDO)
            throws GlobalException {
        /**
         * 更新提现订单状态
         */
        Date now = new Date();
        GlWithdraw withdraw = glWithdrawMapper.selectByPrimaryKey(requestDO.getOrderId());
        if (withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_FAILED
                && withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_PENDING
                && withdraw.getStatus() != FundConstant.WithdrawStatus.PENDING
                && withdraw.getStatus() != FundConstant.WithdrawStatus.CONFIRM_PENDING
                && withdraw.getStatus() != FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                && withdraw.getStatus() != FundConstant.WithdrawStatus.RECHARGE_PENDING) {
            throw new GlobalException("提现订单状态异常,不可退回");
        }

        withdraw.setOrderId(withdraw.getOrderId());
        withdraw.setStatus(FundConstant.WithdrawStatus.SUCCESS_PENDING);
        withdraw.setLastUpdate(now);
        withdraw.setApprover(adminDO.getUsername());
        withdraw.setRemark(requestDO.getRemark());
        withdraw.setApproveTime(now);
        glWithdrawMapper.updateByPrimaryKeySelective(withdraw);
        // 提现成功后，设置重新计算输光
        withdrawEffectBetBusiness.resetLose(withdraw.getUserId(), withdraw.getCoin());
        /**
         * 申请强制成功记录入库
         */
        GlWithdrawReturnRequest request = new GlWithdrawReturnRequest();
        request.setAmount(withdraw.getAmount());
        request.setOrderId(withdraw.getOrderId());
        request.setCreateTime(now);
        request.setType(1);
        request.setStatus(0);
        request.setUserId(withdraw.getUserId());
        request.setUsername(withdraw.getUsername());
        request.setCreator(adminDO.getUsername());
        request.setUserType(withdraw.getUserType());
        request.setRemark(requestDO.getRemark());
        request.setWithdrawType(withdraw.getWithdrawType());
        request.setWithdrawStatus(FundConstant.WithdrawStatus.SUCCESS_PENDING);
        request.setAttachments(requestDO.getAttachments());
        glWithdrawReturnRequestMapper.insertSelective(request);

        //通知撮合系统
        c2COrderCallbackHandler.withdrawCancel(request.getOrderId(),true);
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void doReturnRequest(WithdrawRequestDO requestDO, GlAdminDO adminDO) throws GlobalException {
        try {
            Date now = new Date();

            //提现退回状态：RETURN_PENDING（3）： 全部退回  RETURN_PART_PENDING（13）：部分退回
            Integer status = requestDO.getAmount() == null ? FundConstant.WithdrawStatus.RETURN_PENDING : FundConstant.WithdrawStatus.RETURN_PART_PENDING;
            /**
             * 更新提现订单记录
             */
            GlWithdraw withdraw = glWithdrawMapper.selectByPrimaryKey(requestDO.getOrderId());

            if (status == FundConstant.WithdrawStatus.RETURN_PART_PENDING
                    && requestDO.getAmount().compareTo(withdraw.getAmount()) > 0) {
                throw new GlobalException("部分退回金额不能大于或等于提现金额");
            }
            if (status == FundConstant.WithdrawStatus.RETURN_PART_PENDING
                    && withdraw.getFee().compareTo(requestDO.getAmount()) > 0) {
                throw new GlobalException("部分退回金额不能小于手续费");
            }

            if (withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_FAILED
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.AUTO_PENDING
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.CONFIRM_PENDING
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.UNCONFIRMED_TIMEOUT
                    && withdraw.getStatus() != FundConstant.WithdrawStatus.RECHARGE_PENDING) {
                throw new GlobalException("提现订单状态异常,不可退回");
            }
            if (status == FundConstant.WithdrawStatus.RETURN_PART_PENDING && withdraw.getAisleType() != FundConstant.AisleType.C2C) {
                throw new GlobalException("非极速提现订单不可部分退回");
            }

            withdraw.setOrderId(requestDO.getOrderId());
            withdraw.setStatus(status);
            withdraw.setLastUpdate(now);
            withdraw.setApprover(adminDO.getUsername());
            withdraw.setRemark(requestDO.getRemark());
            withdraw.setApproveTime(now);
            glWithdrawMapper.updateByPrimaryKeySelective(withdraw);

            /**
             * 申请提现退回记录入库
             */
            BigDecimal returnAmount = requestDO.getAmount() == null ? withdraw.getAmount() : requestDO.getAmount();//退回金额

            GlWithdrawReturnRequest request = new GlWithdrawReturnRequest();
            request.setAmount(returnAmount);
            request.setOrderId(withdraw.getOrderId());
            request.setWithdrawStatus(withdraw.getStatus());
            request.setMerchant(withdraw.getMerchant());
            request.setMerchantId(withdraw.getMerchantId());
            request.setCreateTime(now);
            request.setType(0);
            request.setStatus(0);
            request.setUserId(withdraw.getUserId());
            request.setUsername(withdraw.getUsername());
            request.setCreator(adminDO.getUsername());
            request.setUserType(withdraw.getUserType());
            request.setRemark(requestDO.getRemark());
            request.setWithdrawType(withdraw.getWithdrawType());
            request.setWithdrawStatus(status);
            request.setAttachments(requestDO.getAttachments());
            glWithdrawReturnRequestMapper.insertSelective(request);

            // 提现记录上报
            WithdrawReport report = new WithdrawReport();
            report.setUuid(withdraw.getOrderId());
            if (status == FundConstant.WithdrawStatus.RETURN_PART_PENDING) {
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.SUCCESS));
                Long realWithdrawAmount = (withdraw.getAmount().subtract(returnAmount)).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue();
                report.setAmount(realWithdrawAmount);
            } else {
                report.setStatus(WithdrawStatusEnum.valueOf(FundConstant.WithdrawStatus.FAILED));
            }
            report.setTimestamp(withdraw.getCreateDate());
            report.setFinishTime(withdraw.getCreateDate());
            report.setLastUpdate(withdraw.getLastUpdate());
            // 用户VIP等级
            UserVIPCache vipCache = userVipUtils.getUserVIPCache(withdraw.getUserId());
            if (ObjectUtils.isEmpty(vipCache) == false) {
                report.setVipLevel(vipCache.getVipLevel());
                report.set("vipLevel", vipCache.getVipLevel());
            }
            // 用户层级
            FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(withdraw.getUserId());
            if (ObjectUtils.isEmpty(userLevel) == false) {
                report.setUserLevel(userLevel.getLevelId());
                report.setUserLevelName(userLevel.getLevelName());
            }
            reportService.withdrawReport(report);

            updateParentOrderStatus(withdraw);

            //通知撮合系统
            c2COrderCallbackHandler.withdrawCancel(requestDO.getOrderId(),true);
        } catch (Exception e) {
            log.error("doReturnRequest_error.", e);
            if (  e instanceof GlobalException) {
                throw e;
            } else {
                throw new GlobalException(ResultCode.SERVER_ERROR);
            }
        }
    }


    /**
     * 拒绝出款
     *
     * @param req
     * @param dto
     * @param admin
     * @throws GlobalException
     */
    @Transactional(rollbackFor = {GlobalException.class})
    public void doWithdrawApiRefuse(GlWithdraw req, WithdrawApproveDO dto, GlAdminDO admin) throws GlobalException {
        String adminUserName = admin.getUsername();
        Integer adminUserId = admin.getUserId();
        String remark = dto.getRemark();
        String rejectReason = dto.getRejectReason();
        Date now = new Date();
        dto.setUpdateTime(now);
        try {
            GlUserDO glUser = RPCResponseUtils.getData(glUserService.findById(req.getUserId()));
            //插入提现审核记录
            withdrawApproveBusiness.saveWithdrawApprove(req.getOrderId(), adminUserId, adminUserName, 0, FundConstant.WithdrawType.API_MANUAL);

            GlWithdraw glWithdraw = glWithdrawMapper.selectForUpdate(req.getOrderId());
            glWithdraw.setOrderId(req.getOrderId());
            glWithdraw.setStatus(FundConstant.WithdrawStatus.FAILED);
            glWithdraw.setApprover(adminUserName);
            glWithdraw.setApproveTime(now);
            glWithdraw.setLastUpdate(now);
            glWithdraw.setRemark(remark);
            glWithdraw.setRejectReason(rejectReason);
            glWithdraw.setWithdrawType(req.getWithdrawType());
            glWithdrawMapper.updateWithdraw(glWithdraw);

            /**
             * 提现拒绝 - 创建退回申请(审核通过)、提现金额退回中心余额、上报退回记录、
             */
            glWithdrawReturnRequestBusiness.save(glWithdraw, dto, admin);

            DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(glWithdraw.getUserId(), DigitalCoinEnum.getDigitalCoin(glWithdraw.getCoin()));
            glFundUserAccountBusiness.addBalance(glWithdraw.getUserId(), glWithdraw.getAmount(),DigitalCoinEnum.getDigitalCoin(req.getCoin()));

            /**
             * 代理提现扣除可提现额度
             */
            if (UserConstant.Type.PROXY == glUser.getUserType()) {
                ValidWithdrawalDto validWithdrawalDto = new ValidWithdrawalDto();
                validWithdrawalDto.setUserId(glUser.getId());
                validWithdrawalDto.setAmount(glWithdraw.getAmount());
                fundProxyAccountBusiness.addValidWithdrawal(validWithdrawalDto);
            }

            String returnId = redisService.getTradeNo("TH");
            WithdrawReturnReport returnReport = new WithdrawReturnReport();
            returnReport.setAmount(glWithdraw.getAmount().multiply(BigDecimal.valueOf(100000000)).longValue());
            //添加账变前后金额
            Date returnTime = new Date(now.getTime() - 1000);
            returnReport.setBalanceAfter(account.getBalance().add(glWithdraw.getAmount()).multiply(BigDecimal.valueOf(100000000)).longValue());
            returnReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(100000000)).longValue());
            returnReport.setCreateTime(returnTime);
            returnReport.setTimestamp(returnTime);
            returnReport.setFinishTime(returnTime);
            returnReport.setParentName(glUser.getParentName());
            returnReport.setParentId(glUser.getParentId());
            returnReport.setUid(glUser.getId());
            returnReport.setUserName(glUser.getUsername());
            returnReport.setUserType(UserTypeEnum.valueOf(glUser.getUserType()));
            returnReport.setWithdrawId(glWithdraw.getOrderId());
            returnReport.setStatus(1);
            returnReport.setUuid(returnId);
            returnReport.setSubType("提现退回");
            returnReport.setIsFake(glUser.getIsFake());
            returnReport.setCoin(glWithdraw.getCoin());
            reportService.reportWithdrawReturn(returnReport);

            WithdrawReport report = new WithdrawReport();
            report.setUuid(req.getOrderId());
            report.setUid(glUser.getId());
            report.setTimestamp(glWithdraw.getCreateDate());
            report.setFinishTime(glWithdraw.getCreateDate());
            report.setLastUpdate(glWithdraw.getLastUpdate());
            // 用户VIP等级
            UserVIPCache vipCache = userVipUtils.getUserVIPCache(glUser.getId());
            if (ObjectUtils.isEmpty(vipCache) == false) {
                report.setVipLevel(vipCache.getVipLevel());
                report.set("vipLevel", vipCache.getVipLevel());
            }
            // 用户层级
            FundUserLevelDO userLevel = userFundUtils.getFundUserLevel(glUser.getId());
            if (ObjectUtils.isEmpty(userLevel) == false) {
                report.setUserLevel(userLevel.getLevelId());
                report.setUserLevelName(userLevel.getLevelName());
            }
            report.setStatus(WithdrawStatusEnum.WITHDRAWN_FAILED);
            report.setApproveTime(glWithdraw.getApproveTime());
            report.setRejectReason(rejectReason);
            reportService.withdrawReport(report);

            // 拆单父单状态上报
            updateParentOrderStatus(req);
            //提现失败系统通知
            doWithdrawFailNotice(glWithdraw, rejectReason);
        } catch (Exception e) {
            log.error("doWithdrawApiRefuse_error:", e);
            throw new GlobalException(ResultCode.SERVER_ERROR);
        }
    }

}

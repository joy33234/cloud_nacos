package com.seektop.fund.service.impl;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.OrderPrefix;
import com.seektop.constant.ProjectConstant;
import com.seektop.constant.user.UserConstant;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.agent.TransferStatusEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.business.proxy.ProxyCreditLogBusiness;
import com.seektop.fund.dto.param.account.FundProxyAccountDto;
import com.seektop.fund.dto.param.proxy.FundProxyAccountDO;
import com.seektop.fund.dto.param.proxy.QuotaAdjDO;
import com.seektop.fund.dto.param.proxy.TransferDO;
import com.seektop.fund.model.FundProxyAccount;
import com.seektop.fund.model.ProxyCreditLog;
import com.seektop.fund.service.FundProxyAccountService;
import com.seektop.report.agent.ProxyRechargeRebateReport;
import com.seektop.report.agent.ProxyTransferOutReport;
import com.seektop.report.agent.ProxyTransferReturnReport;
import com.seektop.report.user.BalanceDetailDO;
import com.seektop.report.user.UserSynch;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;

@Slf4j
@DubboService(timeout = 5000, interfaceClass = FundProxyAccountService.class)
public class FundProxyAccountServiceImpl implements FundProxyAccountService {

    @DubboReference(retries = 2, timeout = 5000)
    private GlUserService glUserService;

    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;

    @Resource
    private GlFundUserAccountBusiness fundUserAccountBusiness;

    @Resource
    private ProxyCreditLogBusiness proxyCreditLogBusiness;

    @Resource
    private ReportService reportService;

    @Resource
    private RedisService redisService;

    public RPCResponse<FundProxyAccountDO> findById(Integer userId) {
        FundProxyAccount account = fundProxyAccountBusiness.findById(userId);
        FundProxyAccountDO accountDO = DtoUtils.transformBean(account, FundProxyAccountDO.class);
        RPCResponse.Builder<FundProxyAccountDO> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(accountDO).build();
    }

    @Override
    public RPCResponse<FundProxyAccountDO> selectForUpdate(Integer userId) {
        FundProxyAccount account = fundProxyAccountBusiness.selectForUpdate(userId);
        FundProxyAccountDO accountDO = DtoUtils.transformBean(account, FundProxyAccountDO.class);
        RPCResponse.Builder<FundProxyAccountDO> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(accountDO).build();
    }

    @Override
    public RPCResponse<BigDecimal> validBalanceForUpdate(Integer userId) {
        FundProxyAccount glFundProxyAccount = fundProxyAccountBusiness.selectForUpdate(userId);
        DigitalUserAccount userAccount = fundUserAccountBusiness.getUserAccountForUpdate(userId, DigitalCoinEnum.CNY);
        return RPCResponseUtils.buildSuccessRpcResponse(userAccount.getBalance().add(glFundProxyAccount.getCreditAmount()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RPCResponse<Void> transferApply(String orderNo, GlUserDO proxy, GlUserDO targetUser, GlAdminDO admin, BigDecimal amount, String remark, Date now) {
        DigitalCoinEnum coinEnum = DigitalCoinEnum.CNY;
        //变更余额
        BigDecimal proxyBalanceAfter = fundUserAccountBusiness.addBalance(proxy.getId(), amount.negate(), coinEnum);
        BigDecimal balance = proxyBalanceAfter.add(amount);

        //代理资金流水入库
        proxyFundsLog(orderNo, proxy, targetUser, amount, balance, null, TransferStatusEnum.PENDING, ProjectConstant.CreditOptType.BALANCE_TRANSFER, remark, now);

        //上报转账记录
        fundProxyAccountBusiness.transferOutRecordReport(orderNo, proxy, targetUser, amount, balance, TransferStatusEnum.PENDING, remark, admin,coinEnum.getCode(), now);

        //上报更新余额
        fundProxyAccountBusiness.esUserReport(proxy.getId(), coinEnum.getCode(), proxyBalanceAfter, null);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RPCResponse<Void> transferReview(TransferDO transferDO, TransferStatusEnum status) throws GlobalException {
        Date now = new Date();
        BigDecimal amount = transferDO.getAmount();
        String orderNo = transferDO.getOrderNo();
        Integer userId;
        if (status == TransferStatusEnum.SUCCESS) {
            //审核通过，增加转账金额至目标对应账户
            userId = transferDO.getTargetUserId();
        } else {
            //审核失败，还原转账金额至对应账户
            userId = transferDO.getProxyId();
        }

        DigitalCoinEnum coinEnum = DigitalCoinEnum.CNY;
        //变更余额
        BigDecimal balanceAfter = fundUserAccountBusiness.addBalance(userId, amount, coinEnum);
        BigDecimal balance = balanceAfter.subtract(amount);

        //更新代理资金流水
        proxyCreditLogBusiness.updateStatus(status.value(), orderNo);

        //ES上报故意滞后在DB操作之后

        //上报更新转出订单状态
        ProxyTransferOutReport proxyTransferOutReport = new ProxyTransferOutReport();
        proxyTransferOutReport.setUuid(orderNo);
        proxyTransferOutReport.setStatus(status);
        reportService.proxyTransferOutReport(proxyTransferOutReport);

        GlUserDO proxy = RPCResponseUtils.getData(glUserService.findById(transferDO.getProxyId()));
        if (status == TransferStatusEnum.SUCCESS) {
            FundProxyAccount glFundProxyAccount = fundProxyAccountBusiness.selectForUpdate(userId);
            //审核通过，上报目标用户转入记录
            //目标剩余信用额度
            BigDecimal targetCreditBalanceAfter = balanceAfter.compareTo(BigDecimal.ZERO) >= 0 ? glFundProxyAccount.getCreditAmount() : glFundProxyAccount.getCreditAmount().subtract(balanceAfter.abs());

            String subType = "收到代理(" + proxy.getUsername() + ")转账";
            GlUserDO targetUser = RPCResponseUtils.getData(glUserService.findById(transferDO.getTargetUserId()));
            fundProxyAccountBusiness.transferInRecordReport(proxy, targetUser, amount, balance, targetCreditBalanceAfter, subType, transferDO.getRemark(), transferDO.getCreator(),DigitalCoinEnum.CNY.getCode(), now);
        } else {
            //上报转账退回
            ProxyTransferReturnReport returnReport = new ProxyTransferReturnReport();
            returnReport.setUuid(redisService.getTradeNo(OrderPrefix.ZZ.getCode()));
            returnReport.setUid(userId);
            returnReport.setCoin(coinEnum.getCode());
            returnReport.setUserId(userId);
            returnReport.setUserName(proxy.getUsername());
            returnReport.setParentId(proxy.getParentId());
            returnReport.setParentName(proxy.getParentName());
            returnReport.setStatus(TransferStatusEnum.SUCCESS);
            returnReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            //操作代理账户余额的前后变化
            returnReport.setBalanceBefore(balance.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            returnReport.setBalanceAfter((balance.add(amount)).movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            //目标用户信息
            returnReport.setTransferUserId(proxy.getId());
            returnReport.setTransferUserName(proxy.getUsername());
            returnReport.setUserType(UserTypeEnum.PROXY);
            returnReport.setSubType("代理转账-代转退回");
            returnReport.setRemark("转账失败退回");
            returnReport.setCreateTime(now);
            returnReport.setTimestamp(now);
            returnReport.setFinishTime(now);
            reportService.proxyTransferReturnReport(returnReport);
        }

        //上报更新余额
        fundProxyAccountBusiness.esUserReport(userId, coinEnum.getCode(), balanceAfter, null);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RPCResponse<Void> quotaAdj(QuotaAdjDO quotaAdjDO) {
        FundProxyAccount proxyAccount = new FundProxyAccount();
        if (ObjectUtils.isEmpty(quotaAdjDO.getFundProxyAccountDO())) {
            proxyAccount = fundProxyAccountBusiness.selectForUpdate(quotaAdjDO.getProxyId());
        } else {
            BeanUtils.copyProperties(quotaAdjDO.getFundProxyAccountDO(), proxyAccount);
        }

        UserSynch esUser = new UserSynch();
        esUser.setId(proxyAccount.getUserId());

        //调整类型
        if (quotaAdjDO.getType() == UserConstant.UserOperateType.CREDIT_ADJUSTMENT_OPTTYPE) {
            //信用额度调整
            proxyAccount.setCreditAmount(proxyAccount.getCreditAmount().add(quotaAdjDO.getAmount()));
            esUser.setCredit_amount(proxyAccount.getCreditAmount());
            esUser.setCreditAmountDetail(Arrays.asList(new BalanceDetailDO(DigitalCoinEnum.CNY.getCode(),proxyAccount.getCreditAmount())));
        } else {
            //可提现额度调整
            proxyAccount.setValidWithdrawal(proxyAccount.getValidWithdrawal().add(quotaAdjDO.getAmount()));
            esUser.setValid_withdrawal(proxyAccount.getValidWithdrawal());
        }
        proxyAccount.setLastUpdate(new Date());
        fundProxyAccountBusiness.updateByPrimaryKeySelective(proxyAccount);
        reportService.userSynch(esUser);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RPCResponse<Void> quotaAdjLog(QuotaAdjDO quotaDO) {
        Date now = new Date();
        FundProxyAccount proxyAccount = fundProxyAccountBusiness.selectForUpdate(quotaDO.getProxyId());
        // 额度调整记录入库
        ProxyCreditLog creditLog = new ProxyCreditLog();
        BigDecimal amountBefore;
        //调整类型
        if (quotaDO.getType() == UserConstant.UserOperateType.CREDIT_ADJUSTMENT_OPTTYPE) {
            //信用额度调整
            amountBefore = proxyAccount.getCreditAmount();
            creditLog.setOrderId(redisService.getTradeNo(OrderPrefix.SX.getCode()));
            creditLog.setOptType(ProjectConstant.CreditOptType.COMPANY_CREDIT);
        } else {
            //可提现额度调整
            amountBefore = proxyAccount.getValidWithdrawal();
            creditLog.setOrderId(redisService.getTradeNo(OrderPrefix.WJ.getCode()));
            creditLog.setOptType(ProjectConstant.CreditOptType.WITHDRAWAL_ADJUST);
        }
        BigDecimal amountAfter = amountBefore.add(quotaDO.getAmount());

        creditLog.setProxyId(quotaDO.getProxyId());
        creditLog.setProxyUserName(quotaDO.getProxyName());
        creditLog.setCreditedAmountBefore(amountBefore);
        creditLog.setCreditedAmountAfter(amountAfter);
        creditLog.setChangeAmount(quotaDO.getAmount());
        creditLog.setCreateTime(now);
        creditLog.setAccountType(ProjectConstant.AccountType.PROXY_BALANCE);
        creditLog.setOptUserName(quotaDO.getAdminDO().getUsername());
        creditLog.setOptUserId(quotaDO.getAdminDO().getUserId());
        creditLog.setOptPeopleType(ProjectConstant.OperatorType.COMPANY);
        creditLog.setModifyUserId(quotaDO.getProxyId());
        creditLog.setModifyUserName(quotaDO.getProxyName());
        creditLog.setModifyUserType(UserConstant.UserType.PROXY);
        creditLog.setModifyAmountBefore(amountBefore);
        creditLog.setModifyAmountAfter(amountAfter);
        proxyCreditLogBusiness.save(creditLog);

        //调整额度上调
        if (BigDecimal.ZERO.compareTo(quotaDO.getAmount()) <= 0) {
            UserSynch esUser = new UserSynch();
            esUser.setId(quotaDO.getProxyId());

            if (quotaDO.getType() == UserConstant.UserOperateType.CREDIT_ADJUSTMENT_OPTTYPE) {
                proxyAccount.setCreditAmount(amountAfter);
                esUser.setCredit_amount(amountAfter);
                esUser.setCreditAmountDetail(Arrays.asList(new BalanceDetailDO(DigitalCoinEnum.CNY.getCode(),amountAfter)));
            } else {
                proxyAccount.setValidWithdrawal(amountAfter);
                esUser.setValid_withdrawal(amountAfter);
            }
            proxyAccount.setLastUpdate(now);
            fundProxyAccountBusiness.updateByPrimaryKeySelective(proxyAccount);
            reportService.userSynch(esUser);
        }
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    public RPCResponse<Void> updateFundProxyAccount(FundProxyAccountDO fundProxyAccountDO, UserSynch userSynch) {
        FundProxyAccount proxyAccount = new FundProxyAccount();
        BeanUtils.copyProperties(fundProxyAccountDO, proxyAccount);
        fundProxyAccountBusiness.updateByPrimaryKeySelective(proxyAccount);
        reportService.userSynch(userSynch);
        return RPCResponseUtils.buildSuccessRpcResponse(null);
    }

    @Override
    public RPCResponse<Void> rechargeRebateApprovePass(Integer userId, BigDecimal amount) {
        Date now = new Date();
        RPCResponse.Builder<Void> newBuilder = RPCResponse.newBuilder();
        DigitalCoinEnum coinEnum = DigitalCoinEnum.CNY;
        FundProxyAccount proxyAccount = fundProxyAccountBusiness.selectForUpdate(userId);
        if (!ObjectUtils.isEmpty(proxyAccount)) {
            GlUserDO user = glUserService.findById(userId).getData();
            //修改余额
            BigDecimal balanceAfter = fundUserAccountBusiness.addBalance(userId, amount, coinEnum);
            BigDecimal balance = balanceAfter.subtract(amount);

            //修改可提现额度
            proxyAccount.setValidWithdrawal(proxyAccount.getValidWithdrawal().add(amount));
            fundProxyAccountBusiness.updateByPrimaryKeySelective(proxyAccount);

            //上报可提现额度
            UserSynch esUser = new UserSynch();
            esUser.setId(proxyAccount.getUserId());
            esUser.setValid_withdrawal(proxyAccount.getValidWithdrawal());
            reportService.userSynch(esUser);

            // 上报代充返利
            ProxyRechargeRebateReport rebateReport = new ProxyRechargeRebateReport();
            rebateReport.setUuid(redisService.getTradeNo(OrderPrefix.RR.getCode()));
            rebateReport.setUid(user.getId());
            rebateReport.setCoin(coinEnum.getCode());
            rebateReport.setUserName(user.getUsername());
            rebateReport.setUserType(UserTypeEnum.PROXY);
            rebateReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            rebateReport.setBalanceBefore(balance.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            rebateReport.setBalanceAfter(balanceAfter.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
            rebateReport.setIsFake(user.getIsFake());
            rebateReport.setParentId(user.getParentId());
            rebateReport.setParentName(user.getParentName());
            rebateReport.setStatus(1);
            rebateReport.setFinishTime(now);
            rebateReport.setTimestamp(now);
            reportService.proxyRechargeRebateReport(rebateReport);
        } else {
            log.error("rechargeRebateApprove.error:代理账户信息为空 userId={}", userId);
            return newBuilder.fail().build();
        }
        return newBuilder.success().build();
    }

    public RPCResponse<Boolean> createFundAccount(FundProxyAccountDto dto) {
        fundProxyAccountBusiness.save(dto);
        return RPCResponseUtils.buildSuccessRpcResponse(true);
    }

    /**
     * 代理资金流水入库
     *
     * @param orderId
     * @param proxy
     * @param targetUser
     * @param amount
     * @param proxyBalance
     * @param targetBalance
     * @param payerCredit
     * @param now
     */
    private void proxyFundsLog(String orderId, GlUserDO proxy, GlUserDO targetUser, BigDecimal amount, BigDecimal proxyBalance, BigDecimal targetBalance, TransferStatusEnum status, Integer payerCredit, String remark, Date now) {
        ProxyCreditLog proxyCreditLog = new ProxyCreditLog();
        proxyCreditLog.setOrderId(orderId);
        proxyCreditLog.setChangeAmount(amount);
        proxyCreditLog.setOptType(payerCredit);
        proxyCreditLog.setProxyId(proxy.getId());
        proxyCreditLog.setProxyUserName(proxy.getUsername());
        proxyCreditLog.setStatus(status.value());
        //操作人信息
        proxyCreditLog.setOptUserId(proxy.getId());
        proxyCreditLog.setOptUserName(proxy.getUsername());
        proxyCreditLog.setOptPeopleType(ProjectConstant.OperatorType.PROXY);
        // 代理的额度变化前后
        proxyCreditLog.setCreditedAmountBefore(proxyBalance);
        proxyCreditLog.setCreditedAmountAfter(proxyBalance.subtract(amount));
        // 目标代理或会员信息及余额的变化前后
        proxyCreditLog.setModifyUserId(targetUser.getId());
        proxyCreditLog.setModifyUserName(targetUser.getUsername());
        //审核中，目标用户余额暂不记录
        if (null != targetBalance) {
            proxyCreditLog.setModifyAmountBefore(targetBalance);
            proxyCreditLog.setModifyAmountAfter(targetBalance.add(amount));
        }
        proxyCreditLog.setRemark(remark);
        proxyCreditLog.setCreateTime(now);
        proxyCreditLogBusiness.save(proxyCreditLog);
    }

}

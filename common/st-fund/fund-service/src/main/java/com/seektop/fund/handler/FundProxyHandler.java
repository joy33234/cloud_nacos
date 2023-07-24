package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.agent.dto.CommRateDO;
import com.seektop.agent.dto.result.GlProxyAccountResult;
import com.seektop.agent.service.CommCommissionService;
import com.seektop.agent.service.ProxyService;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.BigDecimalUtils;
import com.seektop.common.utils.OrderPrefix;
import com.seektop.constant.ProjectConstant;
import com.seektop.data.service.RechargeService;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlUserDO;
import com.seektop.dto.UserVIPCache;
import com.seektop.enumerate.agent.TransferStatusEnum;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.BettingBalanceEnum;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.business.proxy.ProxyCreditLogBusiness;
import com.seektop.fund.business.proxy.ProxyCreditPayoutLogBusiness;
import com.seektop.fund.business.proxy.ProxyRechargeFeeBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConfigBusiness;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawCommonConfig;
import com.seektop.fund.common.UserVipUtils;
import com.seektop.fund.controller.forehead.param.proxy.ProxyPayoutDO;
import com.seektop.fund.controller.forehead.result.ProxyFundsResult;
import com.seektop.fund.dto.param.account.FundUserBalanceChangeVO;
import com.seektop.fund.enums.ProxyPayoutEnum;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.service.WithdrawUserBankCardService;
import com.seektop.report.agent.UpAmountReport;
import com.seektop.system.service.NoticeService;
import com.seektop.user.dto.result.GlSecurityPasswordDO;
import com.seektop.user.service.GlUserSecurityService;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

import static com.seektop.constant.fund.Constants.DIGITAL_REPORT_MULTIPLY_SCALE;

@Slf4j
@Component
public class FundProxyHandler {

    @Reference(timeout = 3000, retries = 2)
    private GlUserSecurityService glUserSecurityService;

    @Reference(timeout = 3000, retries = 2)
    private WithdrawUserBankCardService withdrawUserBankCardService;

    @Reference(timeout = 3000, retries = 2)
    private GlUserService glUserService;

    @Reference(timeout = 3000, retries = 2)
    private ProxyService proxyService;

    @Reference(timeout = 3000, retries = 2)
    private CommCommissionService commCommissionService;

    @Reference(timeout = 3000, retries = 2)
    private NoticeService noticeService;

    @DubboReference(timeout = 3000, retries = 2)
    RechargeService rechargeReportService;

    @Resource
    private FundProxyAccountBusiness fundProxyAccountBusiness;

    @Resource
    private GlFundUserAccountBusiness fundUserAccountBusiness;

    @Resource
    private GlRechargeBusiness glRechargeBusiness;

    @Resource
    private GlWithdrawConfigBusiness glWithdrawConfigBusiness;

    @Resource
    private ProxyRechargeFeeBusiness proxyRechargeFeeBusiness;

    @Resource
    private ProxyCreditLogBusiness proxyCreditLogBusiness;

    @Resource
    private ProxyCreditPayoutLogBusiness proxyCreditPayoutLogBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private ReportService reportService;

    @Resource
    private RedisService redisService;

    @Autowired
    private GlRechargeMapper glRechargeMapper;

    @Resource
    private UserVipUtils userVipUtils;

    /**
     * 代理钱包信息
     *
     * @param proxyId
     * @return
     */
    public GlProxyAccountResult walletInfo(Integer proxyId) {
        GlProxyAccountResult result = new GlProxyAccountResult();
        BigDecimal balance = fundUserAccountBusiness.getUserAccountBalance(proxyId);
        result.setBalance(balance);

        RPCResponse<GlSecurityPasswordDO> securityPassword = glUserSecurityService.getSecurityPassword(proxyId);
        if (RPCResponseUtils.isSuccess(securityPassword)) {
            result.setExistSecurityPassword(ProjectConstant.switchCase.ON);
        }

        if (withdrawUserBankCardService.findUserActiveCardList(proxyId).getData().size() > 0) {
            result.setExistBankCard(ProjectConstant.switchCase.ON);
        }

        FundProxyAccount proxyAccount = fundProxyAccountBusiness.findById(proxyId);
        if (!ObjectUtils.isEmpty(proxyAccount)) {
            BeanUtils.copyProperties(proxyAccount, result);
            result.setValidCreditAmount(balance.compareTo(BigDecimal.ZERO) < 0 ? proxyAccount.getCreditAmount().add(balance) : proxyAccount.getCreditAmount());
            if (result.getValidCreditAmount().compareTo(BigDecimal.ZERO) == -1) {
                result.setValidCreditAmount(BigDecimal.ZERO);
            }
            result.setValidAmount(proxyAccount.getCreditAmount().add(balance));
        }
        return result;
    }

    public List<GlUserDO> findSubordinateProxyList(Integer parentId, String username) {
        return glUserService.findSubordinateProxyList(parentId, username).getData();
    }

    /**
     * 代理代充、转账
     *
     * @param payoutDO
     * @param targetUser
     * @param proxy
     * @return
     */
    @Transactional(rollbackFor = GlobalException.class)
    public Result payout(ProxyPayoutDO payoutDO, GlUserDO targetUser, GlUserDO proxy) {
        Date now = new Date();

        Integer proxyId = proxy.getId();
        BigDecimal amount = new BigDecimal(payoutDO.getAmount());
        ProxyFundsResult result = new ProxyFundsResult();
        result.setAmount(amount);
        //暂只支持 cny
        DigitalCoinEnum coinEnum = DigitalCoinEnum.CNY;

        //操作代理中心钱包账户
        DigitalUserAccount userAccount = fundUserAccountBusiness.getUserAccountForUpdate(proxyId, coinEnum);
        BigDecimal proxyBalance = userAccount.getBalance();
        result.setBalanceBefore(proxyBalance);

        //操作代理信用额度账户及权限信息
        FundProxyAccount glFundProxyAccount = fundProxyAccountBusiness.selectForUpdate(proxyId);
        BigDecimal creditAmount = glFundProxyAccount.getCreditAmount();
        result.setCreditBefore(proxyBalance.add(creditAmount));
        BigDecimal proxyBalanceAfter = null;
        BigDecimal targetBalanceAfter;
        try {
            if (payoutDO.getType() == ProxyPayoutEnum.RECHARGE.getCode()) {
                if (redisService.exists("PROXY_PAY_OUT_TIME_LIMIT-"+payoutDO.getType()+"-"+proxyId+"-"+targetUser.getId()))
                    return Result.genFailResult("该会员账号代充操作频繁,请1分钟后再试!");
                /**
                 * 会员代充
                 */
                //会员代充权限
                if (glFundProxyAccount.getPayoutStatus() != ProjectConstant.switchCase.ON)
                    return Result.genFailResult("请联系客服开启会员代充权限");

                if (proxyBalance.add(creditAmount).compareTo(amount) < 0)
                    return Result.genFailResult("超出代充额度上限");

                if (amount.compareTo(new BigDecimal(5)) < 0) {
                    return Result.genFailResult("代充金额不能小于5元");
                }

                //校验代理信息及返利方案
                RPCResponse<CommRateDO> commRate = commCommissionService.getCommRate(proxyId,payoutDO.getCoinCode());
                if (!RPCResponseUtils.isSuccess(commRate)) {
                    return Result.genFailResult("代理方案异常，请联系技术");
                }
                CommRateDO commRateData = commRate.getData();

                //目标用户添加会员中心余额与流水
                GlWithdrawCommonConfig commonConfig = glWithdrawConfigBusiness.getWithdrawCommonConfig();
                if (null == commonConfig || commonConfig.getMultiple() <= 0)
                    return Result.genFailResult("提现流水配置异常,请联系技术.");

                //首存校验
                boolean first = false;
//                RPCResponse<KV3Result<String, BigDecimal, Date, Integer>> kv3ResultRPCResponse = rechargeReportService.firstRechargeInfo(targetUser.getId());
//                if (RPCResponseUtils.isFail(kv3ResultRPCResponse)) {
//                    throw new GlobalException("首冲金额查询失败");
//                } else {
//                    KV3Result<String, BigDecimal, Date, Integer> data = kv3ResultRPCResponse.getData();
//                    if (null == data) {
//                        first = true;
//                    }
//                }
                if (glRechargeMapper.isFirst(targetUser.getId())) {
                    first = true;
                }

                String orderNo = redisService.getTradeNo(OrderPrefix.DC.getCode());

                FundUserBalanceChangeVO userAccountVO = new FundUserBalanceChangeVO();
                userAccountVO.setTradeId(orderNo);
                userAccountVO.setUserId(targetUser.getId());
                userAccountVO.setAmount(amount);
                userAccountVO.setFreezeAmount(amount.multiply(BigDecimal.valueOf(commonConfig.getMultiple())));
                userAccountVO.setMultiple(commonConfig.getMultiple());
                userAccountVO.setChangeDate(now);
                userAccountVO.setOperator(proxy.getUsername());
                //目标用户可用余额变化后
                targetBalanceAfter = fundUserAccountBusiness.doUserAccountChange(userAccountVO, targetUser.getIsFake(), BettingBalanceEnum.PROXY_UP.getCode(), null, coinEnum.getCode());
                //目标用户可用余额变化前
                BigDecimal targetBalanceBefore = targetBalanceAfter.subtract(amount);

                // 操作代理中心钱包余额变动
                // 代理可用额度变化后
                proxyBalanceAfter = fundUserAccountBusiness.addBalance(proxyId, amount.negate(), coinEnum);

                // 手续费返利入库
                ProxyRechargeFee fee = calcRuleFee(orderNo, proxyId, commRateData.getRuleId(), commRateData.getCommRate(), amount);

                //代理资金流水入库
                proxyFundsLog(orderNo, proxy, targetUser, amount, proxyBalance, targetBalanceBefore, TransferStatusEnum.SUCCESS, ProjectConstant.CreditOptType.PAYER_CREDIT, payoutDO.getRemarks(), now);

                // 上分记录入库
                ProxyCreditPayoutLog payoutLog = new ProxyCreditPayoutLog();
                payoutLog.setOrderId(orderNo);
                payoutLog.setCreateTime(now);
                payoutLog.setProxyId(proxy.getId());
                payoutLog.setProxyName(proxy.getUsername());
                payoutLog.setUserId(targetUser.getId());
                payoutLog.setUserName(targetUser.getUsername());
                payoutLog.setAmount(amount);
                payoutLog.setRebate(fee.getRebate());
                payoutLog.setOrderType(ProjectConstant.AccountType.PROXY_BALANCE);
                proxyCreditPayoutLogBusiness.save(payoutLog);

                UpAmountReport proxyReport = new UpAmountReport();
                //代理、会员公用属性
                proxyReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).abs().longValue());
                proxyReport.setRebate(fee.getRebate().movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
                proxyReport.setRebateRate(fee.getRebateRate());
                proxyReport.setCoin(coinEnum.getCode());
                proxyReport.setUserRemark("上级代理：" + proxy.getUsername());
                proxyReport.setProxyRemark("会员代充给会员：" + targetUser.getUsername());
                proxyReport.setSubType("代充给下级会员（" + targetUser.getUsername() + "）");
                proxyReport.setCreateTime(now);
                proxyReport.setTimestamp(now);
                proxyReport.setFinishTime(now);
                proxyReport.setRemark(payoutDO.getRemarks());
                UpAmountReport targetReport = new UpAmountReport();
                BeanUtils.copyProperties(proxyReport, targetReport);

                //上报操作代理会员代充资金流水
                proxyReport.setUuid(orderNo);
                upAmountReport(proxyReport, proxy, proxyBalance, proxyBalanceAfter, UserTypeEnum.PROXY);

                // 上报目标用户资金流水
                targetReport.setUuid(redisService.getTradeNo(OrderPrefix.DC.getCode()));
                //首充标示
                if (first) targetReport.setFirst(1);
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(targetUser.getId());
                targetReport.setVipLevel(ObjectUtils.isEmpty(vipCache) ? 0 : vipCache.getVipLevel());
                // 用户层级信息
                GlFundUserlevel userLevel = glFundUserlevelBusiness.getUserLevel(targetUser.getId());
                targetReport.setUserLevel(userLevel.getLevelId());
                targetReport.setUserLevelName(userLevel.getName());
                targetReport.setTimestamp(now);
                targetReport.setFinishTime(now);
                targetReport.setCoin(coinEnum.getCode());
                upAmountReport(targetReport, targetUser, targetBalanceBefore, targetBalanceAfter, UserTypeEnum.MEMBER);

                //上分通知添加
                RPCResponse<Boolean> booleanRPCResponse = noticeService.doUpAmountNotice(targetUser.getId(), targetUser.getUsername(), amount, orderNo);
                if (!booleanRPCResponse.getData()) {
                    log.error("addLogisticsInfo 代理代充上分通知发送失败：{},{},{},{}", targetUser.getId(), targetUser.getUsername(), amount, orderNo);
                    throw new GlobalException("代理代充上分通知发送失败");
                }
            } else if (payoutDO.getType() == ProxyPayoutEnum.TRANSFER.getCode()) {
                if (redisService.exists("PROXY_PAY_OUT_TIME_LIMIT-"+payoutDO.getType()+"-"+proxyId+"-"+targetUser.getId()))
                    return Result.genFailResult("该代理账号转账操作频繁,请1分钟后再试!");
                /**
                 * 代理转账给代理
                 */
                if (glFundProxyAccount.getTransferProxyStatus() != ProjectConstant.switchCase.ON)
                    return Result.genFailResult("请联系客服开启向代理转账权限");

                BigDecimal validBalance = proxyBalance.add(creditAmount);
                if (validBalance.compareTo(amount) < 0) {
                    return Result.genFailResult("可用余额不足");
                }

                String orderNo = redisService.getTradeNo(OrderPrefix.ZZ.getCode());

                //目标用户中心钱包余额及流水变动
                //目标用户可用余额变化后
                targetBalanceAfter = fundUserAccountBusiness.addBalance(targetUser.getId(), amount, coinEnum);
                //目标用户可用余额变化前
                BigDecimal targetBalanceBefore = targetBalanceAfter.subtract(amount);

                //目标用户信用额度
                FundProxyAccount targetGlFundProxyAccount = fundProxyAccountBusiness.selectForUpdate(targetUser.getId());
                BigDecimal targetCreditAmount = targetGlFundProxyAccount.getCreditAmount();

                // 操作代理中心钱包余额变动
                // 代理可用额度变化后
                proxyBalanceAfter = fundUserAccountBusiness.addBalance(proxyId, amount.negate(), coinEnum);

                //代理资金流水入库
                proxyFundsLog(orderNo, proxy, targetUser, amount, proxyBalance, targetBalanceBefore, TransferStatusEnum.SUCCESS, ProjectConstant.CreditOptType.BALANCE_TRANSFER, payoutDO.getRemarks(), now);

                //上报操作代理转出记录
                fundProxyAccountBusiness.transferOutRecordReport(orderNo, proxy, targetUser, amount, proxyBalance, TransferStatusEnum.SUCCESS, payoutDO.getRemarks(), null,coinEnum.getCode(), now);

                //目标剩余信用额度
                BigDecimal targetCreditBalanceAfter = targetBalanceAfter.compareTo(BigDecimal.ZERO) >= 0 ? targetCreditAmount : targetCreditAmount.subtract(targetBalanceAfter.abs());

                //上报目标用户转入记录
                String subType = "收到代理（" + proxy.getUsername() + "）转账";
                fundProxyAccountBusiness.transferInRecordReport(proxy, targetUser, amount, targetBalanceBefore, targetCreditBalanceAfter, subType, payoutDO.getRemarks(), proxy.getUsername(),coinEnum.getCode(), now);
            } else {
                /**
                 * 代理转账至会员账户
                 */

                if (glFundProxyAccount.getTransferMemberStatus() != ProjectConstant.switchCase.ON)
                    return Result.genFailResult("请联系客服开启向会员账户转账权限");

                if (proxyBalance.compareTo(amount) < 0) {
                    return Result.genFailResult("可用余额不足");
                }

                //目标用户添加会员中心余额与流水
                GlWithdrawCommonConfig commonConfig = glWithdrawConfigBusiness.getWithdrawCommonConfig();
                if (null == commonConfig || commonConfig.getMultiple() <= 0)
                    return Result.genFailResult("提现流水配置异常,请联系技术.");

                String orderNo = redisService.getTradeNo(OrderPrefix.ZZ.getCode());

                //首存校验
                boolean first = false;
//                RPCResponse<KV3Result<String, BigDecimal, Date, Integer>> kv3ResultRPCResponse = rechargeReportService.firstRechargeInfo(targetUser.getId());
//                if (RPCResponseUtils.isFail(kv3ResultRPCResponse)) {
//                    throw new GlobalException("首冲金额查询失败");
//                } else {
//                    KV3Result<String, BigDecimal, Date, Integer> data = kv3ResultRPCResponse.getData();
//                    if (null == data) {
//                        first = true;
//                    }
//                }
                if (glRechargeMapper.isFirst(targetUser.getId())) {
                    first = true;
                }


                // 上分记录入库
                ProxyCreditPayoutLog payoutLog = new ProxyCreditPayoutLog();
                payoutLog.setOrderId(orderNo);
                payoutLog.setCreateTime(now);
                payoutLog.setProxyId(1);
                payoutLog.setProxyName("admin");
                payoutLog.setUserId(targetUser.getId());
                payoutLog.setUserName(targetUser.getUsername());
                payoutLog.setAmount(amount);
                payoutLog.setRebate(BigDecimal.ZERO);
                payoutLog.setOrderType(ProjectConstant.AccountType.PROXY_BALANCE);
                proxyCreditPayoutLogBusiness.save(payoutLog);


                FundUserBalanceChangeVO userAccountVO = new FundUserBalanceChangeVO();
                userAccountVO.setCoinCode(coinEnum.getCode());
                userAccountVO.setTradeId(orderNo);
                userAccountVO.setUserId(targetUser.getId());
                userAccountVO.setAmount(amount);
                userAccountVO.setFreezeAmount(amount.multiply(BigDecimal.valueOf(commonConfig.getMultiple())));
                userAccountVO.setMultiple(commonConfig.getMultiple());
                userAccountVO.setChangeDate(now);
                userAccountVO.setOperator(proxy.getUsername());
                //目标用户可用余额变化后
                targetBalanceAfter = fundUserAccountBusiness.doUserAccountChange(userAccountVO, targetUser.getIsFake(), BettingBalanceEnum.PROXY_UP.getCode(), null, coinEnum.getCode());
                //目标用户可用余额变化前
                BigDecimal targetBalanceBefore = targetBalanceAfter.subtract(amount);

                // 操作代理中心钱包余额变动
                // 代理可用额度变化后
                proxyBalanceAfter = fundUserAccountBusiness.addBalance(proxyId, amount.negate(), coinEnum);

                //代理资金流水入库
                proxyFundsLog(orderNo, proxy, targetUser, amount, proxyBalance, targetBalanceBefore, TransferStatusEnum.SUCCESS, ProjectConstant.CreditOptType.TRANSFER_MEMBER, payoutDO.getRemarks(), now);


                UpAmountReport proxyReport = new UpAmountReport();
                //代理、会员公用属性
                proxyReport.setCoin(coinEnum.getCode());
                proxyReport.setAmount(amount.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).abs().longValue());
                proxyReport.setUserRemark("上级代理：" + proxy.getUsername());
                proxyReport.setProxyRemark("会员代充给会员：" + targetUser.getUsername());
                proxyReport.setSubType("转游戏账户");
                proxyReport.setCreateTime(now);
                proxyReport.setTimestamp(now);
                proxyReport.setFinishTime(now);
                proxyReport.setRemark(payoutDO.getRemarks());
                UpAmountReport targetReport = new UpAmountReport();
                BeanUtils.copyProperties(proxyReport, targetReport);
                targetReport.setSubType("佣金转入");
                //上报操作代理会员代充资金流水
                proxyReport.setUuid(orderNo);
                upAmountReport(proxyReport, proxy, proxyBalance, proxyBalanceAfter, UserTypeEnum.PROXY);

                targetReport.setCoin(coinEnum.getCode());
                // 上报目标用户资金流水
                targetReport.setUuid(redisService.getTradeNo(OrderPrefix.ZZ.getCode()));
                //首充标示
                if (first) targetReport.setFirst(1);
                // 用户VIP等级
                UserVIPCache vipCache = userVipUtils.getUserVIPCache(targetUser.getId());
                targetReport.setVipLevel(ObjectUtils.isEmpty(vipCache) ? 0 : vipCache.getVipLevel());
                // 用户层级信息
                GlFundUserlevel userLevel = glFundUserlevelBusiness.getUserLevel(targetUser.getId());
                targetReport.setUserLevel(userLevel.getLevelId());
                targetReport.setUserLevelName(userLevel.getName());
                targetReport.setTimestamp(now);
                targetReport.setFinishTime(now);
                upAmountReport(targetReport, targetUser, targetBalanceBefore, targetBalanceAfter, UserTypeEnum.MEMBER);

                //上分通知添加
                RPCResponse<Boolean> booleanRPCResponse = noticeService.doUpAmountNotice(targetUser.getId(), targetUser.getUsername(), amount, orderNo);
                if (!booleanRPCResponse.getData()) {
                    log.error("addLogisticsInfo 代理代充上分通知发送失败：{},{},{},{}", targetUser.getId(), targetUser.getUsername(), amount, orderNo);
                    throw new GlobalException("代理代充上分通知发送失败");
                }
            }
            //更新操作代理余额上报
            fundProxyAccountBusiness.esUserReport(proxyId, coinEnum.getCode(), proxyBalanceAfter, null);

            //更新目标用户余额上报
            fundProxyAccountBusiness.esUserReport(targetUser.getId(), coinEnum.getCode(), targetBalanceAfter, null);
        } catch (GlobalException e) {
            log.error("payout_error:{}", e);
            e.printStackTrace();
        }

        result.setBalanceAfter(proxyBalanceAfter);
        //计算剩余信用额度
        if (proxyBalanceAfter.compareTo(BigDecimal.ZERO) != -1) {
            result.setCreditAmountAfter(creditAmount);
        } else {
            result.setCreditAmountAfter(creditAmount.subtract(proxyBalanceAfter.abs()));
        }

        result.setCreditAfter(proxyBalanceAfter.add(creditAmount));
        //同用户代充时间限制
        setProxyPayOutTimeLimit(proxyId,targetUser.getId(),payoutDO.getType());
        return Result.genSuccessResult(result);
    }

    private void setProxyPayOutTimeLimit(Integer proxyId,Integer targetUid,Integer payType){
        try{
            redisService.set("PROXY_PAY_OUT_TIME_LIMIT-"+payType+"-"+proxyId+"-"+targetUid,true,60);
        }catch (Exception e){}
    }

    /**
     * 计算返利并入库
     *
     * @param orderNo
     * @param proxyId
     * @param ruleId
     * @param commRate
     * @param amount
     */
    private ProxyRechargeFee calcRuleFee(String orderNo, Integer proxyId, Integer ruleId, BigDecimal commRate, BigDecimal amount) {
        ProxyRechargeFee proxyRechargeFee = new ProxyRechargeFee();
        proxyRechargeFee.setOrderId(orderNo);
        proxyRechargeFee.setUserId(proxyId);
        // 返利
        proxyRechargeFee.setRebate(amount.multiply(commRate).divide(BigDecimalUtils.HUNDRED));
        // 返利比例
        proxyRechargeFee.setRebateRate(commRate);
        proxyRechargeFee.setRuleId(ruleId);
        proxyRechargeFee.setCreateTime(new Date());
        proxyRechargeFeeBusiness.save(proxyRechargeFee);
        return proxyRechargeFee;
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

    /**
     * 代充资金明细上报
     *
     * @param upAmountReport
     * @param user
     * @param balanceBefore
     * @param balanceAfter
     * @param userType
     */
    private void upAmountReport(UpAmountReport upAmountReport, GlUserDO user, BigDecimal balanceBefore, BigDecimal balanceAfter, UserTypeEnum userType) {
        upAmountReport.setBalanceBefore(balanceBefore.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        upAmountReport.setBalanceAfter(balanceAfter.movePointRight(DIGITAL_REPORT_MULTIPLY_SCALE).longValue());
        upAmountReport.setUid(user.getId());
        upAmountReport.setUserId(user.getId());
        upAmountReport.setUserName(user.getUsername());
        upAmountReport.setParentId(user.getParentId());
        upAmountReport.setParentName(user.getParentName());
        upAmountReport.setRegTime(user.getRegisterDate());
        upAmountReport.setIsFake(user.getIsFake());
        upAmountReport.setUserType(userType);
        upAmountReport.setStatus(ProjectConstant.Status.SUCCESS);
        log.info("代充资金明细上报report:{},userType:{}", JSON.toJSONString(upAmountReport),userType.name());
        reportService.upAmountReport(upAmountReport, userType);
    }
}
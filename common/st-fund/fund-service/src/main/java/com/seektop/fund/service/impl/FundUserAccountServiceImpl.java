package com.seektop.fund.service.impl;

import com.alibaba.fastjson.JSON;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponse.Builder;
import com.seektop.common.rest.rpc.RPCResponseCode;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.GameOrderPrefix;
import com.seektop.constant.HandlerResponseCode;
import com.seektop.constant.ProjectConstant;
import com.seektop.digital.model.DigitalUserAccount;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import com.seektop.enumerate.fund.FundReportEvent;
import com.seektop.enumerate.user.UserTypeEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserAccountBusiness;
import com.seektop.fund.business.GlWithdrawUserUsdtAddressBusiness;
import com.seektop.fund.business.proxy.FundProxyAccountBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawUserBankCardBusiness;
import com.seektop.fund.business.withdraw.WithdrawUserBankCardApplyBusiness;
import com.seektop.fund.dto.param.account.FundUserBalanceChangeVO;
import com.seektop.fund.dto.param.account.TransferDO;
import com.seektop.fund.dto.param.account.TransferRecoverDO;
import com.seektop.fund.dto.param.account.UserAccountChangeDO;
import com.seektop.fund.dto.result.account.FundUserAccountDO;
import com.seektop.fund.model.FundProxyAccount;
import com.seektop.fund.model.GlWithdrawUserBankCard;
import com.seektop.fund.model.GlWithdrawUserUsdtAddress;
import com.seektop.fund.service.FundUserAccountService;
import com.seektop.report.fund.BalanceChangeReport;
import com.seektop.report.fund.HandlerResponse;
import com.seektop.report.user.BalanceDetailDO;
import com.seektop.report.user.CommissionReport;
import com.seektop.report.user.UserSynch;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.compress.utils.Sets;
import org.apache.commons.lang3.time.StopWatch;
import org.apache.dubbo.config.annotation.DubboReference;
import org.apache.dubbo.config.annotation.DubboService;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.*;

@Slf4j
@DubboService(timeout = 5000, interfaceClass = FundUserAccountService.class)
public class FundUserAccountServiceImpl implements FundUserAccountService {

    @DubboReference(retries = 2, timeout = 5000)
    private GlUserService glUserService;

    @Resource
    private RedisService redisService;

    @Resource
    private ReportService reportService;

    @Autowired
    private GlFundUserAccountBusiness glFundUserAccountBusiness;

    @Autowired
    private FundProxyAccountBusiness fundProxyAccountBusiness;

    @Autowired
    private GlWithdrawUserBankCardBusiness glWithdrawUserBankCardBusiness;

    @Autowired
    private WithdrawUserBankCardApplyBusiness withdrawUserBankCardApplyBusiness;
    @Resource
    private GlWithdrawUserUsdtAddressBusiness glWithdrawUserUsdtAddressBusiness;

    @Override
    public RPCResponse<Set<Integer>> getBankcardUser(String cardNo) {
        RPCResponse.Builder newBuilder = RPCResponse.newBuilder();
        Set<Integer> userIdList = Sets.newHashSet();
        // 查询银行卡的绑卡信息
        List<GlWithdrawUserBankCard> users = glWithdrawUserBankCardBusiness.findWithdrawBankCardByCardNo(cardNo);
        if (CollectionUtils.isEmpty(users)) {
            return newBuilder.success().setData(userIdList).build();
        }
        // 组装需要的用户ID
        for (GlWithdrawUserBankCard user : users) {
            userIdList.add(user.getUserId());
        }
        return newBuilder.success().setData(userIdList).build();
    }

    @Override
    public RPCResponse<FundUserAccountDO> getFundUserAccount(@NotNull(message = "用户ID不能为空") Integer userId) {
        DigitalUserAccount userAccount = glFundUserAccountBusiness.getUserAccount(userId,DigitalCoinEnum.CNY);
        Builder<FundUserAccountDO> newBuilder = RPCResponse.newBuilder();
        if (ObjectUtils.isEmpty(userAccount)) {
            return newBuilder.fail().setMessage("未查询到对应用户的财务账户信息").build();
        }
        FundUserAccountDO accountDO = new FundUserAccountDO();
        BeanUtils.copyProperties(userAccount, accountDO);
        return newBuilder.success().setData(accountDO).build();
    }

    @Override
    public RPCResponse<List<String>> getUserBankcard(Integer userId) {
        List<String> cards = Lists.newArrayList();
        if (ObjectUtils.isEmpty(userId) == false) {
            List<GlWithdrawUserBankCard> binds = glWithdrawUserBankCardBusiness.findUserActiveCardList(userId);
            if (binds != null && binds.size() > 0) {
                for (GlWithdrawUserBankCard bindCard : binds) {
                    cards.add(bindCard.getCardNo());
                }
            }
            List<GlWithdrawUserBankCard> applyCards = withdrawUserBankCardApplyBusiness.findCardByUserId(userId);
            if (applyCards != null && applyCards.size() > 0) {
                for (GlWithdrawUserBankCard applyCard : applyCards) {
                    cards.add(applyCard.getCardNo());
                }
            }
        }
        Builder<List<String>> newBuilder = RPCResponse.newBuilder();
        return newBuilder.success().setData(cards).build();
    }

    @Override
    public RPCResponse<String> getUserLastBankcard(Integer userId) {
        Builder<String> result = RPCResponse.newBuilder();
        if (ObjectUtils.isEmpty(userId) == false){
            List<GlWithdrawUserBankCard> userCardList = glWithdrawUserBankCardBusiness.findUserCardList(userId);
            if (userCardList!=null&&userCardList.size()>0) return result.success().setData(userCardList.get(0).getCardNo()).build();
        }
        return result.success().build();
    }

    @Override
    public RPCResponse<String> getUserLastUsdtAddress(Integer userId) {
        Builder<String> result = RPCResponse.newBuilder();
        if (ObjectUtils.isEmpty(userId) == false){
            List<GlWithdrawUserUsdtAddress> usdtAddress = glWithdrawUserUsdtAddressBusiness.findByUserId(userId, null);
            if (usdtAddress!=null&&usdtAddress.size()>0) return result.success().setData(usdtAddress.get(0).getAddress()).build();
        }
        return result.success().build();
    }

    /**
     * @param userId  用户id
     * @param orderId 转账的订单id
     * @param amount  转账金额
     */
    @Override
    public HandlerResponse transfer(Integer userId, String orderId, BigDecimal amount, Integer changeType, String remark) {
        StopWatch sw = new StopWatch();
        sw.start();
        HandlerResponse response = null;
        if (changeType == null || amount == null) {
            return HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                    HandlerResponseCode.FAIL.getCode(), "参数异常");
        }
        if (amount.compareTo(BigDecimal.ZERO) <= 0) {
            return HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                    HandlerResponseCode.FAIL.getCode(), "金额必须大于0");
        }
        try {
            response = glFundUserAccountBusiness.transfer(userId, orderId, amount, changeType, remark, false);
        } catch (GlobalException e) {
            return HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER.value(),
                HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试");
        }
        sw.stop();
        log.info("use orderId ={} , response = {} ,use time: {}", orderId, response, sw.getTime());
        return response;

    }

    @Override
    public HandlerResponse transfer(TransferDO transferDO) {
        if (transferDO.getChangeType() == null || transferDO.getAmount() == null) {
            return HandlerResponse.generateRespoese(transferDO.getOrderId(), FundReportEvent.TRANSFER.value(),
                    HandlerResponseCode.FAIL.getCode(), "参数异常");
        }
        if (transferDO.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            return HandlerResponse.generateRespoese(transferDO.getOrderId(), FundReportEvent.TRANSFER.value(),
                    HandlerResponseCode.FAIL.getCode(), "金额必须大于0");
        }
        try {
            return glFundUserAccountBusiness.transfer(transferDO.getUserId(), transferDO.getOrderId(), transferDO.getAmount(),
                    transferDO.getChangeType(), transferDO.getRemark(), transferDO.getNegative());
        } catch (GlobalException e) {
            HandlerResponse response = HandlerResponse.generateRespoese(transferDO.getOrderId(), FundReportEvent.TRANSFER.value(),
                HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试");
            try{
                glFundUserAccountBusiness.saveOrUpdateGlFundTransferRecord(null, 3, transferDO.getOrderId(), 1, transferDO.getAmount(), transferDO.getChangeType(), transferDO.getUserId(),
                    JSON.toJSONString(response), transferDO.getRemark());
            }catch (Exception e1){
                log.error(e1.getMessage(), e);
            }
            return response;
        }

    }

    @Override
    public HandlerResponse transferRecover(TransferRecoverDO transferRecoverDO) {
        try {
            return glFundUserAccountBusiness.transferRecover(transferRecoverDO.getOrderId(), transferRecoverDO.getNegative());
        } catch (GlobalException e) {
            log.error("transferRecover orderId = {} no balance change.", transferRecoverDO.getOrderId());
            return HandlerResponse.generateRespoese(transferRecoverDO.getOrderId(), FundReportEvent.TRANSFER_ROLLBACK.value(),
                HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试");
        }
    }

    @Override
    public void syncUser(Integer userId) {
        UserSynch esUser = new UserSynch();
        esUser.setId(userId);
        esUser.setBalanceDetail(new ArrayList<>());
        esUser.setFreezeBalanceDetail(new ArrayList<>());
        esUser.setValidBalanceDetail(new ArrayList<>());
        for (DigitalCoinEnum coinEnum:DigitalCoinEnum.values()){
            if (!coinEnum.getIsEnable()) continue;
            DigitalUserAccount userAccount = glFundUserAccountBusiness.getUserAccount(userId, coinEnum);
            if (userAccount==null) continue;
            esUser.getBalanceDetail().add(new BalanceDetailDO(coinEnum.getCode(), userAccount.getBalance()));
            esUser.getValidBalanceDetail().add(new BalanceDetailDO(coinEnum.getCode(), userAccount.getValidBalance()));
            esUser.getFreezeBalanceDetail().add(new BalanceDetailDO(coinEnum.getCode(), userAccount.getFreeze()));
        }
        if (esUser.getBalanceDetail().size()>0) reportService.userSynch(esUser);
    }

    @Override
    public HandlerResponse transferRecover(String orderId) {
        try {
            return glFundUserAccountBusiness.transferRecover(orderId, false);
        } catch (GlobalException e) {
            log.error("transferRecover orderId = {} no balance change.", orderId);
            HandlerResponse response = HandlerResponse.generateRespoese(orderId, FundReportEvent.TRANSFER_ROLLBACK.value(),
            HandlerResponseCode.FAIL.getCode(), "系统系统，请稍后重试");
            return response;
        }
    }

    @Override
    public void doCommissionApprove(Integer userId, BigDecimal amount,DigitalCoinEnum coinEnum, Date now) throws GlobalException {
        DigitalUserAccount account = glFundUserAccountBusiness.getUserAccount(userId,coinEnum);
        RPCResponse<GlUserDO> byId = glUserService.findById(userId);
        GlUserDO user = byId.getData();
        glFundUserAccountBusiness.addBalance(userId, amount,DigitalCoinEnum.CNY);

        //发放佣金重置可提现额度
        FundProxyAccount proxyAccount = fundProxyAccountBusiness.selectForUpdate(userId);
        if (!ObjectUtils.isEmpty(proxyAccount)) {
            proxyAccount.setValidWithdrawal(amount);
            fundProxyAccountBusiness.updateByPrimaryKeySelective(proxyAccount);
        }

        BalanceChangeReport balanceChangeReport = new BalanceChangeReport();
        balanceChangeReport.setOrderType(ProjectConstant.BalanceType.COMMISSION);
        balanceChangeReport.setCoin(coinEnum.getCode());
        balanceChangeReport.setTimestamp(now);
        balanceChangeReport.setReallyAmount(amount.longValue() * 10000);
        balanceChangeReport.setFee(0L);
        balanceChangeReport.setBalanceAfter(account.getBalance().add(amount).longValue() * 10000);
        balanceChangeReport.setBalanceBefore(account.getBalance().longValue() * 10000);
        balanceChangeReport.setAmount(amount.longValue() * 10000);
        balanceChangeReport.setOrderId(redisService.getTradeNo(GameOrderPrefix.GAME_YJ.getCode()));
        balanceChangeReport.setUid(userId);
        reportService.balanceChangeReport(balanceChangeReport);

        //上报代理交易明细报表
        long amountLong = amount.multiply(BigDecimal.valueOf(10000)).longValue();
        CommissionReport commissionReport = new CommissionReport();
        commissionReport.setAmount(amountLong);
        commissionReport.setCoin(coinEnum.getCode());
        commissionReport.setBalanceBefore(account.getBalance().multiply(BigDecimal.valueOf(10000)).longValue());
        commissionReport.setBalanceAfter((account.getBalance().add(amount)).multiply(BigDecimal.valueOf(10000)).longValue());
        commissionReport.setUid(userId);
        commissionReport.setUserType(UserTypeEnum.PROXY);
        commissionReport.setUserName(user.getUsername());
        commissionReport.setRemark("佣金");
        commissionReport.setStatus(1);
        commissionReport.setParentName(user.getParentName());
        commissionReport.setFinishTime(new Date());
        reportService.commssionReport(commissionReport);
    }

    @Override
    public RPCResponse<Void> createFundAccount(GlUserDO user, DigitalCoinEnum coin, String creator) {
        try {
            glFundUserAccountBusiness.createFundAccount(user,coin, creator);
            return RPCResponseUtils.buildSuccessRpcResponse(null);
        } catch (Exception e) {
            log.error(e.getMessage(), e);
            RPCResponse response = RPCResponse.newBuilder().fail(RPCResponseCode.FAIL_DEFAULT).
                    setMessage(e.getMessage()).build();
            return response;
        }
    }

    @Override
    public HandlerResponse userAccountChange(UserAccountChangeDO userAccountChangeDO) {
        log.info("userAccountChange = {}", userAccountChangeDO);
        FundUserBalanceChangeVO balanceChangeVO = new FundUserBalanceChangeVO();
        BeanUtils.copyProperties(userAccountChangeDO, balanceChangeVO);
        log.info("balanceChangeVO = {}", balanceChangeVO);
        try {
            if (balanceChangeVO.getMultiple() != null && balanceChangeVO.getMultiple() != 0) {
                balanceChangeVO.setFreezeAmount(balanceChangeVO.getAmount().multiply(new BigDecimal(balanceChangeVO.getMultiple())));
            } else {
                balanceChangeVO.setFreezeAmount(BigDecimal.ZERO);
            }

            BigDecimal balance = glFundUserAccountBusiness.doUserAccountChange(balanceChangeVO, userAccountChangeDO.getIsFake(), userAccountChangeDO.getType(), userAccountChangeDO.getSubType(),DigitalCoinEnum.CNY.getCode());
            HandlerResponse handlerResponse = HandlerResponse.generateRespoese(userAccountChangeDO.getTradeId(), null, HandlerResponseCode.SUCCESS.getCode(), null);

            //和活动约定返回账变前后金额
            Map<String, Object> extraInfo = handlerResponse.getExtraInfo();
            extraInfo.put("amountBefore", balance.subtract(userAccountChangeDO.getAmount()));
            extraInfo.put("amountAfter", balance);
            return handlerResponse;
        } catch (GlobalException e) {
            log.error(e.getExtraMessage(), e);
            HandlerResponse handlerResponse = HandlerResponse.generateRespoese(userAccountChangeDO.getTradeId(), null, HandlerResponseCode.FAIL.getCode(), e.getExtraMessage());
            return handlerResponse;
        }
    }

    @Override
    public RPCResponse<BigDecimal> getUserAccountBalance(Integer userId) {
        return RPCResponseUtils.buildSuccessRpcResponse(glFundUserAccountBusiness.getUserBalance(userId));
    }

    @Override
    public RPCResponse<BigDecimal> getUserAccountBalance(Integer userId, DigitalCoinEnum coinEnum) {
        return RPCResponseUtils.buildSuccessRpcResponse(glFundUserAccountBusiness.getUserBalance(userId, coinEnum));
    }

}
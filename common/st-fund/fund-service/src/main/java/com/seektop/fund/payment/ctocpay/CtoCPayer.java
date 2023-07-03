package com.seektop.fund.payment.ctocpay;

import com.alibaba.fastjson.JSON;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.constant.FundConstant;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.C2CConfigDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.common.C2CRechargeOrderCreateResult;
import com.seektop.fund.handler.C2COrderCallbackHandler;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import com.seektop.fund.payment.niubipay.PaymentInfo;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

@Slf4j
@Service(FundConstant.PaymentChannel.C2CPay + "")
public class CtoCPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler, GlRechargeCancelHandler, GlPaymentHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private GlRechargeMapper glRechargeMapper;

    @Resource
    private C2COrderCallbackHandler c2COrderCallbackHandler;

    @Resource
    private RedisService redisService;


    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result)  {
        try {
            log.info("CtoCPayer_recharge_prepare_params:{}", JSON.toJSONString(req));
            GlUserDO userDO = new GlUserDO();
            userDO.setId(req.getUserId());
            userDO.setUsername(req.getUsername());
            C2CRechargeOrderCreateResult rechargeResult = c2COrderCallbackHandler.rechargeCreate(userDO,req.getAmount(),req.getOrderId(), req.getIp());
            log.info("CtoCPayer_recharge_prepare_result:{}", JSON.toJSONString(rechargeResult));
            if (ObjectUtils.isEmpty(rechargeResult) || rechargeResult.getCode() != 1) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT);
                result.setErrorMsg(rechargeResult == null ? "创建充值订单错误" : rechargeResult.getMessage());
                return;
            }

            GlWithdraw glWithdraw = glWithdrawBusiness.findById(rechargeResult.getWithdrawOrderId());
            if (ObjectUtils.isEmpty(glWithdraw)) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT);
                result.setErrorMsg("匹配的提现订单不存在");
                return;
            }
            BankInfo bankInfo = new BankInfo();
            bankInfo.setBankName(glWithdraw.getBankName());
            bankInfo.setCardNo(glWithdraw.getCardNo());
            bankInfo.setName(glWithdraw.getName());
            bankInfo.setBankId(glWithdraw.getBankId());
            bankInfo.setBankBranchName(glWithdraw.getBankName());
            bankInfo.setKeyword(glWithdraw.getUsername());
            result.setBankInfo(bankInfo);
            result.setThirdOrderId(glWithdraw.getOrderId());
        } catch (Exception e) {
            log.error("撮合系统下单异常",e);
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT);
            result.setErrorMsg("极速支付下单异常:请选择其他支付");
            return;
        }

    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("CtoCPayer_recharge_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderId");
        String status = resMap.get("status");
        String thirdOrderId = resMap.get("thirdOrderId");
        if (StringUtils.isEmpty(orderId) || StringUtils.isEmpty(status)) {
            return null;
        }
        GlRecharge recharge = glRechargeMapper.selectByPrimaryKey(orderId);
        // 1：待付款 2：待确认到账  3：成功
        if (!ObjectUtils.isEmpty(recharge) && status.equals("3")) {
            RechargeNotify notify = new RechargeNotify();
            notify.setAmount(recharge.getAmount().setScale(2, RoundingMode.DOWN));
            notify.setFee(BigDecimal.ZERO);
            notify.setOrderId(orderId);
            notify.setRsp("success");
            notify.setThirdOrderId(thirdOrderId);
            log.info("ctcPayer_recharge_notify:{}", JSON.toJSONString(notify));
            return notify;
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) {
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(req));
        try {
            C2CConfigDO configDO = RedisTools.valueOperations().get(KeyConstant.C2C.C2C_CONFIG, C2CConfigDO.class);

            BigDecimal amount = req.getAmount().subtract(req.getFee()).setScale(0,RoundingMode.DOWN);
            if (!configDO.getChooseAmounts().contains(Integer.valueOf(amount.toString()))) {
                result.setResData("faild");
                result.setValid(false);
                result.setMessage("出款金额不在极速提现配置金额中");
                return result;
            }
            log.info("CtoCPayer_withdraw_transfer_params:{}", JSON.toJSONString(req));
            GlUserDO userDO = new GlUserDO();
            userDO.setId(req.getUserId());
            userDO.setUsername(req.getUsername());
            c2COrderCallbackHandler.withdrawCreate(userDO, req.getAmount().subtract(req.getFee()), req.getOrderId(), req.getIp());
            log.info("CtoCPayer_withdraw_transfer_result");

            result.setResData("success");
            result.setValid(true);
            result.setMessage("ok");
            return result;
        } catch (Exception e) {
            log.error("极速支付下单异常:请选择其他商户", e);
        }
        result.setResData("faild");
        result.setValid(false);
        result.setMessage("极速支付下单异常:请选择其他商户");
        return result;

    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("CtoCPayer_doTransferNotify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderId");
        String status = resMap.get("status");
        String thirdOrderId = resMap.get("thirdOrderId");
        if (StringUtils.isEmpty(orderId) || StringUtils.isEmpty(status)) {
            return null;
        }
        GlWithdraw glWithdraw = glWithdrawBusiness.findById(orderId);
        if (null == glWithdraw) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setThirdOrderId(thirdOrderId);
        //1：待付款 2：待确认到账  3：成功
        if ("3".equals(status)) {
            notify.setStatus(0);
            notify.setRsp("success");
        } else if (false) {
            notify.setStatus(1);
            notify.setRsp("success");
        } else {
            notify.setStatus(2);
        }
        log.info("CtoCPayer_doTransferNotify_notify:{}", JSON.toJSONString(notify));
        return notify;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        WithdrawNotify notify = new WithdrawNotify();
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setStatus(2);
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return new BigDecimal(999999);
    }

    @Override
    public boolean innerPay(GlPaymentMerchantaccount account, Integer paymentId) {
        return true;
    }

    @Override
    public Integer showType(GlPaymentMerchantaccount account, Integer paymentId) {
        return FundConstant.ShowType.DETAIL;
    }

    @Override
    public boolean needName(GlPaymentMerchantaccount account, Integer paymentId) {
        return true;
    }

    @Override
    public boolean needCard(GlPaymentMerchantaccount account, Integer paymentId) {
        return false;
    }

    @Override
    public BigDecimal paymentRate(GlPaymentMerchantaccount account, Integer paymentId) {
        return new BigDecimal(6.3);
    }

    @Override
    public BigDecimal withdrawRate(GlPaymentMerchantaccount account, Integer paymentId) {
        return new BigDecimal(6.3);
    }

    @Override
    public PaymentInfo payments(GlPaymentMerchantaccount account, BigDecimal amount) throws GlobalException {
        return null;
    }

    @Override
    public void cancel(GlPaymentMerchantaccount payment, GlRecharge req)  {
        try {
            GlUserDO userDO = new GlUserDO();
            userDO.setUsername(req.getUsername());
            userDO.setId(req.getUserId());
            log.info("CtoCPayer_recharge_cancel:{}", JSON.toJSONString(req));
            c2COrderCallbackHandler.rechargeCancel(userDO,req.getOrderId());
        } catch (Exception e) {
            log.error("C2CPay_Cancel_Error", e);
        }
    }
}

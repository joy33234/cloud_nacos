package com.seektop.fund.payment.DigitalPay;

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
@Service(FundConstant.PaymentChannel.DigitalPay + "")
public class DigitalPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler, GlRechargeCancelHandler, GlPaymentHandler {

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


    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) {
        log.info("DigitalPayer_withdraw_transfer_params:{}", JSON.toJSONString(req));

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(req));
        result.setResData("success");
        result.setValid(true);
        result.setMessage("ok");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("DigitalPayer_doTransferNotify_resMap:{}", JSON.toJSONString(resMap));
        return null;
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

    }
}

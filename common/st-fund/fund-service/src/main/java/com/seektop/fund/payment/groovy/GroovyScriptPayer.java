package com.seektop.fund.payment.groovy;

import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAccountBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawReceiveInfoBusiness;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import com.seektop.fund.payment.niubipay.PaymentInfo;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static com.seektop.constant.fund.Constants.FUND_COMMON_OFF;
import static com.seektop.fund.payment.groovy.GroovyFunctionEnum.*;

/**
 * @Auther: walter
 * @Date: 7/19/20 10:11
 * @Description:
 */
@Slf4j
@Service(FundConstant.PaymentChannel.GROOVYPAY + "")
public class GroovyScriptPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler, GlRechargeCancelHandler, GlPaymentHandler {

    @Resource
    private OkHttpUtil okHttpUtil;

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private GlWithdrawMapper withdrawMapper;

    @Resource
    private GlRechargeMapper rechargeMapper;

    @Resource
    private GlWithdrawBusiness withdrawBusiness;

    @Resource
    private GlRechargeBusiness rechargeBusiness;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @Resource
    private GlPaymentMerchantAccountBusiness glPaymentMerchantAccountBusiness;

    @Resource
    private OkHttpClient okHttpClient;

    @Resource
    private RedisService redisService;

    @Resource
    private GlWithdrawReceiveInfoBusiness glWithdrawReceiveInfoBusiness;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return (RechargeNotify) invokeMethod(account, GROOVY_FUNCTION_RESULT, new Object[]{okHttpUtil, merchant, account, resMap, resourceMap()});
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        invokeMethod(account, GROOVY_FUNCTION_PAY, new Object[]{okHttpUtil, merchant, account, req, result, resourceMap()});
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return (RechargeNotify) invokeMethod(account, GROOVY_FUNCTION_NOTIFY, new Object[]{okHttpUtil, merchant, account, resMap, resourceMap()});
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return (RechargeNotify) invokeMethod(account, GROOVY_FUNCTION_QUERY, new Object[]{okHttpUtil, account, orderId, resourceMap()});
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        return (WithdrawResult) invokeMethod(merchantAccount, GROOVY_FUNCTION_WITHDRAW_PAY, new Object[]{okHttpUtil, merchantAccount, req, resourceMap()});
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        return (WithdrawNotify) invokeMethod(merchant, GROOVY_FUNCTION_WITHDRAW_NOTIFY, new Object[]{okHttpUtil, merchant, resMap, resourceMap()});
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        return (WithdrawNotify) invokeMethod(merchant, GROOVY_FUNCTION_WITHDRAW_QUERY, new Object[]{okHttpUtil, merchant, orderId, resourceMap()});
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return (BigDecimal) invokeMethod(merchantAccount, GROOVY_FUNCTION_BALANCE_QUERY, new Object[]{okHttpUtil, merchantAccount, resourceMap()});
    }


    private String findScript(GlPaymentMerchantaccount account) {
        account = glPaymentMerchantAccountBusiness.findOne(account.getMerchantId());
        if (Objects.equals(account.getEnableScript(), FUND_COMMON_OFF)) {
            log.error("入款商户[{} {}]未启用脚本，无法使用[{}]", account.getChannelName(), account.getMerchantCode(), this.getClass().getSimpleName());
            throw new RuntimeException("入款商户未启用脚本");
        }
        String script = account.getScript();
        if (StringUtils.isBlank(script)) {
            log.error("入款商户[{} {}]启用了脚本，但没有配置脚本[script:{}]", account.getChannelName(), account.getMerchantCode(), account.getScript());
            throw new RuntimeException("入款商户启用了脚本，但没有配置脚本");
        }
        return script;
    }


    private String findScript(GlWithdrawMerchantAccount account) {
        if (Objects.equals(account.getEnableScript(), FUND_COMMON_OFF)) {
            log.error("出款商户[{} {}]未启用脚本，无法使用[{}]", account.getChannelName(), account.getMerchantCode(), this.getClass().getSimpleName());
            throw new RuntimeException("出款商户未启用脚本");
        }
        String script = account.getScript();
        if (StringUtils.isBlank(script)) {
            log.error("出款商户[{} {}]启用了脚本，但没有配置脚本[script:{}]", account.getChannelName(), account.getMerchantCode(), account.getScript());
            throw new RuntimeException("出款商户启用了脚本，但没有配置脚本");
        }
        return script;
    }

    private Object invokeMethod(GlPaymentMerchantaccount glPaymentMerchantaccount, GroovyFunctionEnum functionEnum, Object[] args) {
        return GroovyScriptUtil.invokeMethod(glPaymentMerchantaccount.getMerchantId(), findScript(glPaymentMerchantaccount), glPaymentMerchantaccount.getScriptSign(), functionEnum, args);
    }

    private Object invokeMethod(GlWithdrawMerchantAccount glWithdrawMerchantAccount, GroovyFunctionEnum functionEnum, Object[] args) {
        return GroovyScriptUtil.invokeMethod(glWithdrawMerchantAccount.getMerchantId(), findScript(glWithdrawMerchantAccount), glWithdrawMerchantAccount.getScriptSign(), functionEnum, args);
    }

    private Map<ResourceEnum, Object> resourceMap() {
        return new HashMap<ResourceEnum, Object>() {
            {
                put(ResourceEnum.GlPaymentChannelBankBusiness, glPaymentChannelBankBusiness);
                put(ResourceEnum.GlRechargeMapper, rechargeMapper);
                put(ResourceEnum.GlWithdrawBusiness, withdrawBusiness);
                put(ResourceEnum.GlWithdrawMapper, withdrawMapper);
                put(ResourceEnum.GlRechargeBusiness, rechargeBusiness);
                put(ResourceEnum.GlFundUserlevelBusiness, glFundUserlevelBusiness);
                put(ResourceEnum.OkHttpClient, okHttpClient);
                put(ResourceEnum.RedisService, redisService);
                put(ResourceEnum.GlWithdrawReceiveInfoBusiness, glWithdrawReceiveInfoBusiness);
            }
        };
    }


    @Override
    public void cancel(GlPaymentMerchantaccount payment, GlRecharge req) {
        GroovyScriptUtil.invokeMethod(payment.getMerchantId(), findScript(payment), payment.getScriptSign(), GROOVY_FUNCTION_CANCEL, new Object[]{okHttpUtil, payment, req, resourceMap()});
    }

    @Override
    public boolean innerPay(GlPaymentMerchantaccount account, Integer paymentId) {
        return (boolean) invokeMethod(account, GROOVY_FUNCTION_INNERPAY, new Object[]{account, paymentId});
    }

    @Override
    public boolean needName(GlPaymentMerchantaccount account, Integer paymentId) {
        return (boolean) invokeMethod(account, GROOVY_FUNCTION_NEEDNAME, new Object[]{account, paymentId});
    }

    @Override
    public boolean needCard(GlPaymentMerchantaccount account, Integer paymentId) {
        return (boolean) invokeMethod(account, GROOVY_FUNCTION_NEEDCARD, new Object[]{account, paymentId});
    }

    @Override
    public Integer showType(GlPaymentMerchantaccount account, Integer paymentId) {
        return (Integer) invokeMethod(account, GROOVY_FUNCTION_SHOWTYPE, new Object[]{account, paymentId});
    }

    @Override
    public BigDecimal paymentRate(GlPaymentMerchantaccount account, Integer paymentId) {
        return (BigDecimal) invokeMethod(account, GROOVY_FUNCTION_PAYMENTRATE, new Object[]{account, paymentId});
    }

    @Override
    public BigDecimal withdrawRate(GlPaymentMerchantaccount account, Integer paymentId) {
        return (BigDecimal) invokeMethod(account, GROOVY_FUNCTION_WITHDRAWRATE, new Object[]{account, paymentId});
    }

    @Override
    public PaymentInfo payments(GlPaymentMerchantaccount account, BigDecimal amount) {
        return (PaymentInfo) invokeMethod(account, GROOVY_FUNCTION_PAYMENTS, new Object[]{okHttpUtil,account, amount});
    }
}

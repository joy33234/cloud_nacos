
package com.seektop.fund.payment.jiufu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * 玖付支付接口
 *
 * @author tiger
 */
@Slf4j
@Service(FundConstant.PaymentChannel.JIUFU + "")
public class JiuFuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness paymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    private static final String SERVEL_PAY = "/unionOrder";//支付地址
    private static final String SERVEL_WITHDRAW = "/agentPay";//代付地址
    private static final String SERVEL_ORDER_QUERY = "/orderQuery";//订单查询地址
    private static final String SERVEL_BALANCE_QUERY = "/balanceQuery";//余额查询地址

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String service = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                service = "10106";
            } else {
                service = "10107";
            }
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                service = "10104";
            } else {
                service = "10105";
            }
        } else if (FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId() || FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            service = "10103";
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            service = "10102";
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId() || FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            service = "10101";
        } else if (FundConstant.PaymentType.ALI_TRANSFER == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                service = "10108";
            } else {
                service = "10109";
            }
        } else if (FundConstant.PaymentType.WECHAT_TRANSFER == merchant.getPaymentId()) {
            service = "10114";
        }
        prepareScan(merchant, payment, req, result, service);
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String service) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("partner", account.getMerchantCode());
            params.put("service", service);
            params.put("tradeNo", req.getOrderId());
            params.put("amount", req.getAmount() + "");
            params.put("notifyUrl", account.getNotifyUrl() + merchant.getId());
            params.put("resultType", "json");
            params.put("extra", "CZ");
            String sign = MD5.toAscii(params) + "&" + account.getPrivateKey();
            params.put("sign", MD5.md5(sign));

            log.info("JiufuPayer_Prepare_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(GlActionEnum.RECHARGE.getCode(), req.getUserId() + "", req.getUsername(), req.getOrderId());
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVEL_PAY, params, requestHeader);
            log.info("JiufuPayer_Prepare_resStr:{}", resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null || !json.getString("isSuccess").equals("T")) {
                result.setErrorCode(1);
                result.setErrorMsg(json == null ? "创建订单失败" : json.getString("msg"));
            } else {
                result.setRedirectUrl(json.getString("url"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("JiuFuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("outTradeNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        JSONObject json = this.queryOrder(account.getMerchantCode(), account.getPrivateKey(), orderId, account.getPayUrl(), "10302", GlActionEnum.RECHARGE_QUERY.getCode());//充值查询
        if (json == null) {
            return null;
        }
        // 订单状态判断标准: 0 处理中 1 成功 2 失败
        if ("1".equals(json.getString("status"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
            return pay;
        }
        return null;
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("partner", merchantAccount.getMerchantCode());
        params.put("service", "10201");
        params.put("tradeNo", req.getOrderId());
        params.put("amount", req.getAmount().subtract(req.getFee()).toString());
        params.put("bankCode", paymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
        params.put("bankCardNo", req.getCardNo());
        params.put("bankCardholder", req.getName());
        params.put("subsidiaryBank", "上海市");
        params.put("subbranch", "上海市");
        params.put("province", "上海市");
        params.put("city", "上海市");
        params.put("extra", "TX");
        params.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
        String sign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        log.info("JiuFuPayer_Transfer_params: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(GlActionEnum.WITHDRAW.getCode(), req.getUserId() + "  ", req.getUsername(), req.getOrderId());
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVEL_WITHDRAW, params, requestHeader);
        log.info("JiuFuPayer_Transfer_resStr: {}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("msg"));
            return result;
        }
        if (StringUtils.isNotEmpty(json.getString("isSuccess")) && json.getString("isSuccess").equals("T")) {
            result.setValid(true);
            result.setMessage("Success");
        } else {
            result.setValid(false);
            result.setMessage(StringUtils.isNotEmpty(json.getString("msg")) ? json.getString("msg") : "请联系技术支持");
        }
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("JiuFuPayer_PAY_doTransferNotify_resp:{}", JSON.toJSONString(resMap));
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        String orderId;
        if (json == null) {
            orderId = resMap.get("outTradeNo");
        } else {
            orderId = json.getString("outTradeNo");
        }
        if (StringUtils.isNotEmpty(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {

        JSONObject json = this.queryOrder(merchant.getMerchantCode(), merchant.getPrivateKey(), orderId, merchant.getPayUrl(), "10301", GlActionEnum.WITHDRAW_QUERY.getCode());//代付查询
        if (json == null) {
            return null;
        }

        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("amount"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark(json.getString("extra"));
        notify.setThirdOrderId("");

        Integer status = json.getInteger("status");//三方状态0:处理中，1：成功，2：失败 3，处理中，4：处理中  //系统状态：0成功，1失败,2处理中
        if (status.intValue() == 1) {
            notify.setStatus(0);
        } else if (status.intValue() == 2) {
            notify.setStatus(1);
        } else {
            notify.setStatus(2);
        }
        notify.setSuccessTime(new Date());
        return notify;

    }


    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("partner", merchantAccount.getMerchantCode());
        params.put("service", "10401");
        String sign = MD5.toAscii(params) + "&" + merchantAccount.getPrivateKey();
        params.put("sign", MD5.md5(sign));

        log.info("JiuFupayer_QueryBalance_reqMap: {}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), "", "", "");
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVEL_BALANCE_QUERY, params, requestHeader);
        log.info("JiuFupayer_QueryBalance_resStr: {}", resStr);

        JSONObject json = this.checkResponse(resStr);
        if (json != null) {
            return json.getBigDecimal("balance");
        }
        return BigDecimal.ZERO;
    }

    /**
     * 充值和代付订单查询
     *
     * @param merchantCode
     * @param privateKey
     * @param orderId
     * @param payUrl
     * @return
     */
    private JSONObject queryOrder(String merchantCode, String privateKey, String orderId, String payUrl, String service, String actionCode) {
        Map<String, String> params = new HashMap<String, String>();
        params.put("partner", merchantCode);
        params.put("service", service);
        params.put("outTradeNo", orderId);
        String sign = MD5.toAscii(params) + "&" + privateKey;
        params.put("sign", MD5.md5(sign));
        GlRequestHeader requestHeader = this.getRequestHeard(actionCode, "", "", orderId);
        log.info("JiufuPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = okHttpUtil.post(payUrl + SERVEL_ORDER_QUERY, params, requestHeader);
        log.info("JiufuPayer_query_resStr:{}", resStr);
        return this.checkResponse(resStr);
    }

    /**
     * 获取头部信息
     *
     * @param actionCode
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String actionCode, String userId, String userName, String orderId) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(actionCode)
                .channelId(PaymentMerchantEnum.JIUFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JIUFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }

    /**
     * 检验返回数据
     *
     * @param response
     * @return
     */
    private JSONObject checkResponse(String response) {
        if (StringUtils.isEmpty(response)) {
            return null;
        }
        JSONObject json = JSON.parseObject(response);
        if (json != null && json.getString("isSuccess").equals("T")) {
            return json;
        }
        return null;
    }
}

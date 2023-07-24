package com.seektop.fund.payment.yixunpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.model.*;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;


@Slf4j
@Service(FundConstant.PaymentChannel.YIXUNPAY + "")
public class YixunPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private OkHttpUtil okHttpUtil;
    @Resource
    private GlRechargeBusiness rechargeService;
    @Resource
    private GlPaymentChannelBankBusiness channelBankBusiness;

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        String payType = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "alipay_scand0";
            } else {
                payType = "alipay_H5d0";
            }
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                payType = "weixin_scand0";
            } else {
                payType = "weixin_H5d0";
            }
        }
        prepareToScan(merchant, account, req, result, payType);
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String payType) throws GlobalException {
        Map<String, String> params = new HashMap<>();
        params.put("pay_type", payType);
        params.put("unique_id", account.getMerchantCode());
        params.put("price", req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("notice_url", account.getNotifyUrl() + merchant.getId());
        params.put("order_number", req.getOrderId());
        params.put("return_url", account.getNotifyUrl() + merchant.getId());
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YixunPayer_recharge_prepare_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE.getCode())
                .channelId(PaymentMerchantEnum.YIXUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIXUN_PAY.getPaymentName())
                .userId(req.getUserId() + "")
                .userName(req.getUsername())
                .tradeId(req.getOrderId())
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/getPayUrl.html", params, requestHeader);
        log.info("YixunPayer_recharge_prepare_resp:{}", resp);
        JSONObject json = this.checkResponse(resp, false);
        if (json == null) {
            throw new GlobalException("创建订单失败");
        }
        result.setRedirectUrl(json.getString("data"));
    }


    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("YixunPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderNum");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        GlRecharge recharge = rechargeService.findById(orderId);
        if (recharge == null) {
            return null;
        }
        Map<String, String> params = new HashMap<>();
        params.put("price", recharge.getAmount() + "");
        params.put("unique_id", account.getMerchantCode());
        params.put("Order_id", orderId);
        String toSign = MD5.toAscii(params);
        toSign += "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YixunPayer_query_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(GlActionEnum.RECHARGE_QUERY.getCode())
                .channelId(PaymentMerchantEnum.YIXUN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YIXUN_PAY.getPaymentName())
                .userId("")
                .userName("")
                .tradeId(orderId)
                .build();
        String resp = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/OrderQuery.html", params, requestHeader);
        log.info("YixunPayer_query_resp:{}", resp);
        if (StringUtils.isEmpty(resp)) {
            return null;
        }
        JSONObject json = this.checkResponse(resp, true);
        if ("1".equals(json.getString("ispay"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("goods_price").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId("");
            return pay;
        }
        return null;
    }


    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount account, GlWithdraw req) throws GlobalException {
        WithdrawResult result = new WithdrawResult();
        Map<String, Object> payCodeMap = getPayCode(account);
        BigDecimal amount = (BigDecimal) payCodeMap.get("amount");

        if (req.getAmount().compareTo(amount) > 1) {
            result.setValid(false);
            result.setMessage("商户余额不足.");
            return result;
        }

        Map<String, String> params = new HashMap<String, String>();
        params.put("price", req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("unique_id", account.getMerchantCode());
        params.put("order_number", req.getOrderId());
        params.put("api_type", payCodeMap.get("api_type").toString());
        params.put("pay_type", payCodeMap.get("pay_type").toString());
        params.put("cardname", channelBankBusiness.getBankName(req.getBankId(), account.getChannelId()));//银行名称
        params.put("bank_code", channelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));//银行卡编号
        params.put("cardno", req.getCardNo());//银行卡编号
        params.put("name", req.getName());//持卡人姓名
        params.put("notice_url", account.getNotifyUrl() + account.getMerchantId());//持卡人姓名
        params.put("subcardname", "上海市");

        String toSign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("YixunPayer_Transfer_paramMap:{}", JSON.toJSONString(params));
        String resStr = okHttpUtil.post(account.getPayUrl() + "/PayView/Index/Substitute.html", params);
        log.info("YixunPayer_Transfer_resStr:{}", resStr);
        if (StringUtils.isEmpty(resStr)) {
            return null;
        }
        JSONObject json = JSON.parseObject(resStr);
        result.setOrderId(req.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);
        if (json == null) {
            result.setValid(false);
            result.setMessage("API异常:请联系出款商户确认订单.");
            return result;
        }
        String message = com.seektop.fund.payment.yixunpay.StringUtils.unicodeToChinese(json.getString("data"));
        if (!"200".equals(json.getString("code"))) {
            result.setValid(false);
            result.setMessage(message);
            return result;
        }
        result.setValid(true);
        result.setMessage(message);
        result.setThirdOrderId("");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("YixunPayer_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderNum");
        if (org.springframework.util.StringUtils.isEmpty(orderId)) {
            log.info("YixunPayer_Notify_Exception:{}", JSON.toJSONString(resMap));
            return null;
        }
        return doTransferQuery(merchant, orderId);
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("unique_id", merchant.getMerchantCode());
        params.put("Order_id", orderId);//订单编号

        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign));
        log.info("Yixun_Transfer_Query_paramMap:{}", params);
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/PayView/Index/SubstituteResult.html", params);
        log.info("YixunPayer_Transfer_Query_resStr:{}", resStr);
        JSONObject json = this.checkResponse(resStr, true);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        notify.setAmount(json.getBigDecimal("goods_price"));
        notify.setMerchantCode(merchant.getMerchantCode());
        notify.setMerchantId(merchant.getMerchantId());
        notify.setMerchantName(merchant.getChannelName());
        notify.setOrderId(orderId);
        notify.setRemark("");
        notify.setSuccessTime(json.getDate("grant_time"));
        if (json.getString("is_forward").equals("1")) {//0-处理中，1-代付成功（其余均为失败）   --  0成功，1失败,2处理中
            notify.setStatus(0);
        } else if (json.getString("is_forward").equals("0")) {
            notify.setStatus(2);
        } else {
            notify.setStatus(1);
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchant) throws GlobalException {
        BigDecimal result = BigDecimal.ZERO;//余额yi
        JSONArray array = this.getBalanceData(merchant);

        if (array == null || array.size() <= 0) {
            return result;
        }

        for (int i = 0; i < array.size(); i++) {
            BigDecimal amount = array.getJSONObject(i).getBigDecimal("balance");
            result = result.add(amount);
        }
        return result;
    }

    /**
     * 查询余额接口
     *
     * @param merchant
     * @return
     */
    private JSONArray getBalanceData(GlWithdrawMerchantAccount merchant) {
        Map<String, String> params = new HashMap<>();
        params.put("unique_id", merchant.getMerchantCode());
        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey();
        params.put("sign", MD5.md5(toSign));

        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/PayView/Index/BalanceInquire.html", params);
        log.info("Yixun_Balance_Query_resStr:{}", resStr);
        JSONObject json = this.checkResponse(resStr, true);
        if (json == null) {
            return null;
        }
        String balanceArr = json.getString("payBalance");
        JSONArray array = JSON.parseArray(balanceArr);
        return array;
    }


    /**
     * 获取代付渠道与编码
     *
     * @param merchant
     * @return
     */
    public Map<String, Object> getPayCode(GlWithdrawMerchantAccount merchant) {
        Map<String, Object> map = new HashMap<>();
        JSONArray array = this.getBalanceData(merchant);

        if (array == null || array.size() <= 0) {
            return null;
        }
        BigDecimal maxAmount = BigDecimal.ZERO;
        map.put("amount", maxAmount);
        for (int i = 0; i < array.size(); i++) {
            BigDecimal amount = array.getJSONObject(i).getBigDecimal("balance");
            if (amount.compareTo(maxAmount) > 0) {
                map.put("api_type", array.getJSONObject(i).getString("api_type"));
                map.put("pay_type", array.getJSONObject(i).getString("pay_type"));
                map.put("amount", amount);
                maxAmount = amount;
            }
        }
        return map;
    }

    /**
     * 检验返回数据
     *
     * @param response
     * @return
     */
    private JSONObject checkResponse(String response, boolean data) {
        if (StringUtils.isEmpty(response)) {
            return null;
        }
        JSONObject json = JSON.parseObject(response);
        if (json == null || !"200".equals(json.getString("code"))) {
            return null;
        }
        if (data) {
            String dataStr = json.get("data").toString();
            JSONObject dataJson = JSON.parseObject(dataStr);
            return dataJson;
        }
        return json;
    }
}


package com.seektop.fund.payment.yitupay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
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
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 易途支付接口
 *
 * @author tiger
 */
@Slf4j
@Service(FundConstant.PaymentChannel.YITUPAY + "")
public class YituPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {


    @Resource
    private OkHttpUtil okHttpUtil;

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
        String bankcode = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            bankcode = "904";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            bankcode = "901";
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId() || FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            bankcode = "911";
        }
        prepareScan(merchant, payment, req, result, bankcode);
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String bankcode) {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("pay_memberid", account.getMerchantCode());
            params.put("pay_orderid", req.getOrderId());
            params.put("pay_applydate", DateUtils.format(req.getCreateDate(), DateUtils.YYYY_MM_DD_HH_MM_SS));
            params.put("pay_bankcode", bankcode);
            params.put("pay_notifyurl", account.getNotifyUrl() + merchant.getId());
            params.put("pay_callbackurl", account.getNotifyUrl() + merchant.getId());
            params.put("pay_amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "");

            String sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
            params.put("pay_md5sign", MD5.md5(sign).toUpperCase());
            params.put("pay_productname", "recharge");

            log.info("YituPayer_Prepare_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Index.html", params, requestHeader);
            log.info("YituPayer_Prepare_resStr:{}", resStr);

            if (StringUtils.isEmpty(resStr)) {
                throw new GlobalException("创建订单失败");
            }

            if(FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()){
                resStr = resStr.replace("/gateway/xfzf/pay.php",account.getPayUrl()+"/gateway/xfzf/pay.php");
            }
            result.setMessage(resStr);
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
        log.info("YituPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");
        if (resMap.get("returncode").equals("00") && StringUtils.isNotEmpty(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("pay_memberid", account.getMerchantCode());
        params.put("pay_orderid", orderId);

        String sign = MD5.toAscii(params) + "&key=" + account.getPrivateKey();
        params.put("pay_md5sign", MD5.md5(sign).toUpperCase());

        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        log.info("YituPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = okHttpUtil.post(account.getPayUrl() + "/Pay_Trade_query.html", params, requestHeader);
        log.info("YituPayer_query_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        // 订单状态判断标准:  NOTPAY-未支付 SUCCESS已支付
        if (json.getString("returncode").equals("00") && "SUCCESS".equals(json.getString("trade_state"))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("amount").setScale(2, RoundingMode.DOWN));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("transaction_id"));
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
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw withdraw) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("mchid", merchantAccount.getMerchantCode());
        params.put("out_trade_no", withdraw.getOrderId());
        params.put("money", withdraw.getAmount().subtract(withdraw.getFee()).setScale(2,RoundingMode.DOWN) + "");
        params.put("bankname", withdraw.getBankName());
        params.put("subbranch", "上海市");
        params.put("province", "上海市");
        params.put("city", "上海市");
        params.put("accountname", withdraw.getName());
        params.put("cardnumber", withdraw.getCardNo());

        String toSign = MD5.toAscii(params) + "&key=" + merchantAccount.getPrivateKey();
        params.put("pay_md5sign", MD5.md5(toSign).toUpperCase());

        log.info("YituPayer_doTransfer_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard(withdraw.getUserId() + "",withdraw.getUsername(),withdraw.getOrderId(), PaymentMerchantEnum.YITU_PAY.getCode() + "");
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Payment_Dfpay_add.html", params, requestHeader);
        log.info("YituPayer_doTransfer_resp:{}", resStr);

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(withdraw.getOrderId());
        result.setReqData(JSON.toJSONString(params));
        result.setResData(resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null || !json.getString("status").equals("success")) {
            result.setValid(false);
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单.": json.getString("msg"));
            return result;
        }
        result.setValid(true);
        result.setMessage(json.getString("msg"));
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        Map<String, String> params = new LinkedHashMap<>();
        params.put("mchid", merchant.getMerchantCode());
        params.put("out_trade_no", orderId);

        String toSign = MD5.toAscii(params) + "&key=" + merchant.getPrivateKey();
        String sign = MD5.md5(toSign).toUpperCase();
        params.put("pay_md5sign", sign);

        log.info("YituPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, PaymentMerchantEnum.YITU_PAY.getCode() + "");
        String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payment_Dfpay_query.html", params, requestHeader);
        log.info("YituPayer_doTransferQuery_resp:{}", resStr);

        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        WithdrawNotify notify = new WithdrawNotify();
        if (json.get("status").equals("success")) {
            notify.setAmount(json.getBigDecimal("amount"));
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(json.getString("out_trade_no"));
            notify.setThirdOrderId(json.getString("transaction_id"));
            if (json.getString("refCode").equals("1")) {//订单状态判断标准： 1 成功 2 失败 3 处理中 4 待处理 5 审核驳回 6 待审核  7 交易不存在 8未知状态    商户返回出款状态：0成功，1失败,2处理中
                notify.setStatus(0);
            } else if (json.getString("code").equals("2")) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
        }
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return BigDecimal.ZERO;
    }


    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.YITU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.YITU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }


}

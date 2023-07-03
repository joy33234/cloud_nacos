package com.seektop.fund.payment.huilianpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * 汇联支付接口
 *
 * @author tiger
 */
@Slf4j
@Service(FundConstant.PaymentChannel.HUILIANPAY + "")
public class HuilianPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    private static Map<String, String> productMap = new HashMap<>();

    static {
        productMap.put("20000203", "支付宝H5/WAP T0支付");
        productMap.put("20000303", "支付宝T0扫码支付");
        productMap.put("10000203", "微信H5/WAP T0支付");
    }

    private static final String SERVEL_PAY = "/roncoo-pay-web-gateway/cnpPay/initPay";//支付地址
    private static final String SERVEL_ORDER_QUERY = "/roncoo-pay-web-gateway/query/singleOrder";//订单查询地址

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        String productType = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            if (req.getClientType() == ProjectConstant.ClientType.PC) {
                productType = "20000303";
            } else {
                productType = "20000203";
            }
        } else if (FundConstant.PaymentType.QUICK_ALI_PAY == merchant.getPaymentId()) {
            productType = "20000201";
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            productType = "10000203";
        } else if (FundConstant.PaymentType.QUICK_WECHAT_PAY == merchant.getPaymentId()) {
            productType = "10000201";
        }
        prepareScan(merchant, payment, req, result, productType);
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String productType) throws GlobalException {
        try {
            Map<String, String> params = new HashMap<String, String>();
            params.put("payKey", account.getMerchantCode());
            params.put("productType", productType);
            params.put("outTradeNo", req.getOrderId());
            params.put("orderPrice", req.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "");
            params.put("orderTime", DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDDHHMMSS));
            params.put("productName", "CZ");
            params.put("orderIp", req.getIp());
            params.put("returnUrl", account.getNotifyUrl() + merchant.getId());
            params.put("notifyUrl", account.getNotifyUrl() + merchant.getId());

            String sign = MD5.toAscii(params) + "&paySecret=" + account.getPrivateKey();
            params.put("sign", MD5.md5(sign).toUpperCase());

            log.info("HuilianPayer_Prepare_resMap:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode());
            String resStr = okHttpUtil.post(account.getPayUrl() + SERVEL_PAY, params, requestHeader);
            log.info("HuilianPayer_Prepare_resStr{}", resStr);

            JSONObject json = JSON.parseObject(resStr);
            if (json == null) {
                throw new GlobalException("创建订单失败");
            }
            if (!json.getString("resultCode").equals("0000")) {
                throw new GlobalException("创建订单失败");
            }
            result.setRedirectUrl(json.getString("payMessage"));
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
        log.info("HuilianPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("outTradeNo");
        if (null != orderId && !"".equals(orderId)) {
            return this.query(account, orderId);
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("payKey", account.getMerchantCode());
        params.put("outTradeNo", orderId);
        String sign = MD5.toAscii(params) + "&paySecret=" + account.getPrivateKey();
        params.put("sign", MD5.md5(sign).toUpperCase());
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        log.info("HuilianPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = okHttpUtil.post(account.getPayUrl() + SERVEL_ORDER_QUERY, params, requestHeader);
        log.info("HuilianPayer_query_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        // 订单状态判断标准:  【SUCCESS】：支付成功 【T0,T1】 【FINISH】交易完成 【T1订单对账完成时返回该状态值】
        //【FAILED】：支付失败 【WAITING_PAYMENT】：等待支付
        if (json.getString("resultCode").equals("0000") &&
                ("SUCCESS".equals(json.getString("orderStatus")) || "FINISH".equals(json.getString("orderStatus")))) {
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(json.getBigDecimal("orderPrice"));
            pay.setFee(BigDecimal.ZERO);
            pay.setOrderId(orderId);
            pay.setThirdOrderId(json.getString("trxNo"));
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
                .channelId(PaymentMerchantEnum.HUILIAN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.HUILIAN_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }


}

package com.seektop.fund.payment.juheweixinpay;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.mapper.GlRechargeMapper;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.Map;

/**
 * 聚合微信
 *
 */
@Slf4j
@Service(FundConstant.PaymentChannel.JUHEWEIXINPAY + "")
public class JuheWeixinPayer implements GlPaymentRechargeHandler {

    @Resource
    private GlRechargeMapper rechargeMapper;

    @Resource
    private OkHttpUtil okHttpUtil;

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount merchantaccount, RechargePrepareDO req, GlRechargeResult result) {

        String requestNo = System.currentTimeMillis() + "";//交易流水号
        String productId = "SY01";
        String transId = "01";
        String transAmt = req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString();

        StringBuilder signStr = new StringBuilder();
        signStr.append(requestNo);
        signStr.append(productId);
        signStr.append(transId);
        signStr.append(merchantaccount.getMerchantCode());
        signStr.append(req.getOrderId());
        signStr.append(transAmt);
        signStr.append(merchantaccount.getPrivateKey());
        String sign = MD5.md5(signStr.toString());

        StringBuilder sb = new StringBuilder();
        sb.append(merchantaccount.getPayUrl()).append("/orgReq/cashierPayH5?");
        sb.append("requestNo=").append(requestNo).append("&");
        sb.append("version=").append("V1.0").append("&");
        sb.append("productId=").append("SY01").append("&");
        sb.append("transId=").append("01").append("&");
        sb.append("merNo=").append(merchantaccount.getMerchantCode()).append("&");
        sb.append("orderDate=").append(DateUtils.format(req.getCreateDate(), DateUtils.YYYYMMDD)).append("&");
        sb.append("orderNo=").append(req.getOrderId()).append("&");
        sb.append("notifyUrl=").append(merchantaccount.getNotifyUrl()+ merchant.getId()).append("&");
        sb.append("transAmt=").append(transAmt).append("&");
        sb.append("signature=").append(sign);
        log.info("JUHEWEIXINPAY_PrepareToWangyin_Prepare:{}", sb.toString());
        result.setRedirectUrl(sb.toString());
    }



    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        log.info("JUHEWEIXINPAY_Notify_resMap:{}", JSON.toJSONString(resMap));
        String orderid = resMap.get("orderNo");
        if (StringUtils.isNotEmpty(orderid)) {
            return query(payment, orderid);
        } else {
            return null;
        }
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {

        GlRecharge recharge = rechargeMapper.selectByPrimaryKey(orderId);
        if (recharge == null){
            return null;
        }
        String requestNo = System.currentTimeMillis() + "";//交易流水号
        String transId = "04";

        StringBuilder signStr = new StringBuilder();
        signStr.append(requestNo);
        signStr.append(transId);
        signStr.append(account.getMerchantCode());
        signStr.append(orderId);
        signStr.append(account.getPrivateKey());
        String sign = MD5.md5(signStr.toString());

        Map<String, String> params = new HashMap<String, String>();
        params.put("requestNo", requestNo);
        params.put("version", "V1.0");
        params.put("transId", transId);
        params.put("merNo", account.getMerchantCode());
        params.put("orderDate", DateUtils.format(recharge.getCreateDate(), DateUtils.YYYYMMDD));
        params.put("orderNo", orderId);
        params.put("orderAmt", recharge.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
        params.put("signature", sign);

        log.info("JUHEWEIXINPAY_Query_reqMap:{}", JSON.toJSONString(params));
        GlRequestHeader requestHeader = this.getRequestHeard("","",orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        String resStr = okHttpUtil.post(account.getPayUrl() + "/orgReq/trQue", params, requestHeader);
        log.info("JUHEWEIXINPAY_Query_resStr:{}", resStr);
        JSONObject json = JSON.parseObject(resStr);
        if (json == null) {
            return null;
        }
        if ("0000".equals(json.getString("respCode")) && "0000".equals(json.getString("origRespCode"))) {//原交易应答码  交易成功：0000
            RechargeNotify pay = new RechargeNotify();
            pay.setAmount(recharge.getAmount());
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
        return notify(merchant, payment, resMap);
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
                .channelId(PaymentMerchantEnum.JUHEWEIXIN_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.JUHEWEIXIN_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }
}

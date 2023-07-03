package com.seektop.fund.payment.hipay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.entity.StringEntity
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import java.math.RoundingMode
import java.security.InvalidKeyException
import java.security.NoSuchAlgorithmException

/**
 * HipayScript  接口
 *
 * @author joy
 */
public class HipayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HipayScript_recharge.class)

    private OkHttpUtil okHttpUtil

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param merchantaccount
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount merchantaccount = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        try {
            Map<String, Object> params = new HashMap<String, Object>()
            params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
            params.put("out_trade_no", req.getOrderId())
            params.put("notify_url", merchantaccount.getNotifyUrl() + merchant.getId())
            params.put("paid_name", req.getFromCardUserName())

            log.info("HipayScript_Prepare_resMap:{}", JSON.toJSONString(params))
            String resStr = getPostResult(merchantaccount.getPayUrl() + "/api/transaction",  params,  merchantaccount.getPrivateKey())

            JSONObject json = JSON.parseObject(resStr)
            log.info("HipayScript_Prepare_resStr:{}", json)
            if (json == null) {
                result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
                result.setErrorMsg("网络请求超时，稍后重试")
                return
            }
            if (StringUtils.isEmpty(json.getString("uri"))) {
                result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
                result.setErrorMsg(StringUtils.isEmpty(json.getString("message")) ? "商户异常" : json.getString("message"))
                return
            }

            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            result.setRedirectUrl(json.getString("uri"))
            result.setThirdOrderId(json.getString("trade_no"));
        } catch (Exception e) {
            e.printStackTrace()
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
    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        Map<String, String> resMap = args[3] as Map<String, String>
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        log.info("HipayScript_notify_resp:{}", JSON.toJSONString(resMap))
        JSONObject json = JSON.parseObject(resMap.get("reqBody"))
        String thirdOrderId = json.getString("trade_no")
        String orderId = json.getString("out_trade_no")
        if (StringUtils.isNotEmpty(orderId) && StringUtils.isNotEmpty(thirdOrderId)) {
            return this.payQuery(okHttpUtil, account, orderId)
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        log.info("HipayScript_query_params_orderId:{}", orderId)

        String resStr = getGetResult(account.getPayUrl() + "/api/transaction/" + orderId,   account.getPrivateKey())

        log.info("HipayScript_query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null) {
            return null
        }
        // 订单状态判断标准:  success => 成功  progress => 进行中 timeout => 逾时
        if (json.getString("status") == ("success")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(json.getString("trade_no"))
            pay.setRsp("ok")
            return pay
        }
        return null
    }


    /**
     * 签名
     *
     * @param value
     * @param accessToken
     * @return
     * @throws NoSuchAlgorithmException
     * @throws InvalidKeyException* @throws UnsupportedEncodingException
     */
    public String getSign(String value, String accessToken)
            throws NoSuchAlgorithmException, InvalidKeyException, UnsupportedEncodingException {
        Base64.Encoder encoder = Base64.getEncoder()
        Mac sha256 = Mac.getInstance("HmacSHA256")
        sha256.init(new SecretKeySpec(accessToken.getBytes("UTF8"), "HmacSHA256"))

        return encoder.encodeToString(sha256.doFinal(value.getBytes("UTF8")))
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId.toString())
                .channelName(channelName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }


    /**
     * 是否为内部渠道
     *
     * @param args
     * @return
     */
    public boolean innerpay(Object[] args) {
        return false
    }

    /**
     * 根据支付方式判断-转帐是否需要实名
     *
     * @param args
     * @return
     */
    public boolean needName(Object[] args) {
        Integer paymentId = args[1] as Integer
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            return true
        }
        return false
    }

    /**
     * 充值是否需要卡号
     *
     * @param args
     * @return
     */
    public boolean needCard(Object[] args) {
        return false
    }

    /**
     * 是否显示充值订单祥情
     *
     * @param args
     * @return
     */
    public Integer showType(Object[] args) {
        return FundConstant.ShowType.NORMAL
    }

    /**
     * 充值USDT汇率
     *
     * @param args
     * @return
     */
    public BigDecimal paymentRate(Object[] args) {
        return null
    }


    private String getPostResult(String url,  Map<String, String> map, String privateKey) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault()
            HttpPost httpPost = new HttpPost(url)
            httpPost.addHeader("Accept", "application/json")
            httpPost.addHeader("Content-Type", "application/json")
            httpPost.addHeader("Authorization", "Bearer " + privateKey)
            httpPost.setEntity(new StringEntity(JSONObject.toJSONString(map), "UTF-8"))

            CloseableHttpResponse response = httpclient.execute(httpPost)
            return EntityUtils.toString(response.getEntity(), "UTF-8")
        } catch (IOException e) {
            e.printStackTrace()
        }
        return null
    }


    private String getGetResult(String url,  String privateKey) {
        try {
            CloseableHttpClient httpclient = HttpClients.createDefault()

            HttpGet httpGet = new HttpGet(url)
            httpGet.addHeader("Accept", "application/json")
            httpGet.addHeader("Authorization", "Bearer " + privateKey)

            CloseableHttpResponse response = httpclient.execute(httpGet)
            return EntityUtils.toString(response.getEntity(), "UTF-8")
        } catch (IOException e) {
            e.printStackTrace()
        }
        return null
    }


}

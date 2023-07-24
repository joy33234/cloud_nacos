package com.seektop.fund.payment.gongfubao

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.parser.Feature
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.apache.http.NameValuePair
import org.apache.http.client.entity.UrlEncodedFormEntity
import org.apache.http.client.methods.CloseableHttpResponse
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.methods.HttpPost
import org.apache.http.impl.client.CloseableHttpClient
import org.apache.http.impl.client.HttpClients
import org.apache.http.message.BasicNameValuePair
import org.apache.http.util.EntityUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

public class GongfubaoScript_Recharge {

    private static final Logger log = LoggerFactory.getLogger(GongfubaoScript_Recharge.class)

    private OkHttpUtil okHttpUtil

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        Map<String, String> param = new LinkedHashMap<>()
        String keyValue = account.getPrivateKey()// AccessToken
        param.put("trade_no", req.getOrderId())
        param.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        param.put("notify_url", account.getNotifyUrl() + merchant.getId())
        param.put("ip", req.getIp())
        param.put("player_name", req.getFromCardUserName())
        //提交无签名校验，但需设置Authorization的值
        CloseableHttpClient httpclient = HttpClients.createDefault()
        HttpPost httpPost = new HttpPost(account.getPayUrl() + "/api/transaction")
        httpPost.addHeader("Content-Type", "application/x-www-form-urlencoded")
        httpPost.addHeader("Authorization", "Bearer " + keyValue)//Bearer后有空格
        List<NameValuePair> params = new ArrayList<>()
        param.entrySet().forEach({ entry ->
            params.add(new BasicNameValuePair(entry.getKey(), entry.getValue()))
        })
        httpPost.setEntity(new UrlEncodedFormEntity(params, "UTF-8"))
        CloseableHttpResponse response2 = httpclient.execute(httpPost)
        log.info("GongfubaoScript_prepare_params = {}", JSON.toJSONString(param))
        String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8")
        log.info("GongfubaoScript_Response_resStr = {}", retBack)
        JSONObject retJson = JSONObject.parseObject(retBack)
        if (null == retJson) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        if ("200" == (retJson.getString("code")) && retJson.getString("success")) {
            result.setErrorCode(FundConstant.RechargeErrorCode.NORMAL)
            String uri = retJson.getString("uri").replaceAll("\\\\", "")
            result.setRedirectUrl(uri)
            return
        } else {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("商户异常")
            return
        }
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("GongfubaoScript_Notify_resMap = {}", resMap)

        JSONObject json = JSON.parseObject(resMap.get("reqBody"), Feature.OrderedField)
        if (null == json) {
            return null
        }
        String signature = json.getString("signature")
        json.remove("signature")

        String toSign = json.toJSONString() + account.getPrivateKey()
        String sign = MD5.md5(toSign)

        String status = json.getString("status")
        //回调的时候订单状态是英文success
        if (StringUtils.isNotEmpty(status) && "success" == (status) && signature == (sign)) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("receive_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(json.getString("trade_no"))
            pay.setThirdOrderId(json.getString("transaction_id"))
            return pay
        }
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String
        String keyValue = account.getPrivateKey()// AccessToken
        //提交无签名校验，但需设置Authorization的值
        CloseableHttpClient httpclient = HttpClients.createDefault()
        String url = account.getPayUrl() + "/api/transaction/" + orderId
        HttpGet httpGet = new HttpGet(url)
        httpGet.addHeader("Content-Type", "application/json")
        httpGet.addHeader("Authorization", "Bearer " + keyValue)//Bearer后有空格
        CloseableHttpResponse response2 = httpclient.execute(httpGet)
        log.info("GongfubaoScript_query_param = {}", url)
        String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8")
        log.info("GongfubaoScript_query_resStr = {}", retBack)
        JSONObject retJson = JSONObject.parseObject(retBack)
        //主动查询的时候订单状态是中文“成功”
        if ("true" == (retJson.getString("success")) && "success" == (retJson.getString("status"))) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(retJson.getBigDecimal("receive_amount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setThirdOrderId(retJson.getString("transaction_no"))
            return pay
        }
        return null
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
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER
                || paymentId == FundConstant.PaymentType.ALI_TRANSFER
                || paymentId == FundConstant.PaymentType.UNION_TRANSFER) {
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
}
package com.seektop.fund.payment.gongfubao

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.alibaba.fastjson.parser.Feature
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.*
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
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

public class GongfubaoScript {

    private static final Logger log = LoggerFactory.getLogger(GongfubaoScript.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, Object> DataContentParms = new LinkedHashMap<>()
        DataContentParms.put("apply_to_amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        DataContentParms.put("bank", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()))
        DataContentParms.put("bank_branch", "上海市")
        DataContentParms.put("bank_name", req.getName())
        DataContentParms.put("bank_number", req.getCardNo())
        DataContentParms.put("notify_url", account.getNotifyUrl() + account.getMerchantId())
        DataContentParms.put("settlement_no", req.getOrderId())

        String toSign = JSONObject.toJSONString(DataContentParms) + account.getPrivateKey()
        DataContentParms.put("signature", MD5.md5(toSign))

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + account.getPublicKey())
        headParams.put("Content-Type", "application/x-www-form-urlencoded")

        log.info("GongfubaoScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
        GlRequestHeader requestHeader =
                this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + "/api/settlement", DataContentParms, requestHeader, headParams)
        log.info("GongfubaoScript_Transfer_resStr: {}", resStr)


        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(DataContentParms))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "true" != json.getString("success")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("message"))
            return result
        }
        result.setValid(true)
        result.setMessage("")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("GongfubaoPayer_withdraw_Notify_resMap:{}", JSON.toJSONString(resMap))
        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"))
        String orderId = json.getString("settlement_no")
        if (StringUtils.isEmpty(orderId)) {
            return null
        }
        return withdrawQuery(this.okHttpUtil, merchant, orderId, args[3])
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> headParams = new HashMap<String, String>()
        headParams.put("Authorization", "Bearer " + merchant.getPublicKey())

        log.info("GongfubaoScript_TransferQuery_order:{}", JSON.toJSONString(orderId))
        GlRequestHeader requestHeader =
                this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.get(merchant.getPayUrl() + "/api/settlement/" + orderId, new HashMap<>(), requestHeader, headParams)
        log.info("GongfubaoScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getInteger("code").intValue() != 200) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if (json.getBoolean("status")) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            notify.setThirdOrderId("")
            //订单状态判断标准：'apply', 'success', 'fail', 'not found'
            if (json.getString("settlement_status") == "success") {
                notify.setStatus(0)
            } else if (json.getString("settlement_status") == "fail") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        try {
            Map<String, String> param = new LinkedHashMap<>()
            String keyValue = account.getPrivateKey()// AccessToken
            param.put("trade_no", req.getOrderId())
            param.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
            param.put("notify_url", account.getNotifyUrl() + merchant.getId())
            param.put("ip", req.getIp())
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
            log.info("GongfubaoScript_prepare_params:{}", JSON.toJSONString(param))
            String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("GongfubaoScript_Response_resStr:{}", retBack)
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
        } catch (Exception e) {
            e.printStackTrace()
            result.setErrorCode(1)
            result.setErrorMsg("创建订单失败，稍后重试")
            return
        }
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("GongfubaoScript_Notify_resMap:{}", resMap)

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
        try {
            String keyValue = account.getPrivateKey()// AccessToken
            //提交无签名校验，但需设置Authorization的值
            CloseableHttpClient httpclient = HttpClients.createDefault()
            String url = account.getPayUrl() + "/api/transaction/" + orderId
            HttpGet httpGet = new HttpGet(url)
            httpGet.addHeader("Content-Type", "application/json")
            httpGet.addHeader("Authorization", "Bearer " + keyValue)//Bearer后有空格
            CloseableHttpResponse response2 = httpclient.execute(httpGet)
            log.info("GongfubaoScript_query_param:{}", url)
            String retBack = EntityUtils.toString(response2.getEntity(), "UTF-8")
            log.info("GongfubaoScript_query_resStr:{}", retBack)
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
        } catch (Exception e) {
            e.printStackTrace()
        }
        return null
    }


    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
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
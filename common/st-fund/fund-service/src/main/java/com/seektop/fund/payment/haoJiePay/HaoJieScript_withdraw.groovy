package com.seektop.fund.payment.haoJiePay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.BaseScript
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 豪杰支付
 * @author Otto
 * @date 2021-11-18
 */
public class HaoJieScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(HaoJieScript_withdraw.class)

    private static final String SERVER_WITHDRAW_URL = "/api/trans/create_order"
    private static final String SERVER_QUERY_URL = "/api/trans/query_order"
    private static final String SERVER_BALANCE_URL = "/api/query/query_balance"
    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        WithdrawResult result = new WithdrawResult()

        String appId = "";
        String param2 = "";
        //公鑰欄位： appId,代付验证码
        if(merchantAccount.getPublicKey().contains(",")){
            String[] urls =  merchantAccount.getPublicKey().split(",");
             appId = urls[0].replace(" ","");
             param2 = urls[1].replace(" ","");

        }else{
            result.setValid(false)
            result.setMessage("公钥栏位配置错误，请检查，应为： appId,代付验证码 ")
            return result
        }

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchantAccount.getMerchantCode())
        paramMap.put("appId", appId)  //appId : 商户后台查看
        paramMap.put("mchTransOrderNo", req.getOrderId())
        paramMap.put("currency", "cny")
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).multiply(BigDecimal.valueOf(100)).setScale(0, RoundingMode.DOWN).toString())
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        paramMap.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()))
        paramMap.put("bankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()))
        paramMap.put("accountType", "1")
        paramMap.put("accountName", req.getName())
        paramMap.put("accountNo", req.getCardNo())
        paramMap.put("province", "上海市")
        paramMap.put("param2", param2)  //代付验证码，与第三方拿
        paramMap.put("city", "上海市")

        // 因为 下单域名与查询域名不一致，所以 填入时将两个域名填入，以 , 隔开
        // ex:  http://pay.heropays.com,http://mer.heropays.com:3030
        String withdrawDomain = merchantAccount.getPayUrl();
        if(merchantAccount.getPayUrl().contains(",")){
            String[] urls =  merchantAccount.getPayUrl().split(",");
            withdrawDomain = urls[0]
        }

        result.setOrderId(req.getOrderId())

        if(StringUtils.isBlank(withdrawDomain) ){
            result.setValid(false)
            result.setMessage("请求url 配置格式有误，请洽技术")
            return result
        }

        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        Map<String, String> params = new HashMap<>()
        params.put("params", JSON.toJSONString(paramMap))

        log.info("HaoJieScript_Transfer_Params: {} , url:{}", params , withdrawDomain)
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())

        String resStr = okHttpUtil.post( withdrawDomain + SERVER_WITHDRAW_URL , params,  requestHeader)
        log.info("HaoJieScript_Transfer_resStr: {} , orderId:{}", resStr , req.getOrderId() )

        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("retCode") || StringUtils.isEmpty(json.getString("transOrderId"))) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("retMsg"))
            return result
        }
        result.setValid(true)
        result.setMessage(json.getString("retMsg"))
        result.setThirdOrderId(json.getString("transOrderId"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HaoJieScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))

        Map<String, String> signMap  = new LinkedHashMap<>() ;
        signMap.put("amount", resMap.get("amount"))
        signMap.put("mchId", resMap.get("mchId"))
        signMap.put("transSuccTime", resMap.get("transSuccTime"))
        signMap.put("channelOrderNo", resMap.get("channelOrderNo"))
        signMap.put("backType", resMap.get("backType"))
        signMap.put("mchTransOrderNo", resMap.get("mchTransOrderNo"))
        signMap.put("param1", resMap.get("param1"))
        signMap.put("transOrderId", resMap.get("transOrderId"))
        signMap.put("status", resMap.get("status"))

        String thirdSign = resMap.get("sign")
        String toSign = MD5.toAscii(signMap) + "&key=" + merchant.getPrivateKey();
        toSign = MD5.md5(toSign).toUpperCase();

        if (StringUtils.isNotEmpty(resMap.get("mchTransOrderNo")) && toSign == thirdSign) {
            return this.withdrawQuery(okHttpUtil, merchant, resMap.get("mchTransOrderNo"))
        }

        log.info("HaoJieScript_notify_Sign: 回调错误或验签失败 orderId: {} " , resMap.get("mchTransOrderNo"))
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        String appId = "";
        //公鑰欄位： appId,代付验证码
        if(merchant.getPublicKey().contains(",")){
            String[] urls =  merchant.getPublicKey().split(",");
            appId = urls[0].replace(" ","");
        }

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchant.getMerchantCode())
        paramMap.put("appId", appId)
        paramMap.put("mchTransOrderNo", orderId)

        String signInfo = MD5.toAscii(paramMap) + "&key=" + merchant.getPrivateKey()
        paramMap.put("sign", MD5.md5(signInfo).toUpperCase())

        Map<String, String> params = new HashMap<>()
        params.put("params", JSON.toJSONString(paramMap))

        // 下单域名与查询域名不同，填入时需将两个域名填入，以 , 隔开
        String queryDomain = merchant.getPayUrl();
        if(merchant.getPayUrl().contains(",")){
            String[] urls =  merchant.getPayUrl().split(",");
            queryDomain = urls[1]
        }

        log.info("HaoJieScript_Transfer_Query_Params: {} ,url: ", JSON.toJSONString(paramMap),queryDomain )
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())

        String resStr = okHttpUtil.post(queryDomain+ SERVER_QUERY_URL, params,  requestHeader)
        log.info("HaoJieScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("status") == null || json.getString("retCode") != "SUCCESS") {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")

        //状态:0-订单生成,1-转账中,2-转账成功,3-转账失败 5-转帐中。
        Integer status = json.getInteger("status")
        if (status == 2) {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (status == 3) {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)

        }
        notify.setThirdOrderId(json.getString("transOrderId"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchantAccount.getMerchantCode())
        paramMap.put("queryTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        String toSign = MD5.toAscii(paramMap) + "&key=" + merchantAccount.getPrivateKey()
        paramMap.put("sign", MD5.md5(toSign).toUpperCase())

        Map<String, String> params = new HashMap<>()
        params.put("params", JSON.toJSONString(paramMap))

        String queryDomain = merchantAccount.getPayUrl();
        if(merchantAccount.getPayUrl().contains(",")){
            String[] urls =  merchantAccount.getPayUrl().split(",");
            queryDomain = urls[1]
        }

        log.info("HaoJieScript_Query_Balance_Params: {} ", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, null, GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(queryDomain + SERVER_BALANCE_URL ,params, requestHeader)
        log.info("HaoJieScript_Query_Balance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("balance")
        return balance == null ? BigDecimal.ZERO : balance.divide(BigDecimal.valueOf(100))
    }


    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channleName) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(channelId + "")
                .channelName(channleName)
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build()
        return requestHeader
    }

}
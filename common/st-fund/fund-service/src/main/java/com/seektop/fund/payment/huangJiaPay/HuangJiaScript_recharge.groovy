package com.seektop.fund.payment.huangJiaPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
/**
 * @desc 皇家支付
 * @date 2021-11-26
 * @auth otto
 */
public class HuangJiaScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(HuangJiaScript_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/api/pay/order"
    private  final String QUERY_URL =  "/api/sett/query"

    public RechargeNotify result(Object[] args) throws GlobalException {
        return null
    }

    public void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String gateway = ""

        if (FundConstant.PaymentType.BANKCARD_TRANSFER == merchant.getPaymentId()) {
            gateway = "8009" //卡转卡

        }

        if (StringUtils.isEmpty(gateway)) {
            result.setErrorMsg("支付方式不支持")
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            return
        }
        prepareToScan(merchant, account, req, result, gateway)
    }


    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String gateway) throws GlobalException {

        Map<String, Object> params = new LinkedHashMap<>()
        params.put("mchId", account.getMerchantCode())
        params.put("productId", gateway )
        params.put("mchOrderNo",req.getOrderId())
        params.put("amount", req.getAmount().multiply(100).setScale(0, RoundingMode.DOWN).toString())
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId())
        params.put("returnUrl", account.getNotifyUrl() )
        params.put("subject", "subject")
        params.put("body", "body")
        params.put("clientIp", req.getIp())

        String secretKey = MD5.md5(account.getPrivateKey()).toUpperCase()
        String toSign = MD5.toAscii(params) +"&secretKey=" +secretKey

        params.put("sign",  MD5.md5(toSign).toUpperCase())

        log.info("HuangJiaScript_recharge_prepare_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())
//        OkHttpUtil okHttpUtil = new OkHttpUtil();
        String resStr = okHttpUtil.post(account.getPayUrl() + PAY_URL, params, requestHeader)
        log.info("HuangJiaScript_recharge_prepare_resp:{}  , orderId:{}", resStr ,req.getOrderId())

        if (StringUtils.isEmpty(resStr)) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }

        JSONObject json = JSONObject.parseObject(resStr)
        if (json == null ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("api接口异常，稍后重试")
            return
        }

        if ("SUCCESS" != json.getString("retCode")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("retMsg"))
            return
        }

        if ( StringUtils.isEmpty(json.getString("payUrl"))) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg("支付商家未出码")
            return
        }
        result.setThirdOrderId(json.getString("payOrderId"))
        result.setRedirectUrl(json.getString("payUrl"))
    }

    public RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount

        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HuangJiaScript_notify_resp:{}", JSON.toJSON(resMap))
        Map<String, String> signMap  = new LinkedHashMap<>();

        signMap.put("income",resMap.get("income"))
        signMap.put("payOrderId",resMap.get("payOrderId"))
        signMap.put("amount",resMap.get("amount"))
        signMap.put("mchId",resMap.get("mchId"))
        signMap.put("productId",resMap.get("productId"))
        signMap.put("mchOrderNo",resMap.get("mchOrderNo"))
        signMap.put("paySuccTime",resMap.get("paySuccTime"))
        signMap.put("channelOrderNo",resMap.get("channelOrderNo"))
        signMap.put("backType",resMap.get("backType"))
        signMap.put("param1",resMap.get("param1"))
        signMap.put("param2",resMap.get("param2"))
        signMap.put("status",resMap.get("status"))

        String thirdSign = resMap.get("sign")

        String secretKey = MD5.md5(account.getPrivateKey()).toUpperCase()
        String toSign = MD5.toAscii(signMap) +"&secretKey=" +secretKey
        toSign = MD5.md5(toSign).toUpperCase();
        println(toSign)
        String orderId = resMap.get("mchOrderNo") ;

        if (StringUtils.isNotEmpty(orderId) && toSign == thirdSign) {
            return this.payQuery(okHttpUtil, account, orderId ) ;
        }
        log.info("HuangJiaScript_notify_Sign: 回调资料错误或验签失败，orderId ：{}" , orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("mchId", account.getMerchantCode())
        params.put("mchOrderNo",orderId)

        String secretKey = MD5.md5(account.getPrivateKey()).toUpperCase()
        String sign = MD5.toAscii(params) +"&secretKey=" +secretKey

        params.put("sign", MD5.md5(sign).toUpperCase())

        log.info("HuangJiaScript_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, requestHeader)

        log.info("HuangJiaScript_query_resp:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null ) {
            return null
        }

        // 0=订单生成,1=支付中,2=支付成功,3=业务处理完成
        // 2 3都是钱已经进帐  2是没通知过去
        if ( "3" == (json.getString("status"))  ||  "2" == (json.getString("status")) ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("amount").divide(100).setScale(2, RoundingMode.DOWN)) //分 -> 元
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("success")
            return pay
        }
        return null
    }

    void cancel(Object[] args) throws GlobalException {

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

    private static GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code, Integer channelId, String channelName) {
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


    static void main(String[] args) {

        HuangJiaScript_recharge wr = new HuangJiaScript_recharge();
        GlPaymentMerchantApp merchant = new GlPaymentMerchantApp();
        GlPaymentMerchantaccount account = new GlPaymentMerchantaccount();
        RechargePrepareDO req = new RechargePrepareDO();
        GlRechargeResult result = new GlRechargeResult();

        account.setMerchantCode("17"); //商户号

        req.setUsername("testUserName");       //会员帐号
        req.setUserId(10248888);    //会员id
        req.setIp("210.213.80.244");
        req.setOrderId("czTEST0019");//订单号
        req.setAmount(488);//金额
        req.setFromCardUserName("道明寺")
        account.setPayUrl("http://43.132.248.145:8885");
        account.setNotifyUrl("http://www.aalgds.com/api/forehead/fund/recharge/notify/");//订单号
        account.setPrivateKey("wljmTLPJD3HwY8yB8wkm8YxDsJJ7zxrqD23gaZHyxQRIudmgBosGJ4TcmdWDEASo")
//        account.setPublicKey("pCTtSnSGlJHqWM9o")
        merchant.setId(301)

        String gateway = "8009" ;  //何种通道
//        wr.prepareToScan( merchant,  account,  req,  result,  gateway)

        Object[] xxx = new Object[10];
        OkHttpUtil ok = new OkHttpUtil();
        xxx[2] = account ;

        String notifyStr ="{\"income\":\"49100\",\"payOrderId\":\"DX021112717302034049\",\"amount\":\"50000\",\"mchId\":\"17\",\"productId\":\"8009\",\"mchOrderNo\":\"CZ20211127173001A9TR\",\"paySuccTime\":\"1638007384000\",\"sign\":\"EB5B4F3E92E1DC1C30C03CCC80D02204\",\"channelOrderNo\":\"\",\"backType\":\"2\",\"param1\":\"\",\"param2\":\"\",\"reqBody\":\"\",\"status\":\"2\"}";

        xxx[3] =  (Map<String, String>) JSON.parseObject(notifyStr);
//        xxx[3] = new HashMap() ;
        wr.notify(xxx)
        xxx[0] = ok ;
        xxx[1] = account ;
        xxx[2] = req.getOrderId() ;
        xxx[3] = "nM3YOqCSlef4DE4M" ;
        xxx[4] = req.getAmount() ;
//        wr.payQuery(xxx)

        if(!account.getNotifyUrl().contains("recharge")){
            println("充值回调网址错误")
        }
        if( wr.showType()== 0 && wr.innerpay() == false ){
            println("为跳转页面")

        }else if( wr.showType()== 1 && wr.innerpay() == true ){
            println("为内部通道")

        }else{
            println("showType 与 innerpay不一致")
        }

    }


}
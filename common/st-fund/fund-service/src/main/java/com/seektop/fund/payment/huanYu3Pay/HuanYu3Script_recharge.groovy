package com.seektop.fund.payment.huanYu3Pay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.constant.FundConstant
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlPaymentMerchantApp
import com.seektop.fund.model.GlPaymentMerchantaccount
import com.seektop.fund.payment.BankInfo
import com.seektop.fund.payment.GlRechargeResult
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.RechargePrepareDO
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode
/**
 * @desc 环宇V3支付
 * @date 2021-11-13
 * @auth otto
 */
public class HuanYu3Script_recharge {

    private static final Logger log = LoggerFactory.getLogger(HuanYu3Script_recharge.class)
    private OkHttpUtil okHttpUtil

    private  final String PAY_URL =  "/pay/order"
    private  final String QUERY_URL =  "/pay/order/query"

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
            gateway = "wyyhk" //卡转卡
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
        params.put("merchNo", account.getMerchantCode())
        params.put("orderNo",req.getOrderId())
        params.put("amount", req.getAmount().setScale(2, RoundingMode.DOWN).toString())
        params.put("currency", "CNY")
        params.put("outChannel", gateway )
        params.put("title", "recharge" )
        params.put("product", "recharge" )
        params.put("outChannel", gateway )
        params.put("returnUrl", account.getNotifyUrl()  )
        params.put("notifyUrl", account.getNotifyUrl() + merchant.getId() )
        params.put("reqTime", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS) )
        params.put("userId", req.getUserId()+"" )
        params.put("realname", req.getFromCardUserName() )
        
        String context = JSON.toJSONString(params);
        String toSign = context + account.getPrivateKey() ;

        Map<String, Object> reqParams = new LinkedHashMap<>()
        reqParams.put("context" , context.getBytes("UTF-8") )
        reqParams.put("sign", MD5.md5(toSign))
        reqParams.put("encryptType","MD5")
        log.info("HuanYu3Script_recharge_prepare_params:{} , url :{}", params, account.getPayUrl() )

        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), account.getChannelId(), account.getChannelName())

        String resStr = okHttpUtil.postJSON(account.getPayUrl() + PAY_URL, JSON.toJSONString(reqParams), requestHeader)

        log.info("HuanYu3Script_recharge_prepare_resp:{} , orderId:{} ", resStr  , req.getOrderId())
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

        //0：请求成功
        if (0 != json.getInteger("code")  ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("msg"))
            return
        }

        String contextStr =  new String(json.getBytes("context"),"UTF-8")
        JSONObject contextJson = JSONObject.parseObject(contextStr)

        BankInfo bankInfo = new BankInfo();
        bankInfo.setName(contextJson.getString("wyAccName"))
        bankInfo.setBankId(-1)
        bankInfo.setBankName(contextJson.getString("wyBankName"))
        bankInfo.setBankBranchName(contextJson.getString("wyBankBranch"))
        bankInfo.setCardNo(contextJson.getString("wyAccCard"))
        result.setBankInfo(bankInfo)

    }

    public RechargeNotify notify(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("HuanYu3Script_notify_resp:{}", resMap)

        JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
        Map<String, Object> map = (Map<String, Object>) json
        String sign = map.get("sign");

        String contextStr =  new String(json.getBytes("context"),"UTF-8")
        JSONObject contextJson = JSONObject.parseObject(contextStr)

        String md5Sign = contextStr + account.getPrivateKey();
        md5Sign = MD5.md5(md5Sign);
        String orderId = contextJson.getString("orderNo")

        if (StringUtils.isNotEmpty(orderId) && sign == md5Sign) {
            return this.payQuery(okHttpUtil, account, orderId)

        }
        log.info("HuanYu3Script_notify_Sign: 回调资料错误或验签失败，orderId：{}" , orderId )

        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new LinkedHashMap<>()
        params.put("merchNo", account.getMerchantCode())
        params.put("orderNo",orderId)

        String context = JSON.toJSONString(params);
        Map<String, String> reqParams = new LinkedHashMap<>()
        reqParams.put("context",context.getBytes("UTF-8"))
        reqParams.put("sign", MD5.md5(context +account.getPrivateKey() ))
        reqParams.put("encryptType", "MD5")

        log.info("HuanYu3Script_query_params:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.postJSON(account.getPayUrl() + QUERY_URL, JSON.toJSONString(reqParams), requestHeader)

        log.info("HuanYu3Script_query_resp:{}", resStr)
        if (StringUtils.isEmpty(resStr)) {
            return null
        }


        JSONObject json = JSON.parseObject(resStr)
        if (json == null  || 0 != json.getInteger("code") ) {
            return null
        }

        String contextStr =  new String(json.getBytes("context"),"UTF-8")
        log.info("HuanYu3Script_query_resp_context:{}" , contextStr)
        JSONObject contextJson = JSONObject.parseObject(contextStr)

        // 0 待支付-下单成功 1支付成功 2支付失败 3处理中 4撤回
        if ( "1" == contextJson.getString("orderState") ) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(contextJson.getBigDecimal("realAmount"))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("ok")
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
        return true
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
        return FundConstant.ShowType.DETAIL
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


}
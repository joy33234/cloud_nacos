package com.seektop.fund.payment.xinV2Pay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.DateUtils
import com.seektop.common.utils.MD5
import com.seektop.common.utils.RSASignature
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
import org.springframework.util.ObjectUtils

import java.math.RoundingMode
/**
 * @desc 星支付
 * @auth Redan
 * @date 2022-02-21
 */
class XinPayScript_recharge {

    private static final Logger log = LoggerFactory.getLogger(XinPayScript_recharge.class)

    private OkHttpUtil okHttpUtil
    private  final String PAY_URL =  "/InterfaceV5/CreatePayOrder/"
    private  final String QUERY_URL =  "/InterfaceV5/QueryPayOrder/"

    RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }

    void pay(Object[] args) {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantApp merchant = args[1] as GlPaymentMerchantApp
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        RechargePrepareDO req = args[3] as RechargePrepareDO
        GlRechargeResult result = args[4] as GlRechargeResult
        String payType = ""
        if (merchant.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            payType = "kzk" //卡转卡
        }
        if (StringUtils.isNotEmpty(payType)) {
            prepareScan(merchant, payment, req, result, payType)
        }
    }

    private void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result, String payType) {
        Map<String, String> params = new HashMap<String, String>()
        params.put("Amount", req.getAmount().setScale(2, RoundingMode.DOWN) + "")
        params.put("Ip", req.getIp())
        params.put("MerchantId", payment.getMerchantCode())
        params.put("MerchantUniqueOrderId", req.getOrderId())
        params.put("NotifyUrl", payment.getNotifyUrl() + merchant.getId())
        params.put("PayTypeId", payType)
        params.put("Remark", System.currentTimeMillis()+"")
        params.put("ReturnUrl", "")
        params = signed(params,payment.getPrivateKey());

        log.info("XinV2PayScript_Prepare_Params:{} url: {}", JSON.toJSONString(params) , payment.getPayUrl())
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.RECHARGE.getCode(), payment.getChannelId(), payment.getChannelName())
        String restr = okHttpUtil.post(payment.getPayUrl() + PAY_URL, params,  requestHeader)
        log.info("XinV2PayScript_Prepare_resStr:{} , orderId :{}" , restr, req.getOrderId())

        JSONObject json = JSON.parseObject(restr);
        if (json == null) {
            result.setErrorCode(FundConstant.RechargeErrorCode.SYSTEM)
            result.setErrorMsg("网络请求超时，稍后重试")
            return
        }
        if (json.getInteger("Code") != 0 ) {
            result.setErrorCode(FundConstant.RechargeErrorCode.PAYMENT)
            result.setErrorMsg(json.getString("MessageForUser"))
            return
        }

        result.setRedirectUrl(json.getString("Url") )

    }

    RechargeNotify notify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount payment = args[2] as GlPaymentMerchantaccount
        Map<String, String> resMap = args[3] as Map<String, String>
        log.info("XinV2PayScript_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("MerchantUniqueOrderId")
        return payQuery(okHttpUtil, payment, orderid)
    }

    RechargeNotify payQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlPaymentMerchantaccount account = args[1] as GlPaymentMerchantaccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("MerchantId", account.getMerchantCode())
        params.put("MerchantUniqueOrderId", orderId)
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params = signed(params,account.getPrivateKey());

        log.info("XinV2PayScript_Query_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.RECHARGE_QUERY.getCode(), account.getChannelId(), account.getChannelName())
        String resStr = okHttpUtil.post(account.getPayUrl() + QUERY_URL, params, 30L, requestHeader)
        log.info("XinV2PayScript_Query_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)

        /*
        0 待支付
        100 支付成功
        -90 支付失败
        -10 订单号不存在
        -10 状态特别注意：请务必在订单创建成功，等待至少10秒后，再进行查询操作，否则可能会因为网络波动，导致查询请求先行到达我方，我方返回订单号不存在的结果，误导您后续的程序逻辑
        * */
        if ( 100 == json.getInteger("PayOrderStatus")) {
            RechargeNotify pay = new RechargeNotify()
            pay.setAmount(json.getBigDecimal("RealAmount").setScale(2, RoundingMode.DOWN))
            pay.setFee(BigDecimal.ZERO)
            pay.setOrderId(orderId)
            pay.setRsp("SUCCESS")
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

    private HashMap<String, String> signed(HashMap<String, String> params,String key){

        /*
        1、步骤1：按照Key值字典序排序，拼接后得到：
        A=aaa&B=bbb&C=ccc
        * */
        StringBuffer sb = new StringBuffer();
        // 对所有传入参数按照字段名的 ASCII 码从小到大排序（字典序）
        for (String k : params.keySet().sort().toList()) {
            if(k==null){
                return
            }
            if(org.springframework.util.StringUtils.isEmpty(k)){
                return
            }
            Object v = params.get(k);
            if (null != v && !ObjectUtils.isEmpty(v)) {
               sb.append(k + "=" + v + "&");
            }else{
               sb.append(k + "=&");
            }
        }
        sb.delete(sb.length()-1,sb.length())
        /*
        2、在末尾拼接Md5Key（不带【key】【=】【&】等符号）（假设Md5Key为kkkkkkkkkk），得到：
        A=aaa&B=bbb&C=ccckkkkkkkkkk
        * */
        sb.append(key);
        /*
        3、最终将整个字符串进行Md5-32位编码，并转换为小写，伪代码：
        Sign = MD5_Length32_LowerCase(“A=aaa&B=bbb&C=ccckkkkkkkkkk”);

        4、得到Sign值
        10c3e9cca92a21e8358d5280e73cb520

        5、最终发送的数据
        A=aaa&B=bbb&C=ccc&Sign=10c3e9cca92a21e8358d5280e73cb520
        * */
        params.put("Sign",MD5.md5(sb.toString()));
        return params;
    }

}

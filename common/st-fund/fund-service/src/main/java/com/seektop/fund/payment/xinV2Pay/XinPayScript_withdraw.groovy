package com.seektop.fund.payment.xinV2Pay

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
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import org.springframework.util.ObjectUtils

import java.math.RoundingMode

/**
 * @desc 星支付
 * @auth Redan
 * @date 2022-02-21
 */
public class XinPayScript_withdraw {

    private static final Logger log = LoggerFactory.getLogger(XinPayScript_withdraw.class)

    private OkHttpUtil okHttpUtil
    private static final String SERVER_WITHDRAW_URL = "/InterfaceV5/CreateWithdrawOrder/"
    private static final String SERVER_QUERY_URL = "/InterfaceV6/QueryWithdrawOrder/"
    private static final String SERVER_BALANCE_URL = "/InterfaceV6/GetBalanceAmount/"

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness

    WithdrawResult withdraw(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
        params.put("BankCardBankName", req.getBankName())
        params.put("BankCardNumber", req.getCardNo())
        params.put("BankCardRealName", req.getName())
        params.put("MerchantId", merchantAccount.getMerchantCode())
        params.put("MerchantUniqueOrderId", req.getOrderId())
        params.put("NotifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
        params.put("WithdrawTypeId", "0")
        params = signed(params,merchantAccount.getPrivateKey());

        log.info("XinV2PayScript_Transfer_params: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, params,  requestHeader)
        log.info("XinV2PayScript_Transfer_resStr: {}", resStr)

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(params))
        result.setResData(resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || 0 != json.getInteger("Code")) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("Message"))
            return result
        }
        result.setValid(true)
        result.setMessage("ojbk")
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        log.info("XinV2PayScript_WithdrawNotify_resMap:{}", JSON.toJSONString(resMap))
        return withdrawQuery(okHttpUtil, merchant, resMap.get("MerchantUniqueOrderId") )
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2] as String

        Map<String, String> params = new HashMap<String, String>()
        params.put("MerchantId", merchant.getMerchantCode())
        params.put("MerchantUniqueOrderId", orderId)
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        params = signed(params,merchant.getPrivateKey());

        log.info("XinV2PayScript_TransferQuery_reqMap:{}", JSON.toJSONString(params))
        GlRequestHeader requestHeader = getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, params, 30L, requestHeader)
        log.info("XinV2PayScript_TransferQuery_resStr:{}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if ( json.getInteger("Code") != 0) {
            return null
        }
        WithdrawNotify notify = new WithdrawNotify()
        if (json.getInteger("WithdrawOrderStatus") != null) {
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)

            /*
            0 处理中
            100 已完成 (已成功)
            -90 已撤销（已失败）
            -10 订单号不存在
                务必注意：请在订单成功创建至少10-60秒后再首次查询订单，过早的查询可能由于网络阻塞，导致查询请求先于创建请求到达我方，从而得到订单不存在的查询结果误导您的后续逻辑，所以确认订单未到达我方的判断应该是：
                WithdrawOrderStatus=="-10" && (订单创建请求上次提交时间 - 当前时间) . 总秒数 > 10或60秒
            * */
            if (json.getInteger("WithdrawOrderStatus") == 100) {
                notify.setStatus(0)
                notify.setRsp("OK")

            } else if (json.getInteger("WithdrawOrderStatus") == -90) {
                notify.setStatus(1)
                notify.setRsp("OK")

            } else {
                notify.setStatus(2)

            }
        }
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness
        Map<String, String> params = new HashMap<>()
        params.put("MerchantId", merchantAccount.getMerchantCode())
        params.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

        params = signed(params,merchantAccount.getPrivateKey());

        log.info("XinV2PayScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, 30L, requestHeader)
        log.info("XinV2PayScript_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("BalanceAmount") == "-1") {
            return BigDecimal.ZERO
        }
        BigDecimal balance = json.getBigDecimal("BalanceAmount").setScale(2, RoundingMode.DOWN)
        return balance == null ? BigDecimal.ZERO : balance
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

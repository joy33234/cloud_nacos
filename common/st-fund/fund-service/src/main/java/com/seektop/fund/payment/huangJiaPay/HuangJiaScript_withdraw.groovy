package com.seektop.fund.payment.huangJiaPay

import com.alibaba.fastjson.JSON
import com.alibaba.fastjson.JSONObject
import com.seektop.common.http.GlRequestHeader
import com.seektop.common.http.OkHttpUtil
import com.seektop.common.utils.MD5
import com.seektop.enumerate.GlActionEnum
import com.seektop.exception.GlobalException
import com.seektop.fund.business.GlPaymentChannelBankBusiness
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.model.GlWithdrawMerchantAccount
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult
import com.seektop.fund.payment.groovy.ResourceEnum
import org.apache.commons.lang3.StringUtils
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.math.RoundingMode

/**
 * 皇家支付
 * @author Otto
 * @date 2021-11-27
 */
public class HuangJiaScript_withdraw {


    private static final Logger log = LoggerFactory.getLogger(HuangJiaScript_withdraw.class)

    private OkHttpUtil okHttpUtil

    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness
    private static final String SERVER_WITHDRAW_URL = "/api/sett/apply"
    private static final String SERVER_QUERY_URL = "/api/sett/query"
    private static final String SERVER_BALANCE_URL = "/api/balance/query"

    WithdrawResult withdraw(Object[] args) throws GlobalException {

        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        GlWithdraw req = args[2] as GlWithdraw
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchantAccount.getMerchantCode())
        paramMap.put("amount", req.getAmount().subtract(req.getFee()).multiply(100).setScale(0, RoundingMode.DOWN).toString()) //元 -> 分
        paramMap.put("mchOrderNo", req.getOrderId())
        paramMap.put("accountName", req.getName())
        paramMap.put("accountNo", req.getCardNo())
        paramMap.put("bankName", req.getBankName())
        paramMap.put("remark", "remark")
        paramMap.put("notifyUrl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId())

        String secretKey = MD5.md5(merchantAccount.getPrivateKey()).toUpperCase()
        String toSign = MD5.toAscii(paramMap) +"&secretKey=" +secretKey
        paramMap.put("sign",  MD5.md5(toSign).toUpperCase())

        log.info("HuangJiaScript_Transfer_Params: {} , url:{} ", JSON.toJSONString(paramMap), merchantAccount.getPayUrl())
        GlRequestHeader requestHeader =
                getRequestHeard(req.getUserId().toString(), req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(),merchantAccount.getChannelId(),merchantAccount.getChannelName())

//        OkHttpUtil okHttpUtil = new OkHttpUtil(); //fixme
        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_WITHDRAW_URL, paramMap,  requestHeader)

        log.info("HuangJiaScript_Transfer_resStr: {} , orderId:{}", resStr ,req.getOrderId() )

        WithdrawResult result = new WithdrawResult()
        result.setOrderId(req.getOrderId())
        result.setReqData(JSON.toJSONString(paramMap))
        result.setResData(resStr)

        if (StringUtils.isEmpty(resStr)) {
            result.setValid(false)
            result.setMessage("API异常:请联系出款商户确认订单.")
            return result
        }

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || "SUCCESS" != json.getString("retCode") ) {
            result.setValid(false)
            result.setMessage(json == null ? "API异常:请联系出款商户确认订单." : json.getString("retMsg"))
            return result
        }

        result.setValid(true)
        result.setMessage(json.getString("retMsg"))
        result.setThirdOrderId(json.getString("settOrderId"))
        return result
    }

    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        Map<String, String> resMap = args[2] as Map<String, String>
        log.info("HuangJiaScript_Transfer_Notify_resMap:{}", JSON.toJSONString(resMap))
        String orderid = resMap.get("mchOrderNo")
        String thirdOrderId = resMap.get("payOrderId")
        if (StringUtils.isNotEmpty(orderid) && StringUtils.isNotEmpty(thirdOrderId)) {
            return withdrawQuery(okHttpUtil, merchant, orderid, thirdOrderId, args[3])
        }
        return null
    }

    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
        String orderId = args[2]

        Map<String, String> paramMap = new HashMap<>()
        paramMap.put("mchId", merchant.getMerchantCode())
        paramMap.put("mchOrderNo", orderId)

        String secretKey = MD5.md5(merchant.getPrivateKey()).toUpperCase()
        String toSign = MD5.toAscii(paramMap) +"&secretKey=" +secretKey
        paramMap.put("sign",  MD5.md5(toSign).toUpperCase())

        log.info("HuangJiaScript_Transfer_Query_Params: {}", JSON.toJSONString(paramMap))
        GlRequestHeader requestHeader =
                getRequestHeard(null, null, orderId, GlActionEnum.WITHDRAW_QUERY.getCode(),merchant.getChannelId(),merchant.getChannelName())
        String resStr = okHttpUtil.post(merchant.getPayUrl() + SERVER_QUERY_URL, paramMap, 30L, requestHeader)
        log.info("HuangJiaScript_Transfer_Query_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return null
        }

        WithdrawNotify notify = new WithdrawNotify()
        notify.setMerchantCode(merchant.getMerchantCode())
        notify.setMerchantId(merchant.getMerchantId())
        notify.setMerchantName(merchant.getChannelName())
        notify.setOrderId(orderId)
        notify.setRemark("")

        //状态:1-等待处理,2-已审核,3-审核不通过,
        // 4-打款中,5-打款成功,6-打款失败
        Integer status = json.getInteger("status")
        if (status == 5) {
            notify.setStatus(0)
            notify.setRsp("success")

        } else if (status == 6  || status == 3) {
            notify.setStatus(1)
            notify.setRsp("success")

        } else {
            notify.setStatus(2)

        }
        notify.setThirdOrderId(json.getString("settOrderId"))
        return notify
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        this.okHttpUtil = args[0] as OkHttpUtil
        GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount
        this.glPaymentChannelBankBusiness = com.seektop.fund.payment.groovy.BaseScript.getResource(args[2], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

        Map<String, String> params = new HashMap<>()
        params.put("mchId", merchantAccount.getMerchantCode())
        String secretKey = MD5.md5(merchantAccount.getPrivateKey()).toUpperCase()
        String toSign = MD5.toAscii(params) +"&secretKey=" +secretKey
        params.put("sign",  MD5.md5(toSign).toUpperCase())

        log.info("HuanYu3Script_QueryBalance_reqParams: {}", JSON.toJSONString(params))
        GlRequestHeader requestHeader =
                getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

        String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + SERVER_BALANCE_URL, params, requestHeader)
        log.info("HuanYu3Script_QueryBalance_resStr: {}", resStr)

        JSONObject json = JSON.parseObject(resStr)
        //retCode:SUCCESS
        if (json == null || json.getString("retCode") != "SUCCESS") {
            return BigDecimal.ZERO
        }

        BigDecimal balance = json.getBigDecimal("balance").divide(new BigDecimal(100)) //分 -> 元
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


    static void main(String[] args) {

        HuangJiaScript_withdraw wr = new HuangJiaScript_withdraw();
        GlWithdrawMerchantAccount account = new GlWithdrawMerchantAccount();
        GlWithdraw req = new GlWithdraw();

        account.setMerchantCode("17"); //商户号

        req.setUsername("testUserName");       //会员帐号
        req.setUserId(10248888);    //会员id

        req.setOrderId("txTEST00002");//订单号
        req.setAmount(100.00);//金额
        req.setFee(0);
        req.setName("吴一凡");
        req.setCardNo("4720684563650000")
        req.setIp("210.213.80.244")
        req.setBankName("浙江银行")

        account.setPayUrl("http://43.132.248.145:8885");
        account.setNotifyUrl("http://www.aalgds.com/api/forehead/fund/withdraw/notify/"); //订单号
        account.setPublicKey("8132f879f4e04e95b2a0af853d729a07")  //密钥
        account.setPrivateKey("wljmTLPJD3HwY8yB8wkm8YxDsJJ7zxrqD23gaZHyxQRIudmgBosGJ4TcmdWDEASo");

        account.setMerchantId(301)
        req.setMerchantId(301)
        req.setBankId(1)

        Object[] xxx = new Object[10];

        OkHttpUtil ok = new OkHttpUtil();
        xxx[0] = ok;
        xxx[1] = account;
        xxx[2] = req;

//        wr.withdraw(xxx) //下单接口

        String notifyStr = "{\"order_no\":\"195907b16a40be430719edd45689b2f6\",\"update_time\":\"2021-11-27 14:25:54\",\"out_trade_no\":\"txTEST00001\",\"user_name\":\"吴一凡\",\"total_fee\":\"3010.00\",\"reqBody\":\"\",\"sign\":\"IArAAZVZwlCOGS5cK/qDLqM4VyKOD1TegjWqST/ClHA=\",\"fee_type\":\"CNY\",\"mch_id\":\"1091371\",\"status\":\"2\",\"bank_account\":\"4720684563650000\"}"
        xxx[2] = (Map<String, String>) JSON.parseObject(notifyStr);

//        wr.withdrawNotify(xxx) //回调接口
        xxx[2] = req.getOrderId()
        xxx[3] = "1000329"; //三方订单号
//        wr.withdrawQuery(xxx) //查单接口
        wr.balanceQuery(xxx) //余额查询


        if (!account.getNotifyUrl().contains("withdraw")) {
            println("提款回调网址错误")
        }


    }


}
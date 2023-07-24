    package com.seektop.fund.payment.hengtaiPay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
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
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory

    import java.math.RoundingMode

    /**
     * 恒泰支付
     * @auth joy
     * @date 2021-07-21
     */

    public class HengTaiScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(HengTaiScript_withdraw.class)

        private OkHttpUtil okHttpUtil


        private RedisService redisService

        private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness


        WithdrawResult withdraw(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
            GlWithdraw req = args[2] as GlWithdraw
            this.redisService = BaseScript.getResource(args[3], ResourceEnum.RedisService) as RedisService
            this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness

            WithdrawResult result = new WithdrawResult()

            Map<String, Object> DataContentParms = new TreeMap<>()
            DataContentParms.put("merchant", account.getMerchantCode())
            DataContentParms.put("requestReference", req.getOrderId())
            DataContentParms.put("merchantBank", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()))
            DataContentParms.put("merchantBankCardRealname", req.getName())
            DataContentParms.put("merchantBankCardAccount", req.getCardNo())

            DataContentParms.put("merchantBankCardProvince", "上海市")
            DataContentParms.put("merchantBankCardCity", "上海市")
            DataContentParms.put("merchantBankCardBranch", "上海市")
            DataContentParms.put("amount", req.getAmount().subtract(req.getFee()).setScale(0, RoundingMode.DOWN).toString())
            DataContentParms.put("callback", account.getNotifyUrl() + account.getMerchantId())
            String toSign = MD5.toSign(DataContentParms) + "&key=" + account.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

            log.info("HengTaiScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/api/merchant/withdraw", DataContentParms, requestHeader)
            log.info("HengTaiScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (!json.getBoolean("success") || json.getString("code") != "1") {
                result.setValid(false)
                result.setMessage(json.getString("message"))
                return result
            }
            result.setValid(true)
            result.setMessage(json.getString("成功"))
            return result
        }


        WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            Map<String, String> resMap = args[2] as Map<String, String>
            log.info("HengTaiScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

            String orderId = resMap.get("requestReference")
            String thirdOrderId = resMap.get("reference")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId, thirdOrderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]
            String thirdOrderId = args[3]

            Map<String, Object> DataContentParms = new TreeMap<>()
            DataContentParms.put("merchant", merchant.getMerchantCode())
            DataContentParms.put("requestReference", orderId)
            DataContentParms.put("reference", thirdOrderId)

            String toSign = MD5.toSign(DataContentParms) + "&key=" + merchant.getPrivateKey()
            DataContentParms.put("sign", MD5.md5(toSign).toUpperCase())

            log.info("HengTaiScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + "/api/merchant/withdraw/query", DataContentParms, requestHeader)
            log.info("HengTaiScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || !json.getBoolean("success") || json.getString("code") != "1") {
                return null
            }
            JSONObject dataJSON = json.getJSONObject("data")
            if (dataJSON == null ) {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            //订单状态判断标准：PENDING，待处理。 LOCKED，在处理。 AUDITED，已支付。 REFUSED，已拒绝。
            if (dataJSON.getString("status") == "AUDITED") {
                notify.setStatus(0)
            } else if (dataJSON.getString("status") == "REFUSED") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> DataContentParams = new TreeMap<>()
            DataContentParams.put("merchant", merchantAccount.getMerchantCode())

            String toSign = MD5.toSign(DataContentParams) + "&key=" + merchantAccount.getPrivateKey()
            DataContentParams.put("sign", MD5.md5(toSign).toUpperCase())

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("HengTaiScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/api/merchant/info", DataContentParams, requestHeader)
            log.info("HengTaiScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (responJSON == null ||  responJSON.getString("code") != "1") {
                return null
            }
            JSONObject dataJSON = responJSON.getJSONObject("data")
            if (dataJSON != null && responJSON.getBoolean("success")) {
                return dataJSON.getBigDecimal("wallet") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("wallet")
            }
            return BigDecimal.ZERO
        }


        /**
         * 获取头部信息
         *
         * @param userId
         * @param userName
         * @param orderId
         * @return
         */
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
    }
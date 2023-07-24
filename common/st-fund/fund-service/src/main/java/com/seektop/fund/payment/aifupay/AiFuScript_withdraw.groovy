    package com.seektop.fund.payment.aifupay

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
     * 爱付支付 - 20210225
     */

    public class AiFuScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(AiFuScript_withdraw.class)

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

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("appId", account.getMerchantCode())
            DataContentParms.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParms.put("custNo", req.getOrderId())
            DataContentParms.put("accountName", req.getName())
            DataContentParms.put("accountNo", req.getCardNo())
            DataContentParms.put("bankName", req.getBankName())
            DataContentParms.put("bankAddr", "上海市")
            DataContentParms.put("notify", account.getNotifyUrl() + account.getMerchantId())
            DataContentParms.put("secret", account.getPrivateKey())

            String toSign = MD5.toAscii(DataContentParms);
            DataContentParms.put("sign", MD5.md5(toSign))
            DataContentParms.remove("secret")

            log.info("AiFuScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/service/carry", DataContentParms, requestHeader)
            log.info("AiFuScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if ("1000" != json.getString("code")) {
                result.setValid(false)
                result.setMessage(json.getString("msg"))
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
            log.info("AiFuScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            String orderId = resMap.get("custNo")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("appId", merchant.getMerchantCode())
            DataContentParms.put("custNo", orderId)
            DataContentParms.put("secret", merchant.getPrivateKey())

            String toSign = MD5.toAscii(DataContentParms)
            DataContentParms.put("sign", MD5.md5(toSign))
            DataContentParms.remove("secret")

            log.info("AiFuScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + "/service/queryWithdrawalResult", DataContentParms, requestHeader)
            log.info("AiFuScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || StringUtils.isEmpty(json.getString("code"))) {
                return null
            }

            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            //订单状态判断标准：0=处理中,1=提现成功，2=提现失败
            if (json.getInteger("code") == 1) {
                notify.setStatus(0)
            } else if (json.getInteger("code") == 2) {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("appId", merchantAccount.getMerchantCode())
            DataContentParams.put("secret", merchantAccount.getPrivateKey())

            String toSign = MD5.toAscii(DataContentParams)
            DataContentParams.put("sign", MD5.md5(toSign))
            DataContentParams.remove("secret")

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("AiFuScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/service/queryBalance", DataContentParams, requestHeader)
            log.info("AiFuScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)

            if (responJSON != null && "1000" == responJSON.getString("code")) {
                return responJSON.getBigDecimal("balance") == null ? BigDecimal.ZERO : responJSON.getBigDecimal("balance")
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
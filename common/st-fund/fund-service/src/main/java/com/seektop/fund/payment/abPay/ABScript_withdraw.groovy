    package com.seektop.fund.payment.abPay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.redis.RedisService
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
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.util.ObjectUtils

    import java.math.RoundingMode

    /**
 * AB支付
 * @auth joy
 * @date 2021-07-18
 */


    public class ABScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(ABScript_withdraw.class)

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
            DataContentParms.put("Amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            DataContentParms.put("BankCardBankName", req.getBankName())
            DataContentParms.put("BankCardNumber", req.getCardNo())
            DataContentParms.put("BankCardRealName", req.getName())
            DataContentParms.put("MerchantId", account.getMerchantCode())
            DataContentParms.put("MerchantUniqueOrderId", req.getOrderId())
            DataContentParms.put("NotifyUrl", account.getNotifyUrl() + account.getMerchantId())
            DataContentParms.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
            DataContentParms.put("WithdrawTypeId", "0")
            String toSign = MD5.toAscii(DataContentParms) + account.getPrivateKey()
            DataContentParms.put("Sign", MD5.md5(toSign))

            log.info("ABScript_Transfer_params: {}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/InterfaceV5/CreateWithdrawOrder/", DataContentParms, requestHeader)
            log.info("ABScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(DataContentParms))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("Code") != "0") {
                result.setValid(false)
                result.setMessage(json.getString("Message"))
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
            log.info("ABScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))

            String orderId = resMap.get("MerchantUniqueOrderId")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> DataContentParms = new HashMap<>()
            DataContentParms.put("MerchantId", merchant.getMerchantCode())
            DataContentParms.put("MerchantUniqueOrderId", orderId)
            DataContentParms.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

            String toSign = MD5.toAscii(DataContentParms) + merchant.getPrivateKey()
            DataContentParms.put("Sign", MD5.md5(toSign))

            log.info("ABScript_TransferQuery_order:{}", JSON.toJSONString(DataContentParms))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + "/InterfaceV6/QueryWithdrawOrder/", DataContentParms, requestHeader)
            log.info("ABScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getString("Code") != "0") {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId.toUpperCase())
            //订单状态判断标准：0 处理中  100 已完成 (已成功)  -90 已撤销（已失败）  -10 订单号不存在
            if (json.getString("WithdrawOrderStatus") == "100") {
                notify.setStatus(0)
                notify.setRsp("SUCCESS")
            } else if (json.getString("WithdrawOrderStatus") == "-90") {
                notify.setStatus(1)
                notify.setRsp("SUCCESS")
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> DataContentParams = new HashMap<>()
            DataContentParams.put("MerchantId", merchantAccount.getMerchantCode())
            DataContentParams.put("Timestamp", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

            String toSign = MD5.toAscii(DataContentParams) + merchantAccount.getPrivateKey()
            DataContentParams.put("Sign", MD5.md5(toSign))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("ABScript_QueryBalance_reqMap: {}", JSON.toJSONString(DataContentParams))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/InterfaceV6/GetBalanceAmount/", DataContentParams, requestHeader)
            log.info("ABScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (ObjectUtils.isEmpty(responJSON) || responJSON.getString("Code") != "0") {
                return BigDecimal.ZERO;
            }
            return responJSON.getBigDecimal("BalanceAmount") == null ? BigDecimal.ZERO : responJSON.getBigDecimal("BalanceAmount")
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
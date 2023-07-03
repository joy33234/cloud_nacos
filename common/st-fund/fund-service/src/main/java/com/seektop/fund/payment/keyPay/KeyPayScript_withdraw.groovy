    package com.seektop.fund.payment.keyPay

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
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.util.ObjectUtils

    import java.math.RoundingMode

    /**
 * keypay支付
 * @auth joy
 * @date 2021-07-20
 */


    public class KeyPayScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(KeyPayScript_withdraw.class)

        private OkHttpUtil okHttpUtil

        private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness



        WithdrawResult withdraw(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
            GlWithdraw req = args[2] as GlWithdraw
            this.glPaymentChannelBankBusiness = BaseScript.getResource(args[3], ResourceEnum.GlPaymentChannelBankBusiness) as GlPaymentChannelBankBusiness


            WithdrawResult result = new WithdrawResult()

            Map<String, Object> params = new HashMap<>()
            params.put("apiKey", account.getMerchantCode())
            params.put("version", "v2")
            params.put("custNo", req.getOrderId())
            params.put("bankCode", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
            params.put("bankName", glPaymentChannelBankBusiness.getBankName(req.getBankId(), account.getChannelId()));
            params.put("bankSubName", "上海支行");
            params.put("bankUserName", req.getName())
            params.put("bankAccount", req.getCardNo())
            params.put("province", "上海市");
            params.put("city", "上海市");
            params.put("money", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            params.put("okUrl", account.getNotifyUrl() + account.getMerchantId())

            String toSign = MD5.toAscii(params) + account.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            log.info("KeyPayScript_Transfer_params: {}", JSON.toJSONString(params))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/Payout/pay", params, requestHeader)
            log.info("KeyPayScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(params))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("code") != "1") {
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
            log.info("KeyPayScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            JSONObject json = JSONObject.parseObject(resMap.get("reqBody"));
            String orderid;
            if (json == null) {
                orderid = resMap.get("custNo");
            } else {
                orderid = json.getString("custNo");
            }
            if (StringUtils.isNotEmpty(orderid)) {
                return withdrawQuery(okHttpUtil, merchant, orderid)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> params = new HashMap<>()
            params.put("apiKey", merchant.getMerchantCode())
            params.put("custNo", orderId)

            String toSign = MD5.toAscii(params) + merchant.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            log.info("KeyPayScript_TransferQuery_order:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Payout/get", params, requestHeader)
            log.info("KeyPayScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getString("code") != "1") {
                return null
            }
            JSONObject dataJSON = json.getJSONObject("dataInfo")
            if (ObjectUtils.isEmpty(dataJSON)) {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId)
            //订单状态判断标准：订单状态: 0:未成功连接 1:已成功连接，等待付款 2:已付款完成 8:失敗 9:取消
            if (dataJSON.getString("status") == "2") {
                notify.setStatus(0)
                notify.setRsp("OK")
            } else if (dataJSON.getString("status") == "8" || dataJSON.getString("status") == "9") {
                notify.setStatus(1)
                notify.setRsp("OK")
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> params = new HashMap<>()
            params.put("apiKey", merchantAccount.getMerchantCode())
            params.put("version", "v2")
            params.put("date", DateUtils.format(new Date(), DateUtils.YYYYMMDD))

            String toSign = MD5.toAscii(params) + merchantAccount.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("KeyPayScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Transaction/total", params, requestHeader)
            log.info("KeyPayScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (ObjectUtils.isEmpty(responJSON) || responJSON.getString("code") != "1") {
                return BigDecimal.ZERO;
            }
            JSONObject dataJSON = responJSON.getJSONObject("dataInfo")
            if (ObjectUtils.isEmpty(dataJSON)) {
                return BigDecimal.ZERO;
            }
            return dataJSON.getBigDecimal("money") == null ? BigDecimal.ZERO : dataJSON.getBigDecimal("money")
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
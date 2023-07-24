    package com.seektop.fund.payment.yuandingPay

    import com.alibaba.fastjson.JSON
    import com.alibaba.fastjson.JSONArray
    import com.alibaba.fastjson.JSONObject
    import com.seektop.common.http.GlRequestHeader
    import com.seektop.common.http.OkHttpUtil
    import com.seektop.common.utils.DateUtils
    import com.seektop.common.utils.MD5
    import com.seektop.enumerate.GlActionEnum
    import com.seektop.exception.GlobalException
    import com.seektop.fund.model.GlWithdraw
    import com.seektop.fund.model.GlWithdrawMerchantAccount
    import com.seektop.fund.payment.WithdrawNotify
    import com.seektop.fund.payment.WithdrawResult
    import org.apache.commons.lang.StringUtils
    import org.slf4j.Logger
    import org.slf4j.LoggerFactory
    import org.springframework.util.ObjectUtils

    import java.math.RoundingMode

    /**
 * 元鼎支付
 * @auth joy
 * @date 2021-07-20
 */


    public class YuanDingScript_withdraw {
        private static final Logger log = LoggerFactory.getLogger(YuanDingScript_withdraw.class)

        private OkHttpUtil okHttpUtil


        WithdrawResult withdraw(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount account = args[1] as GlWithdrawMerchantAccount
            GlWithdraw req = args[2] as GlWithdraw

            WithdrawResult result = new WithdrawResult()

            Map<String, Object> params = new HashMap<>()
            params.put("userid", account.getMerchantCode())
            params.put("action", "withdraw")
            params.put("notifyurl", account.getNotifyUrl() + account.getMerchantId())

            JSONObject paramJson = new JSONObject();
            JSONArray array = new JSONArray();
            paramJson.put("orderno", req.getOrderId())
            paramJson.put("date", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))
            paramJson.put("amount", req.getAmount().subtract(req.getFee()).setScale(2, RoundingMode.DOWN).toString())
            paramJson.put("account", req.getCardNo())
            paramJson.put("name", req.getName())
            paramJson.put("bank", req.getBankName())
            paramJson.put("subbranch", "上海支行")
            array.add(paramJson);
            params.put("content", array.toJSONString())

            String toSign = params.get("userid") + params.get("action") + params.get("content") + account.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            log.info("YuanDingScript_Transfer_params: {}", JSON.toJSONString(params))
            GlRequestHeader requestHeader =
                    this.getRequestHeard(req.getUserId() + "", req.getUsername(), req.getOrderId(), GlActionEnum.WITHDRAW.getCode(), account.getChannelId(), account.getChannelName())
            String resStr = okHttpUtil.post(account.getPayUrl() + "/Apipay", params, requestHeader)
            log.info("YuanDingScript_Transfer_resStr: {}", resStr)

            result.setOrderId(req.getOrderId())
            result.setReqData(JSON.toJSONString(params))
            result.setResData(resStr)
            JSONObject json = JSON.parseObject(resStr)
            if (json == null) {
                result.setValid(false)
                result.setMessage("API异常:请联系出款商户确认订单.")
                return result
            }
            if (json.getString("status") != "1") {
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
            log.info("YuanDingScript_withdrawNotify_resMap:{}", JSON.toJSONString(resMap))
            String orderId = resMap.get("orderno")
            if (StringUtils.isNotEmpty(orderId)) {
                return withdrawQuery(okHttpUtil, merchant, orderId)
            }
        }


        WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchant = args[1] as GlWithdrawMerchantAccount
            String orderId = args[2]

            Map<String, Object> params = new HashMap<>()
            params.put("userid", merchant.getMerchantCode())
            params.put("orderno", orderId)
            params.put("action", "withdrawquery")

            String toSign = params.get("userid") + params.get("action") + params.get("orderno") + merchant.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            log.info("YuanDingScript_TransferQuery_order:{}", JSON.toJSONString(params))
            GlRequestHeader requestHeader =
                    this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode(), merchant.getChannelId(), merchant.getChannelName())
            String resStr = okHttpUtil.post(merchant.getPayUrl() + "/Apipay", params, requestHeader)
            log.info("YuanDingScript_TransferQuery_resStr:{}", resStr)

            JSONObject json = JSON.parseObject(resStr)
            if (json == null || json.getString("status") != "1") {
                return null
            }
            JSONObject dataJSON = json.getJSONObject("content")
            if (ObjectUtils.isEmpty(dataJSON)) {
                return null
            }
            WithdrawNotify notify = new WithdrawNotify()
            notify.setMerchantCode(merchant.getMerchantCode())
            notify.setMerchantId(merchant.getMerchantId())
            notify.setMerchantName(merchant.getChannelName())
            notify.setOrderId(orderId.toUpperCase())
            //订单状态判断标准：【0】申请提现中【1】已支付【2】冻结【3】已驳回
            if (dataJSON.getString("orderstatus") == "1") {
                notify.setStatus(0)
            } else if (dataJSON.getString("orderstatus") == "3") {
                notify.setStatus(1)
            } else {
                notify.setStatus(2)
            }
            return notify
        }


        BigDecimal balanceQuery(Object[] args)  {
            this.okHttpUtil = args[0] as OkHttpUtil
            GlWithdrawMerchantAccount merchantAccount = args[1] as GlWithdrawMerchantAccount

            Map<String, String> params = new HashMap<>()
            params.put("userid", merchantAccount.getMerchantCode())
            params.put("action", "balance")
            params.put("date", DateUtils.format(new Date(), DateUtils.YYYYMMDDHHMMSS))

            String toSign =  params.get("userid") + params.get("date") + params.get("action") + merchantAccount.getPrivateKey()
            params.put("sign", MD5.md5(toSign))

            GlRequestHeader requestHeader =
                    getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode(), merchantAccount.getChannelId(), merchantAccount.getChannelName())

            log.info("YuanDingScript_QueryBalance_reqMap: {}", JSON.toJSONString(params))
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl() + "/Apipay", params, requestHeader)
            log.info("YuanDingScript_QueryBalance_resStr: {}", resStr)

            JSONObject responJSON = JSON.parseObject(resStr)
            if (ObjectUtils.isEmpty(responJSON) || responJSON.getString("status") != "1") {
                return BigDecimal.ZERO;
            }
            return responJSON.getBigDecimal("money") == null ? BigDecimal.ZERO : responJSON.getBigDecimal("money")
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
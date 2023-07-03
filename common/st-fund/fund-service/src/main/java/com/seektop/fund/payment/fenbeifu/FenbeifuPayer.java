
package com.seektop.fund.payment.fenbeifu;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import com.seektop.common.http.GlRequestHeader;
import com.seektop.common.http.OkHttpUtil;
import com.seektop.common.utils.DateUtils;
import com.seektop.common.utils.MD5;
import com.seektop.constant.FundConstant;
import com.seektop.enumerate.GlActionEnum;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * 分贝付支付
 *
 * @author joy
 */
@Slf4j
@Service(FundConstant.PaymentChannel.FENBEIFUPAY + "")
public class FenbeifuPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    @Resource
    private GlPaymentChannelBankBusiness glPaymentChannelBankBusiness;

    @Resource
    private GlWithdrawBusiness glWithdrawBusiness;

    @Resource
    private OkHttpUtil okHttpUtil;

    private static final String SERVEL_PAY = "/Bank/";//支付地址
    private static final String SERVEL_ORDER_QUERY = "/search.aspx";//订单查询地址

    private static Map<String, String> bankCodeMap = new HashMap<>();

    static {
        bankCodeMap.put("970", "CMB");//招商银行
        bankCodeMap.put("967", "ICBC");//工商银行
        bankCodeMap.put("965", "CCB");//建设银行
        bankCodeMap.put("964", "ABC");//农业银行
        bankCodeMap.put("963", "BOC");//中国银行
        bankCodeMap.put("980", "CMBC");//民生银行
        bankCodeMap.put("978", "PINAN");//平安银行
        bankCodeMap.put("972", "CIB");//兴业银行
        bankCodeMap.put("962", "CITIC");//中信银行
        bankCodeMap.put("971", "PSBC");//中国邮政储蓄银行
        bankCodeMap.put("982", "HXB");//华夏银行
    }

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, RechargePrepareDO req, GlRechargeResult result) {
        String typeid = "";
        String type = "";
        if (FundConstant.PaymentType.ALI_PAY == merchant.getPaymentId()) {
            typeid = "98";//支付宝二维码
            type = "992";//支付宝二维码
        } else if (FundConstant.PaymentType.WECHAT_PAY == merchant.getPaymentId()) {
            typeid = "99";//微信二维码
            type = "991";//微信二维码
        } else if (FundConstant.PaymentType.ONLINE_PAY == merchant.getPaymentId()) {
            typeid = "102";//网银支付  type值每个银⾏需要逐个接⼊
        } else if (FundConstant.PaymentType.QUICK_PAY == merchant.getPaymentId()) {
            typeid = "1020";//银联快捷
            type = "1005";//银联快捷
        } else if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId()) {
            typeid = "101";//银联扫码
            type = "1021";//银联扫码
        }
        prepareScan(merchant, payment, req, result, typeid, type);
    }

    public void prepareScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result, String typeid, String type) {
        try {
            Map<String, String> params = new LinkedHashMap();
            params.put("parter", account.getMerchantCode());
            if (StringUtils.equals("102", typeid)) {//网银支付
                params.put("type", glPaymentChannelBankBusiness.getBankCode(req.getBankId(), account.getChannelId()));
            } else {
                params.put("type", type);
            }
            params.put("value", req.getAmount().setScale(2, BigDecimal.ROUND_DOWN) + "");//最⼩⽀付⾦额为 0.02。银联扫码最低额 10 元
            params.put("orderid", req.getOrderId());
            params.put("tyid", typeid);
            params.put("callbackurl", account.getNotifyUrl() + merchant.getId());

            String tosign = MD5.toSign(params);
            params.put("sign", MD5.md5(tosign + account.getPrivateKey()));
            String paramsStr = MD5.toSign(params);
            result.setRedirectUrl(account.getPayUrl() + SERVEL_PAY + "?" + paramsStr);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 支付结果
     *
     * @param merchant
     * @param account
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("FenbeifuPayer_notify_resp:{}", JSON.toJSONString(resMap));
        String orderId = resMap.get("orderid");
        if (null != orderId && !"".equals(orderId)) {
            return this.queryOrder(account, orderId, resMap.get("ekaorderid"));
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        return this.queryOrder(account, orderId, "");
    }

    private RechargeNotify queryOrder(GlPaymentMerchantaccount account, String orderId, String thirdOrderId) throws GlobalException {
        Map<String, String> params = new HashMap<String, String>();
        params.put("orderid", orderId);
        params.put("parter", account.getMerchantCode());
        String sign = MD5.toSign(params) + account.getPrivateKey();
        params.put("sign", MD5.md5(sign));
        GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.RECHARGE_QUERY.getCode());
        log.info("FenbeifuPayer_query_params:{}", JSON.toJSONString(params));
        String resStr = okHttpUtil.get(account.getPayUrl() + SERVEL_ORDER_QUERY, params, requestHeader);
        log.info("FenbeifuPayer_query_resStr:{}", resStr);
        if (StringUtils.isNotEmpty(resStr)) {
            Map<String, String> map = this.getMap(resStr);// 3：请求参数⽆效 2：签名错误 1：商户订单号⽆效 0：⽀付成功 其他：⽤户还未完成⽀付或者⽀付失败
            if (map.get("opstate").equals("0")) {
                RechargeNotify pay = new RechargeNotify();
                pay.setAmount(new BigDecimal(map.get("ovalue")));
                pay.setFee(BigDecimal.ZERO);
                pay.setOrderId(orderId);
                pay.setThirdOrderId(thirdOrderId);
                return pay;
            }
        }
        return null;
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount payment, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw withdraw) throws GlobalException {
        WithdrawResult result = new WithdrawResult();
        try {

            PublicKey publicKey = RSAPemCoder.getPublicKey(merchantAccount.getPublicKey());
            PrivateKey privateKey = RSAPemCoder.getPrivateKey(merchantAccount.getPrivateKey());

            TreeMap<String, String> params = new TreeMap<String, String>();
            params.put("version", "1.0");
            params.put("trancode", "10001");
            params.put("parter", merchantAccount.getMerchantCode());
            params.put("orderid", withdraw.getOrderId());
            params.put("trantime", DateUtils.format(withdraw.getCreateDate(), DateUtils.YYYYMMDDHHMMSS));
            params.put("channelid", "");
            params.put("amount", withdraw.getAmount().subtract(withdraw.getFee()).setScale(2, BigDecimal.ROUND_DOWN) + "");
            params.put("callbackurl", merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
            params.put("trantype", "0");//0:银行卡代付
            params.put("accattr", "0");//0:对私 1:对公(暂不支持)
            params.put("bankcode", bankCodeMap.get(glPaymentChannelBankBusiness.getBankCode(withdraw.getBankId(), merchantAccount.getChannelId())));
            params.put("accname", RSAPemCoder.encryptByPublicKey(withdraw.getName().getBytes(Charset.forName("utf-8")), publicKey));
            params.put("accno", RSAPemCoder.encryptByPublicKey(withdraw.getCardNo().getBytes(Charset.forName("utf-8")), publicKey));
            params.put("phone", "13611111111");
            params.put("province", "Shanghai");
            params.put("city", "Shanghai");
            params.put("branchname", "Shanghai");
            params.put("branchno", "");
            params.put("attach", "");
            params.put("signtype", "RSA");
            params.put("sign", RSAPemCoder.sign(params, privateKey));

            log.info("FenbeifuPayer_doTransfer_params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard(withdraw.getUserId() + "", withdraw.getUsername(), withdraw.getOrderId(), GlActionEnum.WITHDRAW.getCode());
            String resStr = okHttpUtil.post(merchantAccount.getPayUrl(), params, requestHeader);
            log.info("FenbeifuPayer_doTransfer_resp:{}", resStr);

            result.setOrderId(withdraw.getOrderId());
            result.setReqData(JSON.toJSONString(params));
            result.setResData(resStr);
            if (StringUtils.isEmpty(resStr)) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }

            JSONObject json = JSON.parseObject(resStr);
            if (json != null && json.getString("code").equals("0")) {
                //验证服务器签名
                TreeMap<String, String> paramsRes = JSON.parseObject(resStr, new TypeReference<TreeMap<String, String>>() {
                });
                String resSign = paramsRes.get("sign");
                paramsRes.remove("sign");
                boolean isVerify = RSAPemCoder.verify(paramsRes, publicKey, resSign);
                if (isVerify) {
                    JSONObject dataJson = JSON.parseObject(json.getString("data"));
                    result.setValid(true);
                    result.setMessage(dataJson.getString("msg"));
                }
            } else {
                result.setValid(false);
                result.setMessage("API返回数据异常:请联系出款商户确认订单.");
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("FenbeifuPayer_doTransferNotify_resp:{}", JSON.toJSONString(resMap));  //商户不支持回调
        String orderId = resMap.get("orderid");
        if (StringUtils.isNotEmpty(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        GlWithdraw withdraw = glWithdrawBusiness.findById(orderId);
        TreeMap<String, String> params = new TreeMap<>();
        params.put("version", "1.0");
        params.put("trancode", "20001");
        params.put("parter", merchant.getMerchantCode());
        params.put("orderid", orderId);
        params.put("trantime", DateUtils.format(withdraw.getCreateDate(), DateUtils.YYYYMMDDHHMMSS));
        params.put("signtype", "RSA");

        WithdrawNotify notify = new WithdrawNotify();
        try {
            PublicKey publicKey = RSAPemCoder.getPublicKey(merchant.getPublicKey());
            PrivateKey privateKey = RSAPemCoder.getPrivateKey(merchant.getPrivateKey());
            params.put("sign", RSAPemCoder.sign(params, privateKey));

            log.info("FenbeifuPayer_doTransferQuery_params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard("", "", orderId, GlActionEnum.WITHDRAW_QUERY.getCode());

            String resStr = okHttpUtil.post(merchant.getPayUrl(), params, requestHeader);
            log.info("FenbeifuPayer_doTransferQuery_resp:{}", resStr);

            if (StringUtils.isEmpty(resStr)) {
                return null;
            }

            JSONObject json = JSON.parseObject(resStr);
            if (json != null && json.getString("code").equals("0")) {
                JSONObject resultJson = JSON.parseObject(json.getString("data"));

                //验证服务器签名
                TreeMap<String, String> paramsRes = JSON.parseObject(resStr, new TypeReference<TreeMap<String, String>>() {
                });
                String resSign = paramsRes.get("sign");
                paramsRes.remove("sign");
                boolean isVerify = RSAPemCoder.verify(paramsRes, publicKey, resSign);
                if (isVerify) {
                    notify.setAmount(resultJson.getBigDecimal("amount"));
                    notify.setMerchantCode(merchant.getMerchantCode());
                    notify.setMerchantId(merchant.getMerchantId());
                    notify.setMerchantName(merchant.getChannelName());
                    notify.setOrderId(orderId);
                    notify.setThirdOrderId(resultJson.getString("tranorderid"));
                    if (json.getString("code").equals("1")) {// // 商户 0：付款中 1：付款成功 -1：付款失败(付款金额已退回商户)   出款状态：0成功，1失败,2处理中
                        notify.setStatus(0);
                    } else if (json.getString("code").equals("-1")) {
                        notify.setStatus(1);
                    } else {
                        notify.setStatus(2);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        log.info("notify:{}", JSON.toJSONString(notify));
        return notify;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        TreeMap<String, String> params = new TreeMap<>();
        params.put("version", "1.0");
        params.put("trancode", "30001");
        params.put("parter", merchantAccount.getMerchantCode());
        params.put("signtype", "RSA");
        try {
            PublicKey publicKey = RSAPemCoder.getPublicKey(merchantAccount.getPublicKey());
            PrivateKey privateKey = RSAPemCoder.getPrivateKey(merchantAccount.getPrivateKey());
            params.put("sign", RSAPemCoder.sign(params, privateKey));

            log.info("FenbeifuPayer_queryBalance_params:{}", JSON.toJSONString(params));
            GlRequestHeader requestHeader = this.getRequestHeard("", "", "", GlActionEnum.PAYMENT_QUERY_BALANCE.getCode());

            String resStr = okHttpUtil.post(merchantAccount.getPayUrl(), params, requestHeader);

            log.info("FenbeifuPayer_queryBalance_resp:{}", resStr);
            JSONObject json = JSON.parseObject(resStr);
            if (json != null && json.getString("code").equals("0")) {
                //验证服务器签名
                TreeMap<String, String> paramsRes = JSON.parseObject(resStr, new TypeReference<TreeMap<String, String>>() {
                });
                String resSign = paramsRes.get("sign");
                paramsRes.remove("sign");
                boolean isVerify = RSAPemCoder.verify(paramsRes, publicKey, resSign);
                if (isVerify) {
                    JSONObject dataJson = JSON.parseObject(json.getString("data"));
                    log.info("balance:{}", JSON.toJSONString(dataJson.getBigDecimal("balance")));
                    return dataJson.getBigDecimal("balance");
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }

    /**
     * 获取头部信息
     *
     * @param userId
     * @param userName
     * @param orderId
     * @return
     */
    private GlRequestHeader getRequestHeard(String userId, String userName, String orderId, String code) {
        GlRequestHeader requestHeader = GlRequestHeader.builder()
                .action(code)
                .channelId(PaymentMerchantEnum.FENBEIFU_PAY.getCode() + "")
                .channelName(PaymentMerchantEnum.FENBEIFU_PAY.getPaymentName())
                .userId(userId)
                .userName(userName)
                .tradeId(orderId)
                .build();
        return requestHeader;
    }

    /**
     * 订单查询数据转为map
     *
     * @param restr
     * @return
     */
    private Map<String, String> getMap(String restr) {
        Map<String, String> map = new HashMap<>();
        if (StringUtils.isNotEmpty(restr)) {
            String resArr[] = restr.trim().split("&");
            for (String str : resArr) {
                if (StringUtils.isNotEmpty(str)) {
                    String arr[] = str.split("=");
                    if (arr != null && arr.length == 2) {
                        map.put(arr[0], arr[1]);
                    }
                }
            }
        }
        return map;
    }

}

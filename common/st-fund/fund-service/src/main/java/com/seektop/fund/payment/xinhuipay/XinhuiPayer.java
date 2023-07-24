package com.seektop.fund.payment.xinhuipay;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.utils.HtmlTemplateUtils;
import com.seektop.common.utils.XMLUtil;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlPaymentChannelBankBusiness;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.*;
import com.seektop.fund.payment.xinhuipay.common.*;
import com.seektop.fund.payment.xinhuipay.common.util.DocCommUtils;
import com.seektop.fund.payment.xinhuipay.common.util.XinhuiHttpUtils;
import com.seektop.fund.payment.xinhuipay.secure.utils.KeyUtil;
import com.seektop.fund.payment.xinhuipay.secure.utils.RSASignUtil;
import com.thoughtworks.xstream.XStream;
import com.thoughtworks.xstream.io.xml.DomDriver;
import com.thoughtworks.xstream.io.xml.XmlFriendlyReplacer;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.dom4j.Document;
import org.dom4j.DocumentException;
import org.dom4j.DocumentHelper;
import org.dom4j.Element;
import org.springframework.stereotype.Service;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.security.PrivateKey;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;

@Slf4j
@Service(FundConstant.PaymentChannel.XINHUIPAY + "")
public class XinhuiPayer implements GlPaymentRechargeHandler, GlPaymentWithdrawHandler {

    protected XStream xStream = new XStream(new DomDriver("gbk", new XmlFriendlyReplacer("-_", "_")));

    @Resource
    private GlPaymentChannelBankBusiness channelBankBusiness;


    private static final String dateStyle = "yyyyMMddHHmmss";

    @Override
    public RechargeNotify result(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        return null;
    }

    @Override
    public void prepare(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        if (FundConstant.PaymentType.UNIONPAY_SACN == merchant.getPaymentId() ||
                FundConstant.PaymentType.UNION_PAY == merchant.getPaymentId()) {
            prepareToScan(merchant, account, req, result);
        }
    }

    private void prepareToScan(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, RechargePrepareDO req, GlRechargeResult result) throws GlobalException {
        JSONObject jsonObject = new JSONObject();
        jsonObject.put("tradeType", "SCANCODE");

        MsgRequest3510 request = new MsgRequest3510();
        request.getComm_head().setSndChnlNo("W18").setTxnNo("3510");
        request.getMain_data()
                .setPayProducts("13")
                .setTxnType("00")
                .setMerId(account.getMerchantCode())
                .setMerName("BB")
                .setMerAbbr("暂无")
                .setOrderId(req.getOrderId())
                .setTxnTime(ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateStyle).withZone(ZoneId.systemDefault())))
                .setTxnAmt(req.getAmount().multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString())
                .setCurrencyCode("156").setCommodityName("Recharge")
                .setCommoditDesc("Recharge")
                .setBackUrl(account.getNotifyUrl() + merchant.getId())
                .setProdExtend(jsonObject.toJSONString());
        String xmlString = buildRequest(request, account.getPublicKey(), account.getPrivateKey());
        log.info("XinhuiPayer_recharge_prepare_params:{}", xmlString);
        String resp = XinhuiHttpUtils.doPost(account.getPayUrl(), xmlString, "GBK");
        log.info("XinhuiPayer_recharge_prepare_result:{}", resp);
        try {
            Document resDoc = DocumentHelper.parseText(resp.substring(6));
            Element rspNoCode = (Element) resDoc.selectSingleNode("//msg/comm_head/rspNo");
            if ("000000".equals(rspNoCode.getStringValue().trim())) {
                Element fieldNamElement = (Element) resDoc.selectSingleNode("//msg/main_data/prodExtend");
                JSONObject json = JSONObject.parseObject(fieldNamElement.getStringValue().trim());
                if (json.containsKey("qrCode")) {
                    result.setMessage(HtmlTemplateUtils.getQRCode(json.getString("qrCode")));
                }
            }
        } catch (DocumentException e) {
            throw new RuntimeException("创建订单失败");
        }
    }

    @Override
    public RechargeNotify notify(GlPaymentMerchantApp merchant, GlPaymentMerchantaccount account, Map<String, String> resMap) throws GlobalException {
        log.info("xinhuiPayer_notify_params:{}", resMap);
        if (StringUtils.isNotEmpty(resMap.get("orderId"))) {
            return this.query(account, resMap.get("orderId"));
        }
        return null;
    }

    @Override
    public RechargeNotify query(GlPaymentMerchantaccount account, String orderId) throws GlobalException {
        try {
            MsgRequest3903 request = new MsgRequest3903();
            request.getComm_head().setSndChnlNo("W18").setTxnNo("3903");
            request.getMain_data().setMerId(account.getMerchantCode()).setOrderId(orderId);
            String xmlString = buildRequest(request, account.getPublicKey(), account.getPrivateKey());
            log.info("XinhuiPayer_Query_params:{}", xmlString);
            String resp = XinhuiHttpUtils.doPost(account.getPayUrl(), xmlString, "GBK");
            log.info("XinhuiPayer_Query_result:{}", resp);

            Document resDoc = DocumentHelper.parseText(resp.substring(6));
            Element respCode = (Element) resDoc.selectSingleNode("//msg/main_data/respCode");
            if ("EO0000".equals(respCode.getStringValue())) {
                Element orderStau = (Element) resDoc.selectSingleNode("//msg/main_data/orderStau");
                if ("0006".equals(orderStau.getStringValue())) { // 支付成功
                    Element amount = (Element) resDoc.selectSingleNode("//msg/main_data/txnAmt");
                    RechargeNotify pay = new RechargeNotify();
                    BigDecimal orderAmount = (new BigDecimal(amount.getStringValue())).divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
                    pay.setAmount(orderAmount);
                    pay.setFee(BigDecimal.ZERO);
                    pay.setOrderId(orderId);
                    Element orderQid = (Element) resDoc.selectSingleNode("//msg/main_data/orderQid");
                    pay.setThirdOrderId(orderQid.getStringValue());
                    return pay;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(String.format("XinhuiPayer query error :%s ", e.getMessage()));
        }
        return null;
    }

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        try {
            MsgRequest3530 msgRequest3530 = new MsgRequest3530();
            msgRequest3530.getComm_head().setSndChnlNo("W18").setTxnNo("3530");
            MsgRequest3530.MainData mainData = msgRequest3530.getMain_data();
            mainData.setDfType("01");
            mainData.setMerId(merchantAccount.getMerchantCode());
            mainData.setMerName("BallBet");
            mainData.setOrderId(req.getOrderId());
            mainData.setTxnTime(ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateStyle).withZone(ZoneId.systemDefault())));
            mainData.setTxnAmt(req.getAmount().subtract(req.getFee()).multiply(new BigDecimal(100)).setScale(0, RoundingMode.DOWN).toString());
            mainData.setCurrencyCode("156");
            mainData.setBankNo(channelBankBusiness.getBankCode(req.getBankId(), merchantAccount.getChannelId()));
            mainData.setBankName(channelBankBusiness.getBankName(req.getBankId(), merchantAccount.getChannelId()));
            mainData.setAccName(req.getName());
            mainData.setAccNo(req.getCardNo());
            mainData.setCertType("01");
            mainData.setCertNo("440303199008312956");
            mainData.setPhoneNo("13111111111");
            mainData.setBackUrl(merchantAccount.getNotifyUrl() + merchantAccount.getMerchantId());
            mainData.setReserved("CZ");
            String xmlString = buildRequest(msgRequest3530, merchantAccount.getPublicKey(), merchantAccount.getPrivateKey());
            log.info("XinhuiPayer_doTransfer_params:{}", xmlString);
            String resp = XinhuiHttpUtils.doPost(merchantAccount.getPayUrl(), xmlString, "GBK");
            log.info("XinhuiPayer_doTransfer_result:{}", resp);

            Map<String, Object> resMap = XMLUtil.fromXML(resp.substring(6));
            WithdrawResult result = new WithdrawResult();
            result.setReqData(xmlString);
            result.setResData(resp);
            result.setOrderId(req.getOrderId());
            if (StringUtils.isEmpty(resp)) {
                result.setValid(false);
                result.setMessage("API异常:请联系出款商户确认订单.");
                return result;
            }
            Map<String, Object> msg = (Map) resMap.get("msg");
            Map<String, Object> mainRes = (Map) msg.get("main_data");
            Map<String, Object> commHead = (Map) msg.get("comm_head");
            if (ObjectUtils.isEmpty(mainRes)) {
                result.setValid(false);
                result.setMessage(commHead.get("rspMsg").toString());
                return result;
            }
            if (StringUtils.isEmpty(mainRes.get("status").toString()) || mainRes.get("status").toString().equals("02")) {
                result.setValid(false);
                result.setMessage(mainRes.get("respMsg").toString());
                return result;
            }
            result.setOrderId(req.getOrderId());
            result.setReqData(resp);
            result.setValid(true);
            return result;
        } catch (Exception e) {

        }
        return null;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        log.info("xinhuiPayer_doTransferNotify_params:{}", resMap);
        String orderId = resMap.get("orderId");
        if (!StringUtils.isEmpty(orderId)) {
            return this.doTransferQuery(merchant, orderId);
        }
        return null;
    }


    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {

        try {
            MsgRequest3531 msgRequest3531 = new MsgRequest3531();
            msgRequest3531.getComm_head().setSndChnlNo("W18").setTxnNo("3531");
            msgRequest3531.getMain_data().setMerId(merchant.getMerchantCode());
            msgRequest3531.getMain_data().setQueryTime(ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateStyle).withZone(ZoneId.systemDefault())));
            msgRequest3531.getMain_data().setOrderId(orderId);

            String xmlString = buildRequest(msgRequest3531, merchant.getPublicKey(), merchant.getPrivateKey());
            log.info("XinhuiPayer_doTransferQuery_params:{}", xmlString);
            String resp = XinhuiHttpUtils.doPost(merchant.getPayUrl(), xmlString, "GBK");
            log.info("XinhuiPayer_doTransferQuery_result:{}", resp);

            if (StringUtils.isEmpty(resp)) {
                return null;
            }
            Document resDoc = DocumentHelper.parseText(resp.substring(6));

            WithdrawNotify notify = new WithdrawNotify();

            Element amount = (Element) resDoc.selectSingleNode("//msg/main_data/txnAmt");
            Element status = (Element) resDoc.selectSingleNode("//msg/main_data/status");

            BigDecimal orderAmount = (new BigDecimal(amount.getStringValue())).divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
            notify.setAmount(orderAmount);
            notify.setMerchantCode(merchant.getMerchantCode());
            notify.setMerchantId(merchant.getMerchantId());
            notify.setMerchantName(merchant.getChannelName());
            notify.setOrderId(orderId);
            if ("03".equals(status.getStringValue())) {
                notify.setStatus(0);
            } else if ("02".equals(status.getStringValue())) {
                notify.setStatus(1);
            } else {
                notify.setStatus(2);
            }
            return notify;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {

        try {
            MsgRequest3532 msgRequest3532 = new MsgRequest3532();
            msgRequest3532.getComm_head().setSndChnlNo("W18").setTxnNo("3532");
            msgRequest3532.getMain_data().setMerId(merchantAccount.getMerchantCode());
            msgRequest3532.getMain_data().setQueryTime(ZonedDateTime.now().format(DateTimeFormatter.ofPattern(dateStyle).withZone(ZoneId.systemDefault())));

            String xmlString = buildRequest(msgRequest3532, merchantAccount.getPublicKey(), merchantAccount.getPrivateKey());
            log.info("XinhuiPayer_queryBalance_params:{}", xmlString);
            String resp = XinhuiHttpUtils.doPost(merchantAccount.getPayUrl(), xmlString, "GBK");
            log.info("XinhuiPayer_queryBalance_result:{}", resp);

            Document resDoc = DocumentHelper.parseText(resp.substring(6));
            Element respCode = (Element) resDoc.selectSingleNode("//msg/main_data/respCode");

            Element balance = (Element) resDoc.selectSingleNode("//msg/main_data/balance");

            if (!"EO0000".equals(respCode.getStringValue())) {
                return BigDecimal.ZERO;
            }
            BigDecimal retBalance = new BigDecimal(balance.getStringValue());
            return retBalance.divide(new BigDecimal(100)).setScale(2, RoundingMode.DOWN);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return BigDecimal.ZERO;
    }


    private String buildRequest(Object obj, String publicKey, String privateKey) {
        try {
            xStream.alias("msg", obj.getClass());
            String xmlString = xStream.toXML(obj);
            Document reqDoc = DocumentHelper.parseText(xmlString);
            xmlString = signByRsa(reqDoc, publicKey, privateKey);
            String reqLen = xmlString.getBytes("GBK").length + "";
            return "000000".substring(reqLen.length()) + reqLen + xmlString;
        } catch (DocumentException | UnsupportedEncodingException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * @param resDoc
     * @return
     * @throws Exception
     * @Description <p>
     * rsa签名
     * </p>
     */
    public static String signByRsa(Document resDoc, String publicKey, String privateKey) {
        /**
         * 报文签名
         */
        resDoc.setXMLEncoding("GB18030");
        TreeMap<String, String> signTreeMap = DocCommUtils.convertDocToSignMap(resDoc);
        signTreeMap.remove("signature");

        String signature = "";
        try {
            PrivateKey privateKeys = KeyUtil.getSignRsaPrivateKey(privateKey);

            signature = RSASignUtil.signByRsa(DocCommUtils.packageSignatureStr(signTreeMap), privateKeys);

            DocCommUtils.setTextValue(resDoc, "//msg/main_data/signature", signature);
        } catch (Exception e) {
            e.printStackTrace();

            log.error("sign error!", e);
        }

        return resDoc.asXML();
    }

}

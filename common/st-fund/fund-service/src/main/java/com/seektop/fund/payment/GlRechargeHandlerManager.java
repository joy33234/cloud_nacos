package com.seektop.fund.payment;

import com.seektop.common.redis.RedisService;
import com.seektop.constant.FundConstant;
import com.seektop.fund.business.recharge.GlPaymentMerchantAccountBusiness;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.partner.result.PaymentMerchantResponse;
import com.seektop.fund.controller.partner.result.PaymentResponse;
import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import com.seektop.fund.model.GlPaymentMerchantApp;
import com.seektop.fund.model.GlPaymentMerchantaccount;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.seektop.constant.fund.Constants.FUND_COMMON_ON;

@Slf4j
@Component
public class GlRechargeHandlerManager {

    @Autowired
    private Map<String, GlPaymentRechargeHandler> glPaymentRechargeHandlerMap;

    @Autowired
    private Map<String, GlPaymentHandler> glPaymentHandlerMap;

    @Autowired
    private Map<String, GlRechargeCancelHandler> glRechargeCancelHandlerMap;

    @Autowired
    private GlPaymentMerchantAppBusiness glPaymentMerchantAppBusiness;

    @Autowired
    private GlPaymentMerchantAccountBusiness glPaymentMerchantAccountBusiness;

    @Resource
    private RedisService redisService;

    /**
     * 充值handler
     *
     * @param paymentMerchantaccount 入款商户信息
     * @return
     */
    public GlPaymentRechargeHandler getRechargeHandler(GlPaymentMerchantaccount paymentMerchantaccount) {
        paymentMerchantaccount = glPaymentMerchantAccountBusiness.findOne(paymentMerchantaccount.getMerchantId());
        if (null == paymentMerchantaccount) {
            return null;
        }
        // 若入款商户没有启用动态脚本，按原流程执行
        if (Objects.equals(FUND_COMMON_ON, paymentMerchantaccount.getEnableScript())) {
            // 若入款商户启用了动态脚本，调用GroovyScriptPayer
            return glPaymentRechargeHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glPaymentRechargeHandlerMap.get(paymentMerchantaccount.getChannelId().toString());
        }
    }

    /**
     * 充值handler
     *
     * @param paymentMerchantaccount 入款商户信息
     * @return
     */
    public GlPaymentHandler getPaymentHandler(GlPaymentMerchantaccount paymentMerchantaccount) {
        paymentMerchantaccount = glPaymentMerchantAccountBusiness.findOne(paymentMerchantaccount.getMerchantId());
        if (null == paymentMerchantaccount) {
            return null;
        }
        // 若入款商户没有启用动态脚本，按原流程执行
        if (Objects.equals(FUND_COMMON_ON, paymentMerchantaccount.getEnableScript())) {
            // 若入款商户启用了动态脚本，调用GroovyScriptPayer
            return glPaymentHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glPaymentHandlerMap.get(paymentMerchantaccount.getChannelId().toString());
        }
    }

    /**
     * 转账充值handler
     *
     * @param merchantaccount
     * @return
     */
    public GlPaymentRechargeHandler getTransferHandler(GlPaymentMerchantaccount merchantaccount) {
        merchantaccount = glPaymentMerchantAccountBusiness.findOne(merchantaccount.getMerchantId());
        if (null == merchantaccount) {
            return null;
        }
        // 若入款商户没有启用动态脚本，按原流程执行
        if (Objects.equals(FUND_COMMON_ON, merchantaccount.getEnableScript())) {
            // 若入款商户启用了动态脚本，调用GroovyScriptPayer
            return glPaymentRechargeHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glPaymentRechargeHandlerMap.get(merchantaccount.getChannelId().toString());
        }
    }

    /**
     * 撤销充值handler
     *
     * @param merchantaccount
     * @return
     */
    public GlRechargeCancelHandler getRechargeCancelHandler(GlPaymentMerchantaccount merchantaccount) {
        merchantaccount = glPaymentMerchantAccountBusiness.findOne(merchantaccount.getMerchantId());
        if (null == merchantaccount ||
                 (merchantaccount.getChannelId() != FundConstant.PaymentChannel.STORMPAY &&
                 merchantaccount.getChannelId() != FundConstant.PaymentChannel.C2CPay)) {
            return null;
        }
        // 若入款商户没有启用动态脚本，按原流程执行
        if (Objects.equals(FUND_COMMON_ON, merchantaccount.getEnableScript())) {
            // 若入款商户启用了动态脚本，调用GroovyScriptPayer
            return glRechargeCancelHandlerMap.get(FundConstant.PaymentChannel.GROOVYPAY + "");
        } else {
            return glRechargeCancelHandlerMap.get(merchantaccount.getChannelId().toString());
        }
    }

    /**
     * 商户是否支持订单查询
     *
     * @param channelId
     * @return
     */
    public Boolean supportQuery(Integer channelId) {
        if (channelId == null
                ||channelId.equals(FundConstant.PaymentChannel.MACHI)
                || channelId.equals(FundConstant.PaymentChannel.JUBAOFU)
                || channelId.equals(FundConstant.PaymentChannel.JUZIPAY)
                || channelId.equals(FundConstant.PaymentChannel.HONGBAOSHIPAY)
                || channelId.equals(FundConstant.PaymentChannel.TGPAY)
                || channelId.equals(FundConstant.PaymentChannel.UBPay)
                || channelId.equals(FundConstant.PaymentChannel.JSPPAY)
                || channelId.equals(FundConstant.PaymentChannel.JUBAOPEN)
                || channelId.equals(FundConstant.PaymentChannel.AIBEI)) {
            return false;
        }
        return true;
    }

    public Map<String, String> parseRechargeParams(Integer channelId, Map<String, String[]> params, String body, String headSign) throws Exception {
        Map<String, String> resMap = new HashMap<>();
        for (Map.Entry<String, String[]> each : params.entrySet()) {
            resMap.put(each.getKey(), each.getValue()[0]);
        }
        resMap.put("reqBody", body);
        resMap.put("headSign", headSign);
        return resMap;
    }

    private Map<String, String> parseParams(Map<String, String[]> params) throws Exception {
        Map<String, String> params1 = new HashMap<>();
        for (Iterator iter = params.keySet().iterator(); iter.hasNext(); ) {
            String name = (String) iter.next();
            String[] values = (String[]) params.get(name);
            String valueStr = "";
            for (int i = 0; i < values.length; i++) {
                valueStr = (i == values.length - 1) ? valueStr + values[i] : valueStr + values[i] + ",";
            }
            // 乱码解决，这段代码在出现乱码时使用。如果mysign和sign不相等也可以使用这段代码转化
            valueStr = new String(valueStr.getBytes("ISO-8859-1"), "UTF-8");
            params1.put(name, valueStr);
        }
        return params1;
    }

    /**
     * 根据充值方式和充值渠道设置  ShowType，Keyword 字段
     *
     * @param recharge
     * @return
     */
    public GlRechargeDO setShowType(GlRechargeDO recharge) {
        recharge.setShowType(0);
        // 转账订单的附言需要特殊处理
        if (recharge.getPaymentId() == FundConstant.PaymentType.BANKCARD_TRANSFER
                || recharge.getPaymentId() == FundConstant.PaymentType.ALI_TRANSFER
                || recharge.getPaymentId() == FundConstant.PaymentType.WECHAT_TRANSFER
                || recharge.getPaymentId() == FundConstant.PaymentType.UNION_TRANSFER) {
            recharge.setShowType(1);


            if (recharge.getKeyword() != null
                    && recharge.getKeyword().contains("||")) {
                recharge.setKeyword(recharge.getKeyword().split("\\|\\|")[1]);
            }
            if (recharge.getChannelId() == FundConstant.PaymentChannel.JINPAY
                    || recharge.getChannelId() == FundConstant.PaymentChannel.GBOPAY) {
                recharge.setKeyword(null);
            }

            //取脚本返回值
            GlPaymentMerchantaccount merchantAccount = glPaymentMerchantAccountBusiness.getMerchantAccountCache(recharge.getMerchantId());
            if (merchantAccount != null && (Objects.equals(FUND_COMMON_ON, merchantAccount.getEnableScript())
                    || merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay)) {
                GlPaymentHandler handler = getPaymentHandler(merchantAccount);
                if (handler != null) {
                    recharge.setShowType(handler.showType(merchantAccount, recharge.getPaymentId()));
                }

            }
        }
        return recharge;
    }

    /**
     * 充值 /payment/info 返回: 设置姓名、卡号、InnerPay
     *
     * @param payment
     */
    public void paymentSetting(GlPaymentResult payment) {
        if (payment.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY) {
            //虚拟币支付
            payment.getMerchantList().forEach(item -> {
                item.setProtocolMap(getProtocol(item.getChannelId()));
            });
        }

        //取脚本返回值
        payment.getMerchantList().forEach(item -> {
            GlPaymentMerchantaccount merchantAccount = glPaymentMerchantAccountBusiness.findById(item.getMerchantId());
            if (Objects.equals(FUND_COMMON_ON, merchantAccount.getEnableScript())
                || merchantAccount.getChannelId() == FundConstant.PaymentChannel.C2CPay) {
                GlPaymentHandler handler = getPaymentHandler(merchantAccount);
                if (null != handler) {
                    item.setNeedName(handler.needName(merchantAccount, payment.getPaymentId()));
                    item.setNeedCardNo(handler.needCard(merchantAccount, payment.getPaymentId()));
                    item.setInnerPay(handler.innerPay(merchantAccount, payment.getPaymentId()));
                }
            }
        });
    }

    /**
     * 获取渠道支持数字货币协议
     *
     * @param channelId
     */
    private Map<String, String> getProtocol(Integer channelId) {
        Map<String, String> protocolMap = new HashMap<>();

        if (channelId == FundConstant.PaymentChannel.TGPAY) {
            protocolMap = FundConstant.protocolMap;
            protocolMap.remove(FundConstant.ProtocolType.TRC20);
        }if (channelId == FundConstant.PaymentChannel.STPAYER) {
            protocolMap = FundConstant.protocolMap;
            protocolMap.remove(FundConstant.ProtocolType.OMNI);
        }else {
            protocolMap.put(FundConstant.ProtocolType.ERC20, FundConstant.protocolMap.get(FundConstant.ProtocolType.ERC20));
        }
        return protocolMap;
    }

    /**
     * 充值 /payment/info 返回: 设置姓名、卡号、InnerPay
     * @param payment
     */
        public void paymentSetting(PaymentResponse payment) {
        List<Integer> merchantIds = payment.getMerchants().stream().map(PaymentMerchantResponse::getMerchantId)
                .distinct().collect(Collectors.toList());
        if(!CollectionUtils.isEmpty(merchantIds)) {
            String ids = StringUtils.join(merchantIds, ",");
            List<GlPaymentMerchantaccount> list = glPaymentMerchantAccountBusiness.findByIds(ids);
            payment.getMerchants().forEach(m -> list.stream()
                    .filter(a -> FUND_COMMON_ON.equals(a.getEnableScript()))
                    .filter(a -> m.getMerchantId().equals(a.getMerchantId()))
                    .findFirst()
                    .ifPresent(a -> {
                        GlPaymentHandler handler = getPaymentHandler(a);
                        if (null != handler) {
                            m.setNeedName(handler.needName(a, payment.getPaymentId()));
                            m.setNeedCardNo(handler.needCard(a, payment.getPaymentId()));
                            m.setInnerPay(handler.innerPay(a, payment.getPaymentId()));
                        }
                    }));
            if (payment.getPaymentId() == FundConstant.PaymentType.DIGITAL_PAY) {
                //虚拟币支付
                payment.getMerchants().forEach(item -> {
                    item.setProtocolMap(getProtocol(item.getChannelId()));
                });
            }
        }
    }

    /**
     * InnerPay
     * @param paymentId
     * @param merchantAppId
     * @return
     */
    public boolean getInnerPay(Integer paymentId, Integer merchantAppId) {
        GlPaymentMerchantApp merchantApp = glPaymentMerchantAppBusiness.findById(merchantAppId);
        if (ObjectUtils.isEmpty(merchantApp))
            return false;
        GlPaymentMerchantaccount pmAccount = glPaymentMerchantAccountBusiness.findById(merchantApp.getMerchantId());
        if (ObjectUtils.isEmpty(pmAccount))
            return false;
        if (FUND_COMMON_ON.equals(pmAccount.getEnableScript())) {
            GlPaymentHandler handler = getPaymentHandler(pmAccount);
            if (null != handler) {
                return handler.innerPay(pmAccount, paymentId);
            }
        }
        return false;
    }

    /**
     * 充值submit提示
     */
    private static final String tpl = "<!DOCTYPE html>\n" +
            "<html lang=\"en\" style=\"margin: 0;padding: 0;\">\n" +
            "<head>\n" +
            "  <meta charset=\"UTF-8\">\n" +
            "  <meta name=\"viewport\" content=\"width=device-width, initial-scale=1.0\">\n" +
            "  <title>充值错误</title>\n" +
            "</head>\n" +
            "<body style=\"margin: 0;padding: 0;\">\n" +
            "  <div style=\"display: flex;height: 100vh;width: 100vw;align-items: center;justify-content: center;background: #e5e5e5;\">\n" +
            "    <div style=\"display: flex;flex-direction: column;align-items: center;text-align: center;\">\n" +
            "      <span style=\"font-weight: 900;\n" +
            "      font-size: 36px;\n" +
            "      line-height: 45px;color:#d1d1d1;\">NONE</span>\n" +
            "      <span style=\"font-weight: 500;\n" +
            "      font-size: 18px;\n" +
            "      line-height: 25px;color:#2b2b2b;\">创建订单失败,请更换充值金额或充值方式</span>\n" +
            "      <span style=\"font-size: 12px;width: 198px;\n" +
            "      line-height: 17px;color:#969696;\">%s</span>\n" +
            "    </div>\n" +
            "  </div>\n" +
            "</body>\n" +
            "</html>";

    /**
     * 是否是银行卡转账
     *
     * @param paymentId
     * @return
     */
    public boolean isBankcardTransfer(Integer paymentId) {
        if (paymentId == FundConstant.PaymentType.BANKCARD_TRANSFER) {
            return true;
        }
        return false;
    }

    /**
     * 是否是网银支付
     *
     * @param paymentId
     * @return
     */
    public boolean isOnlinePay(Integer paymentId) {
        return paymentId == FundConstant.PaymentType.ONLINE_PAY;
    }

    /**
     * 是否是极速支付
     *
     * @param paymentId
     * @return
     */
    public boolean isQuickPay(Integer paymentId) {
        if (paymentId == FundConstant.PaymentType.QUICK_WECHAT_PAY
                || paymentId == FundConstant.PaymentType.QUICK_ALI_PAY
                || paymentId == FundConstant.PaymentType.QUICK_QQ_PAY
                || paymentId == FundConstant.PaymentType.PHONECARD_PAY) {
            return true;
        }
        return false;
    }

    /**
     * 充值 跳转
     *
     * @param url
     * @return
     */
    public String buildRechargeJump(String url) {
        StringBuffer sb = new StringBuffer();
        sb.append("<html>");
        sb.append("<head>");
        sb.append("</head>");
        sb.append("<body>");
        sb.append("<form id='payform' action='" + url.substring(0, url.indexOf("?")) + "' method='post'>");
        String[] data = url.substring(url.indexOf("?") + 1).split("&");
        for (String d : data) {
            int idx = d.indexOf("=");
            if (idx > 0 && idx < d.length()) {
                sb.append("<input type='hidden' name='" + d.substring(0, idx) + "' value='" + d.substring(idx + 1)
                        + "' />");
            } else {
                sb.append("<input type='hidden' name='" + d + "' value='' />");
            }
        }
        sb.append("<form>");
        sb.append("<script>");
        sb.append("document.getElementById('payform').submit();");
        sb.append("</script>");
        sb.append("</body>");
        sb.append("</html>");
        return sb.toString();
    }


    /**
     * 拼接充值失败的返回值
     *
     * @param message
     * @return
     */
    public String rechargeSubmitFailedHtml(String message) {
        return String.format(tpl, message);
    }

    /**
     * 拼接充值成功的返回值
     *
     * @param rechargeResult
     * @throws IOException
     */
    public RechargeSubmitResponse rechargeSubmitSuccess(GlRechargeResult rechargeResult) {

        RechargeSubmitResponse rechargeSubmitResponse = new RechargeSubmitResponse();

        if (StringUtils.isNotEmpty(rechargeResult.getRedirectUrl())) {//跳转到商户支付地址
            rechargeSubmitResponse.setRedirect(true);
            rechargeSubmitResponse.setContent(rechargeResult.getRedirectUrl());
        } else if (StringUtils.isNotEmpty(rechargeResult.getMessage())) {//显示返回HTML内容
            rechargeSubmitResponse.setContent(rechargeResult.getMessage());
        } else {
            rechargeSubmitResponse.setContent(String.format(tpl, "输出三方充值请求"));
        }
        return rechargeSubmitResponse;
    }

    /**
     * 充值notify失败的response
     *
     * @return
     */
    public RechargeNotifyResponse rechargeFailNotifyResponse() {
        RechargeNotifyResponse rechargeNotifyResponse = new RechargeNotifyResponse();
        rechargeNotifyResponse.setRedirect(false);
        rechargeNotifyResponse.setContent("pay failed");
        return rechargeNotifyResponse;
    }

    /**
     * 充值notify成功的response
     *
     * @param channelId
     * @return
     */
    public RechargeNotifyResponse rechargeOKNotifyResponse(Integer channelId) {
        RechargeNotifyResponse rechargeNotifyResponse = new RechargeNotifyResponse();
        switch (channelId) {
            case FundConstant.PaymentChannel.HUIFUBAO:
            case FundConstant.PaymentChannel.SITONGPAY:
            case FundConstant.PaymentChannel.XINPAY:
            case FundConstant.PaymentChannel.HAOFU_PAY_117:
            case FundConstant.PaymentChannel.PONY_PAY:
            case FundConstant.PaymentChannel.AIBEI:
            case FundConstant.PaymentChannel.XINHUIPAY:
            case FundConstant.PaymentChannel.RUIFUPAY:
            case FundConstant.PaymentChannel.BEILEIPAY:
            case FundConstant.PaymentChannel.YINSHANFUPAY:
            case FundConstant.PaymentChannel.JINTAOPAY:
            case FundConstant.PaymentChannel.YIXUNPAY:
            case FundConstant.PaymentChannel.RONGHEPAY:
            case FundConstant.PaymentChannel.JUHEPAY:
            case FundConstant.PaymentChannel.JUHEWEIXINPAY:
            case FundConstant.PaymentChannel.HENGXINGPAY:
            case FundConstant.PaymentChannel.TTPAY:  //泰坦支付
            case FundConstant.PaymentChannel.HUOYIPAY:  //泰坦支付
                rechargeNotifyResponse.setContent("SUCCESS");
                return rechargeNotifyResponse;
            case FundConstant.PaymentChannel.ONEGOPAY:
            case FundConstant.PaymentChannel.XINDUOBAO:
            case FundConstant.PaymentChannel.HIPAY:
            case FundConstant.PaymentChannel.CFPAY:
            case FundConstant.PaymentChannel.ANTWITHDRAWPAY:
                rechargeNotifyResponse.setContent("ok");
                return rechargeNotifyResponse;
            case FundConstant.PaymentChannel.JUBAOFU:
            case FundConstant.PaymentChannel.YHPAY:
            case FundConstant.PaymentChannel.UPAY:
            case FundConstant.PaymentChannel.P168_PAY:
            case FundConstant.PaymentChannel.HOTPOTPAY:
            case FundConstant.PaymentChannel.ZHIHUIFU:
            case FundConstant.PaymentChannel.YIZHIFUPAY:
            case FundConstant.PaymentChannel.SHUBAOLAIPAY:
            case FundConstant.PaymentChannel.STPAYER:
            case FundConstant.PaymentChannel.YITUPAY:
            case FundConstant.PaymentChannel.WANDEPAY:
            case FundConstant.PaymentChannel.DIORPAY:
            case FundConstant.PaymentChannel.ZHONGQIFUPAY:
                rechargeNotifyResponse.setContent("OK");
                return rechargeNotifyResponse;
            case FundConstant.PaymentChannel.SAVEPAY:
            case FundConstant.PaymentChannel.FENBEIFUPAY:
                rechargeNotifyResponse.setContent("opstate=0");
                return rechargeNotifyResponse;
            case FundConstant.PaymentChannel.MACHI:
            case FundConstant.PaymentChannel.LARGEPAY:
            case FundConstant.PaymentChannel.GOODPAY:
                rechargeNotifyResponse.setContent("200");
                return rechargeNotifyResponse;
            case FundConstant.PaymentChannel.YANGGUANG:
                rechargeNotifyResponse.setContent("SUC");
                return rechargeNotifyResponse;
            default:
                rechargeNotifyResponse.setContent("success");
                return rechargeNotifyResponse;
        }
    }

}

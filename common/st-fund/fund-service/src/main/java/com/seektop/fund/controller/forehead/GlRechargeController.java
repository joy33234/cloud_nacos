package com.seektop.fund.controller.forehead;

import com.alibaba.fastjson.JSON;
import com.seektop.common.local.compoent.parse.LocalCommonParser;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.common.C2CRechargeOrderMatchResult;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.result.GlPaymentMerchantResult;
import com.seektop.fund.controller.backend.result.GlPaymentNewResult;
import com.seektop.fund.controller.backend.result.GlPaymentResult;
import com.seektop.fund.controller.forehead.param.recharge.*;
import com.seektop.fund.controller.forehead.result.RechargeAmountResult;
import com.seektop.fund.controller.forehead.result.RechargeInfoResult;
import com.seektop.fund.controller.forehead.result.RechargeSettingResult;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.payment.*;
import com.seektop.fund.payment.niubipay.PaymentInfo;
import com.seektop.fund.payment.yixunpay.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.NotBlank;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 用户充值相关接口
 */
@Slf4j
@RestController
@RequestMapping("/forehead/fund/recharge")
public class GlRechargeController extends GlFundForeheadBaseController {

    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;

    @Resource
    private GlRechargeHandlerManager glRechargeHandlerManager;
    @Resource
    private RedisService redisService;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Autowired
    private GlRechargeBusiness rechargeBusiness;


    /**
     * 清空存款人姓名和卡号
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/clear/name")
    public Result clearName(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        rechargeHandler.clearName(userDO);
        return Result.genSuccessResult();
    }

    /**
     * 获取用户最后一条充值记录-新版本
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/status/v2", produces = "application/json;charset=utf-8")
    public Result statusNew(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, ParamBaseDO paramBaseDO) throws GlobalException {
        RechargeInfoResult result = rechargeHandler.getLastRecharge(userDO);
        result.setPaymentName(FundLanguageUtils.getPaymentName(result.getPaymentId(), result.getPaymentName(), paramBaseDO.getLanguage()));
        return Result.genSuccessResult(result);
    }

    /**
     * 获取用户最后一条充值记录
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/status", produces = "application/json;charset=utf-8")
    public Result status(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, ParamBaseDO paramBaseDO) throws GlobalException {
        RechargeInfoResult result = rechargeHandler.getLastRecharge(userDO);
        result.setPaymentName(FundLanguageUtils.getPaymentName(result.getPaymentId(), result.getPaymentName(), paramBaseDO.getLanguage()));
        return Result.genSuccessResult(result);
    }

    /**
     * 用户撤销充值订单
     *
     * @param orderId
     * @param userDO
     * @return
     */
    @PostMapping(value = "/cancel", produces = "application/json;charset=utf-8")
    public Result cancel(@NotBlank String orderId, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        rechargeHandler.doCancel(userDO, orderId);
        // 是否弹窗显示待客充值帮助提示
        boolean showTip = isShowTip(userDO);
        Map<String, Boolean> result = new HashMap<>();
        result.put("showTip", showTip);
        return Result.genSuccessResult(result);
    }

    private boolean isShowTip(GlUserDO userDO) {
        boolean showTip = false;
        String key = RedisKeyHelper.USER_CANCEL_RECHARGE_COUNT + userDO.getId();
        long count = redisService.incrBy(key, 1);
        redisService.setTTL(key, 60 * 10);
        //开关是否打开
        Object value = redisService.get(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG);

        //层级开关是否打开
        if (value != null) {
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
            boolean levenOpen = redisService.exists(RedisKeyHelper.AGENCY_USER_LEVEL + userlevel.getLevelId());
            if (levenOpen && count >= 3) {
                showTip = true;
            }
        }
        return showTip;
    }

    /**
     * 获取用户有效充值方式列表
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     */
    @PostMapping(value = "/payment/info/v2", produces = "application/json;charset=utf-8")
    public Result paymentInfoNew(@Validated RechargePaymentInfoDO paymentInfoDO, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        GlPaymentNewResult results = rechargeHandler.paymentInfo(paymentInfoDO, userDO);
        if(!CollectionUtils.isEmpty(results.getLarge())) {
            for (GlPaymentResult glPaymentResult : results.getLarge()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage())
                );
            }
        }
        if(!CollectionUtils.isEmpty(results.getNormal())) {
            for (GlPaymentResult glPaymentResult : results.getNormal()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage()));
            }
        }
        return Result.genSuccessResult(results);
    }

    /**
     * 获取用户有效充值方式列表
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     */
    @PostMapping(value = "/payment/info", produces = "application/json;charset=utf-8")
    public Result paymentInfo(@Validated RechargePaymentInfoDO paymentInfoDO, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        GlPaymentNewResult results = rechargeHandler.paymentInfo(paymentInfoDO, userDO);
        if(!CollectionUtils.isEmpty(results.getLarge())) {
            for (GlPaymentResult glPaymentResult : results.getLarge()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage()));
            }
        }
        if(!CollectionUtils.isEmpty(results.getNormal())) {
            for (GlPaymentResult glPaymentResult : results.getNormal()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage()));
            }
        }
        return Result.genSuccessResult(results);
    }

    /**
     * 获取代理有效充值方式列表-固定查询大额渠道-银行卡转账充值方式
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     */
    @PostMapping(value = "/proxy/payment/info", produces = "application/json;charset=utf-8")
    public Result proxyPaymentInfo(@Validated RechargePaymentInfoDO paymentInfoDO, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        GlPaymentNewResult results = rechargeHandler.proxyPaymentInfo(paymentInfoDO, userDO);
        if(!CollectionUtils.isEmpty(results.getLarge())) {
            for (GlPaymentResult glPaymentResult : results.getLarge()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage()));
            }
        }
        if(!CollectionUtils.isEmpty(results.getNormal())) {
            for (GlPaymentResult glPaymentResult : results.getNormal()) {
                glPaymentResult.setPaymentName(FundLanguageUtils.getPaymentName(glPaymentResult.getPaymentId(), glPaymentResult.getPaymentName(), paymentInfoDO.getLanguage()));
            }
        }
        return Result.genSuccessResult(results);
    }


    /**
     * 充值界面快捷金额以及通道设置
     *
     * @param userDO
     * @param paramBaseDO
     * @return
     */
    @PostMapping(value = "/fastAmount/list", produces = "application/json;charset=utf-8")
    public Result getFastAmountList(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, @Validated ParamBaseDO paramBaseDO) {
        RechargeSettingResult result = rechargeHandler.getFastAmountList(paramBaseDO, userDO);
        return Result.genSuccessResult(result);
    }

    /**
     * 提交充值-跳转商户充值界面
     *
     * @param rechargeSubmitDO
     * @param userDO
     * @param request
     * @param response
     */
    @RequestMapping(value = "/do/submit", method = {RequestMethod.GET, RequestMethod.POST})
    public void doSubmit(@Validated RechargeSubmitDO rechargeSubmitDO, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO,
                         HttpServletRequest request,
                         HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            GlRechargeResult rechargeResult = rechargeHandler.doRechargeSubmit(rechargeSubmitDO, userDO, request);

            if (null != rechargeResult) {
                //充值失败
                if (rechargeResult.getErrorCode() != FundConstant.RechargeErrorCode.NORMAL) {
                    String message = LanguageLocalParser.key(FundLanguageDicEnum.RECHARGE_CREATE_ORDER_ERROR)
                            .withParam("" + rechargeResult.getErrorCode())
                            .withDefaultValue(rechargeResult.getErrorMsg())
                            .parse(rechargeSubmitDO.getLanguage());
                    out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml(message));
                    return;
                } else {
                    log.info("Recharge_doSubmit:{}", JSON.toJSONString(rechargeResult));
                    RechargeSubmitResponse rechargeSubmitResponse = glRechargeHandlerManager.rechargeSubmitSuccess(rechargeResult);
                    response.setContentType(rechargeSubmitResponse.getContentType());
                    //跳转商户请求地址
                    if (rechargeSubmitResponse.isRedirect()) {
                        response.sendRedirect(rechargeSubmitResponse.getContent());
                    } else {
                        //Form表单请求或者显示收款二维码
                        out.print(rechargeSubmitResponse.getContent());
                    }
                    return;
                }
            }
        } catch (Exception e) {
            if (out != null) {
                out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml("订单创建失败"));
            }
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * 提交充值-显示平台收款界面(收款账户信息)
     *
     * @param rechargeTransferDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    @RequestMapping(value = "/do/transfer", method = {RequestMethod.POST, RequestMethod.GET})
    public Result
    doTransfer(@Validated RechargeTransferDO rechargeTransferDO, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO,
               HttpServletRequest request) throws GlobalException {
        GlRechargeTransferResult result = rechargeHandler.doRechargeTransfer(rechargeTransferDO, userDO, request);

        if (StringUtils.isEmpty(result.getErrorMsg())) {
            result.setPaymentName(FundLanguageUtils.getPaymentName(rechargeTransferDO.getPaymentId(), result.getPaymentName(), rechargeTransferDO.getLanguage()));
            return Result.genSuccessResult(result);
        } else {
            return Result.genFailResult(ResultCode.RECHARGE_SUBMIT_ERROR.getCode(),LanguageLocalParser.key(FundLanguageDicEnum.RECHARGE_CREATE_ORDER_ERROR)
                    .withParam("" + result.getErrorCode())
                    .withDefaultValue( result.getErrorMsg())
                    .parse(rechargeTransferDO.getLanguage()));
        }

    }


    /**
     * 充值订单回调接口
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notify/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void notify(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                       HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            RechargeNotifyResponse notify = rechargeHandler.notify(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("GlRecharge_Notify_Error", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }


    /**
     * 风云聚合回调通知接口
     *
     * @param merchantId
     * @param request
     * @param response
     */
    @RequestMapping(value = "/notifyForFengYun/{merchantId}", method = {RequestMethod.POST, RequestMethod.GET})
    @ResponseStatus(HttpStatus.OK)
    public void notifyForStormPay(@PathVariable(value = "merchantId") Integer merchantId, HttpServletRequest request,
                                  HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            RechargeNotifyResponse notify = rechargeHandler.notifyForStormPay(merchantId, request);
            out.write(notify.getContent());
            out.flush();
            return;
        } catch (Exception e) {
            log.error("notifyForTransfer_error", e);
            return;
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }


    /**
     * 有特殊域名要求的充值渠道，做一次跳转
     */
    @RequestMapping(value = "/jump", method = {RequestMethod.POST, RequestMethod.GET})
    public void jump(@RequestParam String url, HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            String jumpForm = glRechargeHandlerManager.buildRechargeJump(url);
            out.print(jumpForm);
        } catch (IOException e) {
            log.error(e.getMessage(), e);
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * 判断金额是否被占用，返回建议金额
     *
     * @param form
     * @return
     */
    @PostMapping(value = "/amount", produces = "application/json;charset=utf-8")
    public Result getAmount(@Validated RechargeAmountForm form) {
        RechargeAmountResult result = rechargeBusiness.getAmount(form);
        return Result.genSuccessResult(result);
    }


    /**
     * 获取渠道支付方式
     *
     * @param paymentDo
     */
    @RequestMapping(value = "/payments", method = {RequestMethod.GET, RequestMethod.POST})
    public Result payments(@Validated RechargePaymentDo paymentDo) throws GlobalException {
        PaymentInfo info = rechargeHandler.payments(paymentDo);
        if (StringUtils.isEmpty(info.getErrosMessage())) {
            return Result.genSuccessResult(info);
        } else {
            final Result result = Result.genFailResult(ResultCode.RECHARGE_SUBMIT_ERROR.getCode(), info.getErrosMessage());
            result.setKeyConfig(info.getLocalKeyConfig());
            return result;
        }
    }

    /**
     * 查询充值订单进度
     *
     * @param orderId
     */
    @RequestMapping(value = "/order/status", method = {RequestMethod.GET, RequestMethod.POST})
    public Result orderStatus(@RequestParam String orderId) {
        String redirectUrl = redisService.get(RedisKeyHelper.RECHARGE_ORDER_STATUS + orderId);
        if (StringUtils.isEmpty(redirectUrl)) {
            final Result result = Result.genFailResult(ResultCode.RECHARGE_SUBMIT_ERROR.getCode(), "充值订单过期或不存在");
            result.setKeyConfig(FundLanguageMvcEnum.RECHARGE_ORDER_EXPIRED_OR_NOT_EXIST);
            return result;
        } else {
            return Result.genSuccessResult(redirectUrl);
        }
    }


    /**
     * 根据充值金额匹配撮合系统订单
     *
     * @param form
     * @return
     */
    @PostMapping(value = "/c2c/amount", produces = "application/json;charset=utf-8")
    public Result getC2CAmount(@Validated RechargeAmountForm form, @ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO) throws GlobalException {
        C2CRechargeOrderMatchResult result = rechargeBusiness.getC2CAmount(form, userDO);
        return Result.genSuccessResult(result);
    }
}

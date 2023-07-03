package com.seektop.fund.controller.backend;

import com.alibaba.fastjson.JSON;
import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.constant.FundConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlRechargeManageBusiness;
import com.seektop.fund.business.recharge.GlRechargeSuccessApproveBusiness;
import com.seektop.fund.business.recharge.GlRechargeSuccessRequestBusiness;
import com.seektop.fund.business.recharge.GlRechargeTransactionBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.param.recharge.RechargeApproveDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeCreateDO;
import com.seektop.fund.controller.backend.param.recharge.RechargeRequestDO;
import com.seektop.fund.controller.backend.param.recharge.RequestRechargeRejectDO;
import com.seektop.fund.controller.backend.result.recharge.RechargeDetailResult;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.enums.FundLanguageMvcEnum;
import com.seektop.fund.handler.RechargeHandler;
import com.seektop.fund.payment.GlRechargeHandlerManager;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.GlRechargeTransferResult;
import com.seektop.fund.payment.RechargeSubmitResponse;
import com.seektop.fund.payment.yixunpay.StringUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;


@Slf4j
@RestController
@RequestMapping("/manage/fund/recharge")
public class RechargeManageController extends FundBackendBaseController {

    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;

    @Resource
    private GlRechargeHandlerManager glRechargeHandlerManager;

    @Autowired
    private GlRechargeManageBusiness glRechargeManageBusiness;

    @Autowired
    private GlRechargeSuccessRequestBusiness glRechargeSuccessRequestBusiness;

    @Autowired
    private GlRechargeSuccessApproveBusiness glRechargeSuccessApproveBusiness;

    @Resource
    private GlRechargeTransactionBusiness glRechargeTransactionBusiness;

    /**
     * 同步订单状态
     *
     * @param orderId
     * @param adminDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/submit/synchronize", produces = "application/json;charset=utf-8")
    public Result submitSynchronize(@RequestParam String orderId, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO) {
        return rechargeHandler.submitSynchronize(adminDO, orderId);
    }

    /**
     * 查询充值订单详情
     *
     * @param orderId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/load/order/detail", produces = "application/json;charset=utf-8")
    public Result loadDetail(@RequestParam String orderId, @ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, ManageParamBaseDO manageParamBaseDO) throws GlobalException {
        RechargeDetailResult detailDto = glRechargeManageBusiness.loadDetail(orderId, adminDO);
        if (ObjectUtils.isNotEmpty(detailDto.getRecharge())) {
            detailDto.getRecharge().setPaymentName(FundLanguageUtils.getPaymentName(detailDto.getRecharge().getPaymentId(), detailDto.getRecharge().getPaymentName(), manageParamBaseDO.getLanguage()));
        }
        return Result.genSuccessResult(detailDto);
    }


    /**
     * 充值订单-人工拒绝补单
     *
     * @param adminDO
     * @param rechargeRejectDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/success/reject", produces = "application/json;charset=utf-8")
    public Result rejectRequest(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RequestRechargeRejectDO rechargeRejectDO) throws GlobalException {
        glRechargeTransactionBusiness.rejectRequest(adminDO, rechargeRejectDO);
        return Result.genSuccessResult();
    }

    /**
     * 充值订单-申请补单审核
     *
     * @param adminDO
     * @param requestDO
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/success/requestReCharge", produces = "application/json;charset=utf-8")
    public Result requestSuccess(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargeRequestDO requestDO) throws GlobalException {
        glRechargeManageBusiness.requestRecharge(adminDO, requestDO);
        return Result.genSuccessResult(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_ORDER_REQ_SUCCESS).withDefaultValue("补单申请成功").parse(requestDO.getLanguage()));

    }

    /**
     * 充值补单审核
     */
    @PostMapping(value = "/success/approve", produces = "application/json;charset=utf-8")
    public Result successApprove(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, @Valid RechargeApproveDO approveDO, @RequestHeader(defaultValue = "50") Integer systemId) throws GlobalException {
        glRechargeManageBusiness.requestRechargeApprove(adminDO, approveDO, systemId);
        return Result.genSuccessResult();
    }

    /**
     * 调用商户接口查询订单
     *
     * @param orderId
     * @return
     * @throws GlobalException
     */
    @PostMapping(value = "/query/status", produces = "application/json;charset=utf-8")
    public Result queryStatus(@RequestParam String orderId) throws GlobalException {
        return Result.genSuccessResult(glRechargeManageBusiness.queryRechargeOrder(orderId));
    }

    /**
     * 后台创建订单
     *
     * @param adminDO
     * @param createDO
     * @param request
     * @param response
     * @return
     */
    @RequestMapping(value = "/create/recharge", method = {RequestMethod.GET, RequestMethod.POST})
    public void createRecharge(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargeCreateDO createDO,
                               HttpServletRequest request,
                               HttpServletResponse response) {
        response.setContentType("text/html;charset=UTF-8");
        PrintWriter out = null;
        try {
            out = response.getWriter();
            GlRechargeResult rechargeResult = rechargeHandler.doRechargeForBackend(createDO, adminDO, request);
            log.info("rechargeResult : {}", JSON.toJSONString(rechargeResult));
            if (null != rechargeResult) {
                //充值失败
                if (rechargeResult.getErrorCode() != FundConstant.RechargeErrorCode.NORMAL) {
                    String message = LanguageLocalParser.key(FundLanguageDicEnum.RECHARGE_CREATE_ORDER_ERROR)
                            .withParam("" + rechargeResult.getErrorCode())
                            .withDefaultValue(rechargeResult.getErrorMsg())
                            .parse(createDO.getLanguage());
                    out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml(message));
                    return;
                }
                //充值请求成功
                if (rechargeResult.getErrorCode() == 0) {
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
                out.print(glRechargeHandlerManager.rechargeSubmitFailedHtml(LanguageLocalParser.key(FundLanguageMvcEnum.RECHARGE_CREATE_ORDER_ERROR)
                        .withDefaultValue("订单创建失败").parse(createDO.getLanguage())));
            }
        } finally {
            if (out != null) {
                out.flush();
                out.close();
            }
        }
    }

    /**
     * 后台创建充值订单-Tranfer
     *
     * @param adminDO
     * @param createDO
     * @param request
     * @param response
     */
    @RequestMapping(value = "/create/transfer", method = {RequestMethod.GET, RequestMethod.POST})
    public Result createRechargeForTransfer(@ModelAttribute(value = "adminInfo", binding = false) GlAdminDO adminDO, @Validated RechargeCreateDO createDO,
                                            HttpServletRequest request,
                                            HttpServletResponse response) throws GlobalException {
        GlRechargeTransferResult result = rechargeHandler.doRechargeTransferForBackend(createDO, adminDO, request);
        if (StringUtils.isEmpty(result.getErrorMsg())) {
            return Result.genSuccessResult(result);
        } else {
            return Result.genFailResult(ResultCode.RECHARGE_SUBMIT_ERROR.getCode(), result.getErrorMsg());
        }
    }

    /**
     * 查询补单申请人列表
     * @return
     */
    @RequestMapping(value = "/list/applicant", produces = "application/json;charset=utf-8")
    public Result listFirst() {
        List<String> applicants = glRechargeSuccessRequestBusiness.findAllApplicant();
        return Result.genSuccessResult(applicants);
    }

    /**
     * 查询补单审核人列表
     * @return
     */
    @RequestMapping(value = "/list/auditor", produces = "application/json;charset=utf-8")
    public Result listSecond() {
        List<String> auditors = glRechargeSuccessApproveBusiness.findAllAuditor();
        return Result.genSuccessResult(auditors);
    }
}

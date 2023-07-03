package com.seektop.fund.controller.partner;

import com.seektop.common.rest.Result;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.partner.param.AgencyForm;
import com.seektop.fund.controller.partner.param.OrderForm;
import com.seektop.fund.controller.partner.param.OrderQueryForm;
import com.seektop.fund.controller.partner.param.PaymentForm;
import com.seektop.fund.handler.RechargeInsteadOrderHandler;
import com.seektop.fund.handler.RechargeInsteadPaymentHandler;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.ObjectUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 提供第三方的代客充值接口
 */
@Slf4j
@RestController
@RequestMapping(value = "/partner/fund/recharge/instead", method = RequestMethod.POST, produces = "application/json;charset=utf-8")
public class RechargeInsteadController {

    @Autowired
    private RechargeInsteadPaymentHandler paymentHandler;
    @Autowired
    private RechargeInsteadOrderHandler orderHandler;

    /**
     * 查询可用的支付方式
     *
     * @param form
     * @return
     */
    @RequestMapping("/find/payments")
    public Result findPayments(@Validated PaymentForm form) {
        return paymentHandler.getPayments(form);
    }

    /**
     * 查询用户的代客充值码列表
     *
     * @param form
     * @return
     */
    @RequestMapping("/find/agency/codes")
    public Result findAgencyCodes(@Validated AgencyForm form) {
        return orderHandler.findAgencyCodes(form);
    }

    /**
     * 创建订单
     *
     * @param form
     * @param request
     * @return
     */
    @RequestMapping("/order")
    public Result order(@Validated OrderForm form, HttpServletRequest request) {
        Result result;
        try {
            result = orderHandler.order(form, request);
        }
        catch (GlobalException e) {
            log.info("代客充值创建订单异常", e);
            result = getFailResult(e);
        }
        return result;
    }

    /**
     * 查询订单
     *
     * @param form
     * @return
     */
    @RequestMapping("find/order")
    public Result findOrder(@Validated OrderQueryForm form) {
        Result result;
        try {
            result = orderHandler.findOrder(form);
        }
        catch (Exception e) {
            log.info("代客充值查询订单异常", e);
            result = Result.newBuilder().fail(ResultCode.SERVER_ERROR).build();
        }
        return result;
    }

    private Result getFailResult(GlobalException e){
        Integer code = ObjectUtils.isEmpty(e.getCode()) ? ResultCode.SERVER_ERROR.getCode() : e.getCode();
        String message = StringUtils.isBlank(e.getExtraMessage()) ? ResultCode.SERVER_ERROR.getMessage() : e.getExtraMessage();
        return Result.newBuilder().fail().setCode(code).setMessage(message).build();
    }
}

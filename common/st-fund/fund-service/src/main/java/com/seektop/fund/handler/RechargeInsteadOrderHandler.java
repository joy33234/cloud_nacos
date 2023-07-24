package com.seektop.fund.handler;

import com.alibaba.fastjson.JSON;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.AgencyRechargeBusiness;
import com.seektop.fund.business.recharge.GlRechargeBusiness;
import com.seektop.fund.controller.forehead.param.recharge.RechargeSubmitDO;
import com.seektop.fund.controller.forehead.param.recharge.RechargeTransferDO;
import com.seektop.fund.controller.partner.param.AgencyForm;
import com.seektop.fund.controller.partner.param.OrderForm;
import com.seektop.fund.controller.partner.param.OrderQueryForm;
import com.seektop.fund.controller.partner.result.OrderInfoResponse;
import com.seektop.fund.controller.partner.result.OrderResponse;
import com.seektop.fund.controller.partner.result.OrderStatusResponse;
import com.seektop.fund.controller.partner.result.OrderTextResponse;
import com.seektop.fund.handler.event.OrderNotifyDto;
import com.seektop.fund.model.AgencyRecharge;
import com.seektop.fund.model.GlRecharge;
import com.seektop.fund.payment.GlRechargeHandlerManager;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.GlRechargeTransferResult;
import com.seektop.fund.payment.RechargeSubmitResponse;
import com.seektop.fund.vo.AgencyRechargeQueryDto;
import com.seektop.fund.vo.AgencyRechargeVO;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
public class RechargeInsteadOrderHandler {

    @DubboReference(timeout = 3000)
    private GlUserService userService;
    @Resource(name = "rechargeHandler")
    private RechargeHandler rechargeHandler;
    @Resource
    private AgencyRechargeBusiness agencyRechargeBusiness;
    @Resource
    private GlRechargeHandlerManager rechargeHandlerManager;
    @Resource
    private GlRechargeBusiness rechargeBusiness;
    @Autowired
    private RedisService redisService;

    /**
     * 创建订单
     * @param form
     * @return
     */
    public Result order(OrderForm form, HttpServletRequest request) throws GlobalException {
        log.info("代客充值接口参数:{}", form);
        RPCResponse<GlUserDO> rpcResponse = userService.findById(form.getUserId());
        if (RPCResponseUtils.isFail(rpcResponse) || ObjectUtils.isEmpty(rpcResponse.getData())) {
            return Result.newBuilder().fail(ResultCode.USER_NAME_NOT_EXIST).build();
        }
        AgencyRecharge agencyRecharge = agencyRechargeBusiness.findById(form.getAgencyId());
        if (agencyRecharge == null) {
            return Result.newBuilder().fail(ResultCode.PARAM_ERROR).setMessage("代客充值订单不存在").build();
        }
        if (StringUtils.isNotBlank(agencyRecharge.getOrderId())) {
            return Result.newBuilder().fail(ResultCode.PARAM_ERROR).setMessage("代客充值订单已提交,请勿重复提交").build();
        }
        GlUserDO user = rpcResponse.getData();
        // 操作人信息
        GlAdminDO admin = new GlAdminDO();
        admin.setUserId(-1);
        admin.setUsername(String.format("%s（%s）", form.getAdminUsername(), form.getAdminUserId()));

        boolean innerPay = rechargeHandlerManager.getInnerPay(form.getPaymentId(), form.getMerchantAppId());
        Result result;
        if (innerPay) { // 转账
            result = doTransfer(form, user, admin, request);
        }
        else { // 返回URL或HTML
            result = doSubmit(form, user, admin, request);
        }
        return result;
    }

    /**
     * 查询订单明细
     * @param form
     * @return
     */
    public Result findOrder(OrderQueryForm form) {
        RPCResponse<GlUserDO> rpcResponse = userService.findById(form.getUserId());
        if (RPCResponseUtils.isFail(rpcResponse) || ObjectUtils.isEmpty(rpcResponse.getData())) {
            return Result.newBuilder().fail(ResultCode.USER_NAME_NOT_EXIST).build();
        }
        Optional<GlRecharge> optional = Optional.ofNullable(rechargeBusiness.findById(form.getOrderId()));
        if (!optional.isPresent() || !form.getUserId().equals(optional.get().getUserId())) {
            return Result.newBuilder().fail(ResultCode.PARAM_ERROR).setMessage("用户订单不存在").build();
        }
        OrderStatusResponse statusResponse = new OrderStatusResponse();
        optional.ifPresent(r -> {
            BeanUtils.copyProperties(r, statusResponse);
            statusResponse.setPayTime(r.getLastUpdate());
        });
        return Result.genSuccessResult(statusResponse);
    }

    /**
     * 查询代客充值码
     * @param form
     * @return
     */
    public Result findAgencyCodes(AgencyForm form){
        RPCResponse<GlUserDO> rpcResponse = userService.findById(form.getUserId());
        if (RPCResponseUtils.isFail(rpcResponse) || ObjectUtils.isEmpty(rpcResponse.getData())) {
            return Result.newBuilder().fail(ResultCode.USER_NAME_NOT_EXIST).build();
        }
        AgencyRechargeQueryDto queryDto = new AgencyRechargeQueryDto();
        BeanUtils.copyProperties(form, queryDto);
        List<AgencyRechargeVO> list = agencyRechargeBusiness.findCodes(queryDto);
        log.info("查询代客充值码参数：{}，结果：{}", queryDto, list);
        return Result.genSuccessResult(list);
    }

    /**
     * 转账
     * @param form
     * @param user
     * @param admin
     * @param request
     * @return
     * @throws GlobalException
     */
    private Result doTransfer(OrderForm form, GlUserDO user, GlAdminDO admin,
                              HttpServletRequest request) throws GlobalException {
        RechargeTransferDO rtd = new RechargeTransferDO();
        BeanUtils.copyProperties(form, rtd);
        rtd.setPayType(FundConstant.AGENT_TYPE);
        rtd.setLimitType(FundConstant.PaymentCache.NORMAL);
        GlRechargeTransferResult transferResult = rechargeHandler.doRechargeTransfer(rtd, user, request);
        agencyRechargeBusiness.doAgencyRechargeSubmit(transferResult.getTradeNo(), user, rtd.getAgencyId(), admin);
        log.info("============ doTransfer.rechargeResult:{}", JSON.toJSONString(transferResult));

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setInnerPay(true);
        Result result;
        if (FundConstant.RechargeErrorCode.NORMAL == transferResult.getErrorCode()) {
            OrderInfoResponse info = new OrderInfoResponse();
            BeanUtils.copyProperties(transferResult, info);
            orderResponse.setInfo(info);
            info.setOrderId(transferResult.getTradeNo());
            result = Result.genSuccessResult(orderResponse);
            setNotifyUrl(info.getOrderId(), form.getNotifyUrl());
        }
        else { // 失败
            result = Result.newBuilder().fail()
                    .setCode(ResultCode.TRANSFER_ERROR.getCode())
                    .setMessage(StringUtils.isBlank(transferResult.getErrorMsg()) ?
                            ResultCode.TRANSFER_ERROR.getMessage() : transferResult.getErrorMsg())
                    .build();
        }
        return result;
    }

    /**
     * 返回URL或HTML
     * @param form
     * @param user
     * @param admin
     * @param request
     * @return
     * @throws GlobalException
     */
    private Result doSubmit(OrderForm form, GlUserDO user, GlAdminDO admin,
                            HttpServletRequest request) throws GlobalException {
        RechargeSubmitDO rechargeSubmitDO = new RechargeSubmitDO();
        BeanUtils.copyProperties(form, rechargeSubmitDO);
        // 生成充值结果
        rechargeSubmitDO.setPayType(FundConstant.AGENT_TYPE);
        rechargeSubmitDO.setLimitType(FundConstant.PaymentCache.NORMAL);
        GlRechargeResult rechargeResult = rechargeHandler.doRechargeSubmit(rechargeSubmitDO, user, request);
        // 充值记录入库&上报
        agencyRechargeBusiness.doAgencyRechargeSubmit(rechargeResult.getTradeId(), user, rechargeSubmitDO.getAgencyId(), admin);
        log.info("============ doSubmit.rechargeResult:{}", JSON.toJSONString(rechargeResult));

        OrderResponse orderResponse = new OrderResponse();
        orderResponse.setInnerPay(false);
        Result result;
        if (FundConstant.RechargeErrorCode.NORMAL == rechargeResult.getErrorCode()) {
            RechargeSubmitResponse rechargeSubmitResponse = rechargeHandlerManager.rechargeSubmitSuccess(rechargeResult);
            OrderTextResponse text = new OrderTextResponse();
            text.setOrderId(rechargeResult.getTradeId());
            text.setAmount(rechargeResult.getAmount());
            text.setIsUrl(rechargeSubmitResponse.isRedirect());
            text.setContent(rechargeSubmitResponse.getContent());
            orderResponse.setText(text);
            result = Result.genSuccessResult(orderResponse);
            setNotifyUrl(text.getOrderId(), form.getNotifyUrl());
        }
        else { //充值失败
            result = Result.newBuilder().fail()
                    .setCode(ResultCode.RECHARGE_SUBMIT_ERROR.getCode())
                    .setMessage(StringUtils.isBlank(rechargeResult.getErrorMsg()) ?
                            ResultCode.RECHARGE_SUBMIT_ERROR.getMessage() : rechargeResult.getErrorMsg())
                    .build();
        }
        return result;
    }

    /**
     * 缓存回调地址
     * @param orderId
     * @param url
     */
    private void setNotifyUrl(String orderId, String url) {
        if (StringUtils.isBlank(url))
            return;
        OrderNotifyDto notifyDto = new OrderNotifyDto();
        notifyDto.setUrl(url);
        String key = String.format(RedisKeyHelper.RECHARGE_INSTEAD_NOTIFY_URL, orderId);
        redisService.set(key, notifyDto, 3600 * 2);
    }
}

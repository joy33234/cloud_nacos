package com.seektop.fund.handler;

import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.rest.Result;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.param.recharge.RechargeCreateDO;
import com.seektop.fund.controller.backend.result.GlPaymentNewResult;
import com.seektop.fund.controller.forehead.param.recharge.RechargePaymentDo;
import com.seektop.fund.controller.forehead.param.recharge.RechargePaymentInfoDO;
import com.seektop.fund.controller.forehead.param.recharge.RechargeSubmitDO;
import com.seektop.fund.controller.forehead.param.recharge.RechargeTransferDO;
import com.seektop.fund.controller.forehead.result.RechargeInfoResult;
import com.seektop.fund.controller.forehead.result.RechargeSettingResult;
import com.seektop.fund.payment.GlRechargeResult;
import com.seektop.fund.payment.GlRechargeTransferResult;
import com.seektop.fund.payment.RechargeNotifyResponse;
import com.seektop.fund.payment.niubipay.PaymentInfo;

import javax.servlet.http.HttpServletRequest;
import java.util.Date;

public interface RechargeHandler {

    /**
     * 清空存款人姓名和卡号
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    void clearName(GlUserDO userDO) throws GlobalException;

    /**
     * 获取用户最后一条充值记录
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    RechargeInfoResult getLastRecharge(GlUserDO userDO);

    /**
     * 撤销充值订单
     *
     * @param userDO
     * @param orderId
     * @return
     * @throws GlobalException
     */
    void doCancel(GlUserDO userDO, String orderId) throws GlobalException;

    /**
     * 获取用户有效充值方式列表
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    GlPaymentNewResult paymentInfo(RechargePaymentInfoDO paymentInfoDO, GlUserDO userDO) throws GlobalException;

    /**
     * 获取用户有效充值方式列表
     *
     * @param paymentInfoDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    GlPaymentNewResult proxyPaymentInfo(RechargePaymentInfoDO paymentInfoDO, GlUserDO userDO) throws GlobalException;

    /**
     * 充值界面快捷金额以及通道设置
     *
     * @param paramBaseDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    RechargeSettingResult getFastAmountList(ParamBaseDO paramBaseDO, GlUserDO userDO);

    /**
     * 充值提交
     *
     * @param rechargeSubmitDO
     * @param userDO
     * @return
     * @throws GlobalException
     */
    GlRechargeResult doRechargeSubmit(RechargeSubmitDO rechargeSubmitDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException;

    /**
     * 后台创建订单
     *
     * @param createDO
     * @param adminDO
     * @param request
     * @return
     * @throws GlobalException
     */
    GlRechargeResult doRechargeForBackend(RechargeCreateDO createDO, GlAdminDO adminDO, HttpServletRequest request) throws GlobalException;

    /**
     * 转账充值提交
     *
     * @param rechargeTransferDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    GlRechargeTransferResult doRechargeTransfer(RechargeTransferDO rechargeTransferDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException;

    /**
     * 后台创建转账充值提交
     *
     * @param createDO
     * @param adminDO
     * @param request
     * @return
     * @throws GlobalException
     */
    GlRechargeTransferResult doRechargeTransferForBackend(RechargeCreateDO createDO, GlAdminDO adminDO, HttpServletRequest request) throws GlobalException;

    /**
     * 充值订单回调
     *
     * @param merchantAppId
     * @param request
     * @return
     * @throws GlobalException
     */
    RechargeNotifyResponse notify(Integer merchantAppId, HttpServletRequest request) throws GlobalException;

    /**
     * 风云聚合商户定制回调接口
     *
     * @param merchantAppId
     * @param request
     * @return
     * @throws GlobalException
     */
    RechargeNotifyResponse notifyForStormPay(Integer merchantAppId, HttpServletRequest request) throws GlobalException;

    /**
     * 同步充值订单状态
     *
     * @param orderId
     */
    Result submitSynchronize(GlAdminDO adminDO, String orderId);

    /**
     * 重新同步指定ID的充值订单数据到数据中心
     *
     * @param orderIds
     */
    void reSynchronize(String... orderIds);

    /**
     * 充值订单同步到RabbitMQ
     *
     * @param startDate
     * @param endDate
     */
    void synchronize(Date startDate, Date endDate);

    /**
     * 获取渠道支付方式
     * @param paymentDo
     * @return
     */
    PaymentInfo payments(RechargePaymentDo paymentDo) throws GlobalException;

}

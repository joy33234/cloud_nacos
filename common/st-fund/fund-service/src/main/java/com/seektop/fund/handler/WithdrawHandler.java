package com.seektop.fund.handler;

import com.seektop.common.rest.Result;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.controller.backend.dto.withdraw.RejectWithdrawRequestDO;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawConfirmDto;
import com.seektop.fund.controller.forehead.param.withdraw.WithdrawSubmitDO;
import com.seektop.fund.controller.forehead.result.GlWithdrawDetailResult;
import com.seektop.fund.controller.forehead.result.GlWithdrawInfoResult;
import com.seektop.fund.controller.forehead.result.WithdrawResult;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawNotifyResponse;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

public interface WithdrawHandler {

    /**
     * C2C提现是否有权限
     *
     * @param glUserDO
     * @return
     * @throws GlobalException
     */
    boolean setC2CWithdrawOpen(GlUserDO glUserDO,String coin) throws GlobalException;

    /**
     * 获取提现配置信息
     *
     * @param userDO
     * @return
     */
    Result loadWithdrawInfo(GlUserDO userDO,String coin);

    /**
     * 获取提现配置信息
     *
     * @param userDO
     * @return
     */
    Result loadWithdrawInfoNew(GlUserDO userDO, String coin);

    /**
     * 获取提现配置信息
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    GlWithdrawInfoResult withdrawInfo(GlUserDO userDO,String coin) throws GlobalException;

    /**
     * 提现入口是否开启
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    RejectWithdrawRequestDO isClosed(GlUserDO userDO);

    /**
     * 未登录用户获取提现配置信息
     *
     * @return
     * @throws GlobalException
     */
    GlWithdrawInfoResult withdrawInfoForVisitor() throws GlobalException;

    /**
     * 提现提交接口
     *
     * @param withdrawSubmitDO
     * @param userDO
     * @param request
     * @return
     * @throws GlobalException
     */
    List<GlWithdraw> doWithdrawSubmit(WithdrawSubmitDO withdrawSubmitDO, GlUserDO userDO, HttpServletRequest request) throws GlobalException;

    /**
     * 获取提现订单详情
     *
     * @param userDO
     * @param orderId
     * @return
     * @throws GlobalException
     */
    GlWithdrawDetailResult withdrawDetail(GlUserDO userDO, String orderId) throws GlobalException;

    /**
     * 获取用户最后一笔提现订单记录
     *
     * @param userDO
     * @return
     * @throws GlobalException
     */
    WithdrawResult getLastWithdrawDetail(GlUserDO userDO) throws GlobalException;

    /**
     * 提现订单回调
     *
     * @param merchantId
     * @param request
     * @param response
     * @throws GlobalException
     */
    WithdrawNotifyResponse withdrawNotify(Integer merchantId, HttpServletRequest request, HttpServletResponse response) throws GlobalException;

    /**
     * 风云聚合商户定制回调接口
     *
     * @param merchantId
     * @param request
     * @return
     * @throws GlobalException
     */
    WithdrawNotifyResponse notifyForStormPay(Integer merchantId, HttpServletRequest request) throws GlobalException;

    /**
     * 风云聚合出款订单确认接口
     *
     * @param confirm
     * @return
     * @throws GlobalException
     */
    Boolean withdrawConfirm(WithdrawConfirmDto confirm);

    /**
     *
     * @param orderId
     * @return
     */
    WithdrawNotify withdrawStatusConfirm(String orderId) throws GlobalException;

    /**
     * 获取UDT提现汇率
     *
     * @param userDO
     * @return
     */
    Result loadUsdtRate(GlUserDO userDO);

    /**
     * 重新同步指定ID的提现订单数据到数据中心
     *
     * @param orderIds
     */
    void reSynchronize(String... orderIds);

}
package com.seektop.fund.payment.rengongpay


import com.seektop.exception.GlobalException
import com.seektop.fund.model.GlWithdraw
import com.seektop.fund.payment.RechargeNotify
import com.seektop.fund.payment.WithdrawNotify
import com.seektop.fund.payment.WithdrawResult

class RenGongScript {

    /**
     * 封装支付请求参数
     *
     * @param merchant
     * @param payment
     * @param req
     * @param result
     */
    public void pay(Object[] args) {
    }

    /**
     * 解析支付结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify notify(Object[] args) throws GlobalException {
        return null
    }

    public RechargeNotify payQuery(Object[] args) throws GlobalException {
        return null
    }

    /**
     * 解析页面跳转结果
     *
     * @param merchant
     * @param payment
     * @param resMap
     * @return
     */
    public RechargeNotify result(Object[] args) throws GlobalException {
        return notify(args)
    }


    WithdrawResult withdraw(Object[] args) throws GlobalException {
        GlWithdraw req = args[2] as GlWithdraw

        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData("人工出款");
        result.setResData("人工出款");
        result.setValid(false);
        result.setMessage("人工出款");
        return result;
    }


    WithdrawNotify withdrawNotify(Object[] args) throws GlobalException {
        return null
    }


    WithdrawNotify withdrawQuery(Object[] args) throws GlobalException {
        return null
    }


    BigDecimal balanceQuery(Object[] args) throws GlobalException {
        return new BigDecimal(-1)
    }

}

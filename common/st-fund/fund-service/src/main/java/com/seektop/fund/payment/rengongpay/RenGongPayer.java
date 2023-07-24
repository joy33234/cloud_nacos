package com.seektop.fund.payment.rengongpay;

import com.alibaba.fastjson.JSON;
import com.seektop.constant.FundConstant;
import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;
import com.seektop.fund.payment.GlPaymentWithdrawHandler;
import com.seektop.fund.payment.WithdrawNotify;
import com.seektop.fund.payment.WithdrawResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 人工出款 专用商户
 *
 * @author joy
 */

@Slf4j
@Service(FundConstant.PaymentChannel.RENGONGPAY + "")
public class RenGongPayer implements GlPaymentWithdrawHandler {

    @Override
    public WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException {
        log.info("人工出款商户，merchantAccount:{},req:{}", JSON.toJSONString(merchantAccount), JSON.toJSONString(req));
        WithdrawResult result = new WithdrawResult();
        result.setOrderId(req.getOrderId());
        result.setReqData("人工出款");
        result.setResData("人工出款");
        result.setValid(false);
        result.setMessage("人工出款");
        return result;
    }

    @Override
    public WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException {
        return doTransferQuery(merchant, "");
    }

    @Override
    public WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException {
        return null;
    }

    @Override
    public BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException {
        return BigDecimal.ZERO;
    }

}

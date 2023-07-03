package com.seektop.fund.payment;


import com.seektop.exception.GlobalException;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.fund.model.GlWithdrawMerchantAccount;

import java.math.BigDecimal;
import java.util.Map;

public interface GlPaymentWithdrawHandler {

    WithdrawResult doTransfer(GlWithdrawMerchantAccount merchantAccount, GlWithdraw req) throws GlobalException;

    WithdrawNotify doTransferNotify(GlWithdrawMerchantAccount merchant, Map<String, String> resMap) throws GlobalException;

    WithdrawNotify doTransferQuery(GlWithdrawMerchantAccount merchant, String orderId) throws GlobalException;

    BigDecimal queryBalance(GlWithdrawMerchantAccount merchantAccount) throws GlobalException;

}

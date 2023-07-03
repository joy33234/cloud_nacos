package com.seektop.fund.payment;

import com.seektop.fund.model.GlPaymentMerchantaccount;
import com.seektop.fund.model.GlRecharge;

public interface GlRechargeCancelHandler {

    void cancel(GlPaymentMerchantaccount payment, GlRecharge req);
}

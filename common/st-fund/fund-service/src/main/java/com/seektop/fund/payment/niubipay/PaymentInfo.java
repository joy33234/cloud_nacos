package com.seektop.fund.payment.niubipay;

import com.seektop.common.local.base.LocalKeyConfig;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Data
public class PaymentInfo implements Serializable {

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 汇率
     */
    private BigDecimal price;

    private List<PaymentType> types;

    private String errosMessage;

    private LocalKeyConfig localKeyConfig;
}

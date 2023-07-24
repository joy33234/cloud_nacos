package com.seektop.fund.controller.partner.result;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class PaymentBankResponse implements Serializable {

    private static final long serialVersionUID = 5010051320874267294L;

    private Integer bankId;
    private String bankName;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer status;
}

package com.seektop.fund.controller.backend.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Created by ken on 2018/4/27.
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlPaymentBankResult implements Serializable {

    private int bankId;
    private String bankName;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer Status;
}

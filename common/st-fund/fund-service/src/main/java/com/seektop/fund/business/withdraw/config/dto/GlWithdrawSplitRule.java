package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class GlWithdrawSplitRule implements Serializable {

    private BigDecimal minAmount; //最小提现金额
    private BigDecimal maxAmount; //最大提现金额
    private BigDecimal splitAmount; //拆单金额
    private BigDecimal randomAmount; //随机范围金额
}

package com.seektop.fund.business.withdraw.config.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class GlWithdrawRule implements Serializable {

    private BigDecimal betAmount;//有效投注额
    private int freeTimes;//免费提现次数
}

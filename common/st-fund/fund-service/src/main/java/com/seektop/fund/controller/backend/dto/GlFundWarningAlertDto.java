package com.seektop.fund.controller.backend.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlFundWarningAlertDto implements Serializable {

    /**
     * 充值是否有低预警
     */
    private Boolean rechargeHasLow;

    /**
     * 充值是否有高预警
     */
    private Boolean rechargeHasHigh;

    /**
     * 提现是否有低预警
     */
    private Boolean withdrawHasLow;

    /**
     * 提现是否有高预警
     */
    private Boolean withdrawHasHigh;

}

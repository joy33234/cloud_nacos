package com.seektop.fund.controller.backend.result.recharge;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeSuccessRateResult implements Serializable {

    /**
     * 商户号
     */
    private String merchantCode;

    /**
     * 额度类型
     */
    private Integer payType;

    /**
     * 成功率
     */
    private Double successRate;
}

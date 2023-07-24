package com.seektop.fund.dto.result.recharge;

import lombok.Data;

import java.io.Serializable;

@Data
public class RechargeCountVo implements Serializable {

    private static final long serialVersionUID = -92072011L;

    private Integer paymentId;

    private Integer count;

}

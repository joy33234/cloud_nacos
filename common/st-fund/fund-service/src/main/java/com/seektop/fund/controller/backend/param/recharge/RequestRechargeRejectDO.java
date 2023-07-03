package com.seektop.fund.controller.backend.param.recharge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RequestRechargeRejectDO implements Serializable {

    private static final long serialVersionUID = 1448070260629376662L;

    @NotNull(message = "orderId is not Null")
    private String orderId;


    //充值补单拒绝备注
    @NotNull(message = "remark is not Null")
    private String remark;


}

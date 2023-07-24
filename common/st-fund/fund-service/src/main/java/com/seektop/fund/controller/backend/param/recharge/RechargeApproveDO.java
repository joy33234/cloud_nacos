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
public class RechargeApproveDO implements Serializable {


    private static final long serialVersionUID = -5791390218542158761L;

    @NotNull(message = "orderId is Not Null")
    private String orderId;

    // 审核结果：1通过，2拒绝
    @NotNull(message = "status is Not Null")
    private Integer status;

    /**
     * 备注
     */
    @NotNull(message = "remark is Not Null")
    private String remark;

}

package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
public class WithdrawRequestApproveDO implements Serializable {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    /**
     * 审核结果：1通过，2拒绝
     */
    @NotNull(message = "status 不能为空")
    private Integer status;

    @NotBlank(message = "remark 不能为空")
    private String remark;
}

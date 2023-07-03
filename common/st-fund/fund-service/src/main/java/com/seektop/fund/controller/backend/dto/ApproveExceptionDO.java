package com.seektop.fund.controller.backend.dto;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.NotNull;


@Data
@ToString
public class ApproveExceptionDO extends ManageParamBaseDO {

    @NotNull(message = "orderId 不能为空")
    private String orderId;

    @NotNull(message = "status 不能为空")
    private Integer status;

    private String remark;

    @NotNull(message = "rejectReason 不能为空")
    private String rejectReason;
}

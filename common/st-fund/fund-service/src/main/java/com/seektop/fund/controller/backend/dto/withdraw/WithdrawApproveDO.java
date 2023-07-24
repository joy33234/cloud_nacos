package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
public class WithdrawApproveDO implements Serializable {

    private static final long serialVersionUID = -1545642806451096372L;

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    private String remark;

//    @NotEmpty(message = "rejectReason 不能为空")
    private String rejectReason;

    /**
     * 审核结果：1通过，2拒绝
     */
    @NotNull(message = "status 不能为空")
    private Integer status;

    /**
     * 出款方式 0:人工出款，2三方手动出款
     */
    @NotNull(message = "withdrawType 不能为空")
    private Integer withdrawType;

    private String transferBankName;

    private String transferName;

    /**
     * 出款商户ID
     */
    private Integer merchantId;


    private Date updateTime;
}

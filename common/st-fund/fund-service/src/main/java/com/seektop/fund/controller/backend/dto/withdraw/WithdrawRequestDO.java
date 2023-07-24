package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;
import java.math.BigDecimal;

@Getter
@Setter
public class WithdrawRequestDO implements Serializable {

    @NotBlank(message = "orderId 不能为空")
    private String orderId;

    @NotBlank(message = "remark 不能为空")
    private String remark;

    /**
     * 部分提现金额退回
     */
    @Min(value = 0,message = "最低充值金额不能低于0")
    private BigDecimal amount;

    /**
     * 附件
     */
    private String attachments;



}

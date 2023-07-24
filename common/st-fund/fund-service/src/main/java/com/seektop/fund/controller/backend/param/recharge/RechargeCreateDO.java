package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargeCreateDO extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 7581524522316513191L;

    /**
     * 原始订单ID
     */
    @NotNull(message = "relationOrderId is Not Null")
    private String relationOrderId;

    /**
     * 订单金额
     */
    @NotNull(message = "amount is Not Null")
    @Min(value = 100, message = "订单最低金额100")
    private BigDecimal amount;

    /**
     * 备注
     */
    @NotNull(message = "remark is Not Null")
    private String remark;

    /**
     * 附件
     */
    @NotNull(message = "attachments is not Null")
    private List<String> attachments;
}

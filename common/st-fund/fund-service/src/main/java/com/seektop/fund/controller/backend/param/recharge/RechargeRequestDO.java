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
public class RechargeRequestDO extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 1436281673526952925L;

    @NotNull(message = "orderId is Not Null")
    private String orderId;

    //收款人姓名
    private String payeeName;

    //收款银行id
//    @Min(value = 1, message = "收款银行ID不能小于1")
    private Integer payeeBankId;

    //收款银行名称
    private String payeeBankName;

    /**
     * 补单附件
     */
    @NotNull(message = "attachments is not Null")
    private List<String> attachments;

    /**
     * 补单申请金额
     */
    @NotNull(message = "amount is Not Null")
    @Min(value = 1, message = "补单申请金额不能小于1")
    private BigDecimal amount;

    /**
     * 备注
     */
    @NotNull(message = "remark is Not Null")
    private String remark;

}

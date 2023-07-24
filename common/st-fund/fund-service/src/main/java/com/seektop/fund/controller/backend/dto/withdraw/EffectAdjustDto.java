package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class EffectAdjustDto extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = -4296053702211203258L;

    @NotNull(message = "userId不能为空")
    private Integer userId;
    /**
     * 调整额度，可正可负，对当前这个记录进行流水调整
     */
    @NotNull(message = "调整额度不能为空")
    private BigDecimal amount;
    @NotBlank(message = "备注信息不能为空")
    private String remark;
    @NotBlank(message = "币种不能为空")
    private String coinCode;
}

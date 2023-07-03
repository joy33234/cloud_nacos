package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Data
public class EffectRemoveDto extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 1176810251213814477L;

    @NotBlank(message = "操作单号不能为空")
    private String orderNo;
    @NotBlank(message = "备注信息不能为空")
    private String remark;
    @NotBlank(message = "币种不能为空")
    private String coinCode;
}

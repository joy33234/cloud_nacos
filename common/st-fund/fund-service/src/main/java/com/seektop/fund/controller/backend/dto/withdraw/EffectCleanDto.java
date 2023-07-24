package com.seektop.fund.controller.backend.dto.withdraw;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class EffectCleanDto extends ManageParamBaseDO implements Serializable {

    private static final long serialVersionUID = 1176810251213814477L;

    @NotNull(message = "操作单号不能为空")
    private Integer userId;
    @NotBlank(message = "备注信息不能为空")
    private String remark;
    @NotBlank(message = "币种不能为空")
    private String coinCode;
}

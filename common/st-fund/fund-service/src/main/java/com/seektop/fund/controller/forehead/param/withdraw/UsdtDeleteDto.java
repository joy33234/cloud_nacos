package com.seektop.fund.controller.forehead.param.withdraw;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class UsdtDeleteDto extends ParamBaseDO {

    @NotNull(message = "id不能为空")
    private Integer id;

    @NotBlank(message = "短信验证码不能为空")
    private String code;
}

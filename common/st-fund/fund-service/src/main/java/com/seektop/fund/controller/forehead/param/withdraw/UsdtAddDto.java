package com.seektop.fund.controller.forehead.param.withdraw;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class UsdtAddDto extends ParamBaseDO {

    @NotNull(message = "nickName不能为空")
    private String nickName;

    @NotNull(message = "币种不能为空")
    private String coin;

    @NotNull(message = "protocol不能为空")
    private String protocol;

    @NotNull(message = "address不能为空")
    private String address;

    @NotBlank(message = "短信验证码不能为空")
    private String code;

}
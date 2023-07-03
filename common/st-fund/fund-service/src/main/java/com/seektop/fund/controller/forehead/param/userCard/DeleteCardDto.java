package com.seektop.fund.controller.forehead.param.userCard;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;

@Data
public class DeleteCardDto extends ParamBaseDO {

    @NotNull(message = "银行卡id不能为空")
    private Integer cardId;

    @NotBlank(message = "短信验证码不能为空")
    private String code;
}

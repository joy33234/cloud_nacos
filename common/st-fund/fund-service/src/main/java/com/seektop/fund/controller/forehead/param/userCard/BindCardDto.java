package com.seektop.fund.controller.forehead.param.userCard;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BindCardDto extends ParamBaseDO {

    @NotBlank(message = "银行卡不能为空")
    private String cardNo;

    /**
     * 持卡人姓名
     */
    private String name;

    /**
     * 短信验证码
     */
    private String code;
}

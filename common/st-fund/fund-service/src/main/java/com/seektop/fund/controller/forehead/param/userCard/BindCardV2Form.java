package com.seektop.fund.controller.forehead.param.userCard;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class BindCardV2Form extends ParamBaseDO {

    private static final long serialVersionUID = 8571061512549486975L;

    @NotBlank(message = "银行卡不能为空")
    private String cardNo;

    /**
     * 持卡人姓名
     */
    private String name;

    /**
     * 短信验证码
     */
    @NotBlank(message = "短信验证码不能为空")
    private String code;
}

package com.seektop.fund.controller.forehead.param.userCard;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ApplyBindCardV2Form extends ApplyBindCardDto {
    private static final long serialVersionUID = 715802953956691951L;

    /**
     * 短信验证码
     */
    @NotBlank(message = "短信验证码不能为空")
    private String code;
}

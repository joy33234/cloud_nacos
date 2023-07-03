package com.seektop.fund.controller.backend.param.bankcard;

import com.seektop.common.utils.RegexValidator;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

@Data
public class BindCardForm implements Serializable {
    private static final long serialVersionUID = -7965316832770690452L;
    /**
     * 账号
     */
    @NotBlank(message = "账号不能为空")
    private String username;
    /**
     * 持卡人姓名
     */
    @NotBlank(message = "持卡人姓名不能为空")
    private String name;
    /**
     * 银行卡号
     */
    @NotBlank(message = "银行卡号不能为空")
    @Pattern(regexp = RegexValidator.REGEX_BANKCARD, message = "银行卡号格式不正确")
    private String cardNo;
    /**
     * 银行id
     */
    @NotNull(message = "银行不能为空")
    private Integer bankId;
    /**
     * 银行名称
     */
    private String bankName;
}

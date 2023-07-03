package com.seektop.fund.controller.partner.param;

import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.utils.RegexValidator;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import lombok.Data;

import javax.validation.constraints.*;
import java.math.BigDecimal;

@Data
public class OrderForm extends ParamBaseDO {
    private static final long serialVersionUID = 2432991601756004186L;

    @NotNull(message = "userId不能为空")
    private Integer userId;
    @NotNull(message = "adminUserId不能为空")
    private Long adminUserId;
    @NotBlank(message = "adminUsername不能为空")
    private String adminUsername;
    @NotNull(message = "paymentId不能为空")
    private Integer paymentId;
    @NotNull(message = "merchantAppId不能为空")
    private Integer merchantAppId;
    @NotNull(message = "amount不能为空")
    private BigDecimal amount;
    /**
     * 待客充值码对应的id
     */
    @NotNull(message = "agencyId不能为空")
    private Integer agencyId;

    /**
     * 银行id
     */
    @Min(value = 1, message = "bankId最小为1")
    @Max(value = 15, message = "bankId最大为15")
    private Integer bankId;
    /**
     * 付款人姓名
     */
    @Size(max = 20, message = "付款人姓名长度为小于等于20")
    private String name;
    /**
     * 付款银行卡号
     */
    @Pattern(regexp = "(" + RegexValidator.REGEX_BANKCARD + ")?", message = "请填写正确的转账银行信息")
    private String cardNo;
    /**
     * 回调通知地址
     */
    private String notifyUrl;

    /**
     * 钱包协议
     */
    private String protocol;

    /**
     * 币种
     */
    private String coin = DigitalCoinEnum.CNY.getCode();
}

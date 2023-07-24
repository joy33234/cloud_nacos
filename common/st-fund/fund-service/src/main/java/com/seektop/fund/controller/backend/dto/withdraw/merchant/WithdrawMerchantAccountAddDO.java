package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@ToString
public class WithdrawMerchantAccountAddDO implements Serializable {

    /**
     * 支付渠道ID
     */
    @NotNull(message = "参数异常:channelId Not Null")
    private Integer channelId;

    /**
     * 商户号
     */
    @NotNull(message = "参数异常:merchantCode Not Null")
    private String merchantCode;

    /**
     * 公钥
     */
    @NotNull(message = "参数异常:publicKey Not Null")
    private String publicKey;

    /**
     * 私钥
     */
    @NotNull(message = "参数异常:privateKey Not Null")
    private String privateKey;

    /**
     * 支付地址
     */
    @NotNull(message = "参数异常:payUrl Not Null")
    private String payUrl;

    /**
     * 通知地址
     */
    @NotNull(message = "参数异常:notifyUrl Not Null")
    private String notifyUrl;

    /**
     * merchantFeeType = 0 按笔收费
     * merchantFeeType = 1 按百分比收费
     */
    @NotNull(message = "参数异常:merchantFeeType Not Null")
    private Integer merchantFeeType;

    /**
     * 出款手续费：根据merchantFeeType计算
     */
    @NotNull(message = "参数异常:merchantFee Not Null")
    private BigDecimal merchantFee;

    /**
     * 备注
     */
    @NotNull(message = "参数异常:remark Not Null")
    private String remark;

    /**
     * 每日出款上限
     */
    private Integer dailyLimit = 0;

    /**
     * 出款最小金额
     */
    @NotNull(message = "参数异常:minAmount Not Null")
    @Min(value = 1, message = "最小金额不能小于1")
    private BigDecimal minAmount;

    /**
     * 出款最大金额
     */
    @NotNull(message = "参数异常:maxAmount Not Null")
    @Min(value = 1, message = "最大金额不能小于1")
    private BigDecimal maxAmount;

    /**
     * 支持代付银行id
     */
    @NotNull(message = "参数异常:bankId Not Null")
    private String bankId;

    @NotNull(message = "参数异常:enableScript Not Null")
    private Integer enableScript;

    /**
     * 付款人姓名类型：-1全部 1普通汉族姓名 2少数民族姓名 3英文名
     */
    @NotEmpty(message = "参数异常:nameType Not Null")
    private List<Integer> nameType;

}

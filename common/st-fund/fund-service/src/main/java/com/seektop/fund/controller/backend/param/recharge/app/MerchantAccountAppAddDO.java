package com.seektop.fund.controller.backend.param.recharge.app;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccountAppAddDO implements Serializable {

    /**
     * 额度分类： 0 普通充值、1 大额充值
     */
    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType;
    /**
     * 支付方式ID
     */
    @NotNull(message = "参数异常:paymentId Not Null")
    private Integer paymentId;
    /**
     * 三方商户ID
     */
    @NotNull(message = "参数异常:merchantId Not Null")
    private Integer merchantId;
    /**
     * 层级ID(多个用逗号隔开)
     */
    @NotNull(message = "参数异常:levelId Not Null")
    private String levelId;
    /**
     * 应用端(0 PC端、1 移动端)
     */
    @NotNull(message = "参数异常:clientType Not Null")
    private Integer clientType;
    /**
     * 适用方式：0:应用渠道, 1:代客渠道  2：极速转卡
     */
    @NotEmpty(message = "参数异常:useModes Not Null")
    private List<Integer> useModes;
    /**
     * 充值商户佣金费率类型 0、固定金额 1、百分比金额
     */
    @NotNull(message = "参数异常:merchantFeeType Not Null")
    private Integer merchantFeeType;
    /**
     * 充值商户佣金
     */
    @NotNull(message = "参数异常:merchantFee Not Null")
    private BigDecimal merchantFee;
    /**
     * 备注
     */
    @NotNull(message = "参数异常:remark Not Null")
    private String remark;
    /**
     * 快捷金额，多个用逗号隔开
     */
    private String quickAmount;
    /**
     * 手续费比例
     */
    @Max(value = 100, message = "参数异常:feeRate maxValue 100")
    @Min(value = 0, message = "feeRate 不能小于0")
    private BigDecimal feeRate;
    /**
     * 最大手续费金额
     */
    @NotNull(message = "参数异常:maxFee Not Null")
    @Min(value = 0, message = "maxFee 不能小于0")
    private BigDecimal maxFee;
    /**
     * 最低充值金额
     */
    @NotNull(message = "参数异常:minAmount Not Null")
    @Min(value = 0, message = "minAmount 不能小于0")
    private BigDecimal minAmount;
    /**
     * 最高充值金额
     */
    @NotNull(message = "参数异常:maxAmount Not Null")
    @Min(value = 0, message = "maxAmount 不能小于0")
    private BigDecimal maxAmount;

    /**
     * 通道最大处理量   0:不限制
     */
    private Integer maxCount = 0;

    /**
     * 单位时间（轮询）处理量
     */
    @NotNull(message = "参数异常:cycleCount Not Null")
    private Integer cycleCount;

    /**
     * 轮询优化级
     */
    @NotNull(message = "参数异常:cyclePriority Not Null")
    private Integer cyclePriority;

    /**
     * 付款人姓名类型：-1全部 1普通汉族姓名 2少数民族姓名 3英文名
     */
    @NotEmpty(message = "参数异常:nameType Not Null")
    private List<Integer> nameType;

    /**
     * VIP等级
     */
    @NotNull(message = "参数异常:vipLevel Not Null")
    private List<Integer> vipLevel;

}

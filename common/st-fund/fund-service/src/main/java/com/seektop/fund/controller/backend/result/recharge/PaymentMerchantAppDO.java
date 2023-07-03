package com.seektop.fund.controller.backend.result.recharge;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class PaymentMerchantAppDO implements Serializable {


    private static final long serialVersionUID = 2724785630033600122L;

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 支付方式ID
     */
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    private String paymentName;

    /**
     * 三方商户渠道ID
     */
    private Integer channelId;

    /**
     * 三方商户渠道名称
     */
    private String channelName;

    /**
     * 三方商户ID
     */
    private Integer merchantId;

    /**
     * 三方商户号
     */
    private String merchantCode;

    /**
     * 层级ID(多个用逗号隔开)
     */
    private String levelId;

    /**
     * 额度分类： 0 普通充值、1 大额充值
     */
    private Integer limitType;

    /**
     * 应用端(0 PC端、1 移动端)
     */
    private Integer clientType;

    /**
     * 适用方式：0:应用渠道, 1:代客渠道
     */
    private Integer useMode;

    /**
     * 推荐状态（0-已推荐、1-未推荐）
     */
    private Integer recommendStatus;

    /**
     * 状态（0-已上架、1-已下架、2-已删除）
     */
    private Integer status;

    /**
     * 置顶状态（0-已置顶、1-未置顶）
     */
    private Integer topStatus;

    /**
     * 置顶时间
     */
    private Date topDate;

    /**
     * 开关状态(0 已开启、1 已关闭)
     */
    private Integer openStatus;

    /**
     * 充值商户佣金费率类型 0、固定金额 1、百分比金额
     */
    private Integer merchantFeeType;

    /**
     * 充值商户佣金
     */
    private BigDecimal merchantFee;

    /**
     * 快捷金额，多个用逗号隔开
     */
    private String quickAmount;


    /**
     * 备注
     */
    private String remark;

    private Date createDate;

    private String creator;

    private Date lastUpdate;

    private String lastOperator;

    /**
     * 层级名称
     */
    private String levelName;

    /**
     * 今日已收款金额
     */
    private BigDecimal successAmount;

    /**
     * 每日收款限额
     */
    private BigDecimal dailyLimit;

    /**
     * 渠道别称
     */
    private String aisleName;
    /**
     * 手续费比例
     */
    private BigDecimal feeRate;
    /**
     * 最大手续费金额
     */
    private BigDecimal maxFee;
    /**
     * 最低充值金额
     */
    private BigDecimal minAmount;
    /**
     * 最高充值金额
     */
    private BigDecimal maxAmount;
    /**
     * 通道最大处理订单数量    0:不限制
     */
    private Integer maxCount;

    /**
     * 单位时间（轮询）处理量
     */
    private Integer cycleCount;

    /**
     * 轮询优化级
     */
    private Integer cyclePriority;

    /**
     * 付款人姓名类型：-1全部 1普通汉族姓名 2少数民族姓名 3英文名
     */
    private String nameType;


    /**
     * VIP等级
     */
    private String vipLevel;
}
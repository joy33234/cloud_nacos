package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_payment_merchant_app")
public class GlPaymentMerchantApp {

    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 支付方式ID
     */
    @Column(name = "payment_id")
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    @Column(name = "payment_name")
    private String paymentName;

    /**
     * 三方商户渠道ID
     */
    @Column(name = "channel_id")
    private Integer channelId;

    /**
     * 三方商户渠道名称
     */
    @Column(name = "channel_name")
    private String channelName;

    /**
     * 三方商户ID
     */
    @Column(name = "merchant_id")
    private Integer merchantId;

    /**
     * 三方商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 层级ID(多个用逗号隔开)
     */
    @Column(name = "level_id")
    private String levelId;

    /**
     * 额度分类： 0 普通充值、1 大额充值
     */
    @Column(name = "limit_type")
    private Integer limitType;

    /**
     * 应用端(0 PC端、1 移动端)
     */
    @Column(name = "client_type")
    private Integer clientType;

    /**
     * 适用方式：0:应用渠道, 1:代客渠道
     */
    @Column(name = "use_mode")
    private Integer useMode;

    /**
     * 推荐状态（0-已推荐、1-未推荐）
     */
    @Column(name = "recommend_status")
    private Integer recommendStatus;

    /**
     * 状态（0-已上架、1-已下架、2-已删除）
     */
    private Integer status;

    /**
     * 置顶状态（0-已置顶、1-未置顶）
     */
    @Column(name = "top_status")
    private Integer topStatus;

    /**
     * 置顶时间
     */
    @Column(name = "top_date")
    private Date topDate;

    /**
     * 开关状态(0 已开启、1 已关闭)
     */
    @Column(name = "open_status")
    private Integer openStatus;

    /**
     * 充值商户佣金费率类型 0、固定金额 1、百分比金额
     */
    @Column(name = "merchant_fee_type")
    private Integer merchantFeeType;

    /**
     * 充值商户佣金
     */
    @Column(name = "merchant_fee")
    private BigDecimal merchantFee;

    /**
     * 快捷金额，多个用逗号隔开
     */
    @Column(name = "quick_amount")
    private String quickAmount;


    /**
     * 备注
     */
    private String remark;

    @Column(name = "create_date")
    private Date createDate;

    private String creator;

    @Column(name = "last_update")
    private Date lastUpdate;

    @Column(name = "last_operator")
    private String lastOperator;

    /**
     * 通道最大处理订单数量   0:不限制
     */
    @Column(name = "max_count")
    private Integer maxCount;

    /**
     * 单位时间（轮询）处理量
     */
    @Column(name = "cycle_count")
    private Integer cycleCount;

    /**
     * 轮询优化级
     */
    @Column(name = "cycle_priority")
    private Integer cyclePriority;

    /**
     * 付款人姓名类型:  付款人姓名类型：-1全部 1普通汉族姓名 2少数民族姓名 3英文名
     */
    @Column(name = "name_type")
    private String nameType;

    /**
     * vip等级
     */
    @Column(name = "vip_level")
    private String vipLevel;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;
}
package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 代充手续费记录表
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_proxy_recharge_fee")
public class ProxyRechargeFee implements Serializable {

    private static final long serialVersionUID = -463761738925117974L;

    /**
     * id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 代充订单Id
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 方案ID
     */
    @Column(name = "rule_id")
    private Integer ruleId;

    /**
     * 代理的用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 结算手续费费率
     */
    @Column(name = "fee_rate")
    private BigDecimal feeRate;

    /**
     * 结算手续费
     */
    @Column(name = "fee")
    private BigDecimal fee;

    /**
     * 返利比例
     */
    @Column(name = "rebate_rate")
    private BigDecimal rebateRate;

    /**
     * 返利*10000
     */
    @Column(name = "rebate")
    private BigDecimal rebate;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 帐变类型: 0  代充账户, 1 授信账户
     */
    @Transient
    private Integer accountType;

    /**
     * 上分帐变类型：1 上分清算-人工操作清算,2 代理授信,3 公司授信,4 会员代充,5 上分清算-账户余额清算,6 代充清算-银行卡充值清算
     */
    @Transient
    private Integer optType;

}

package com.seektop.fund.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_c2c_withdraw_order_pool")
public class C2CWithdrawOrderPool implements Serializable {

    /**
     * 提现单号
     */
    @Id
    @Column(name = "withdraw_order_id")
    private String withdrawOrderId;

    /**
     * 充值单号
     */
    @Column(name = "recharge_order_id")
    private String rechargeOrderId;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户名
     */
    @Column(name = "username")
    private String username;

    /**
     * IP地址
     */
    @Column(name = "ip")
    private String ip;

    /**
     * 提现金额
     */
    @Column(name = "withdraw_amount")
    private BigDecimal withdrawAmount;

    /**
     * 撮合状态
     *
     * @see com.seektop.enumerate.fund.C2CWithdrawOrderStatusEnum
     */
    @Column(name = "status")
    private Short status;

    /**
     * 是否锁定
     */
    @Column(name = "is_locked")
    private Boolean isLocked;

    /**
     * 锁定的用户ID
     */
    @Column(name = "locked_user_id")
    private Integer lockedUserId;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 锁定时间
     */
    @Column(name = "locked_date")
    private Date lockedDate;

    /**
     * 撮合时间
     */
    @Column(name = "matched_date")
    private Date matchedDate;

    /**
     * 付款时间
     */
    @Column(name = "payment_date")
    private Date paymentDate;

    /**
     * 收款时间
     */
    @Column(name = "receive_date")
    private Date receiveDate;

}
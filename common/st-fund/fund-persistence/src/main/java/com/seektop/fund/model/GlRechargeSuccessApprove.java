package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 充值补单审核
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge_sucapv")
public class GlRechargeSuccessApprove {

    private static final long serialVersionUID = -8113357573637409603L;
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 审核人ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 审核人用户名
     */
    private String username;

    /**
     * 审核金额
     */
    private BigDecimal amount;

    /**
     * 审核备注
     */
    private String remark;

    /**
     * 审核状态：1同意，2拒绝
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

}
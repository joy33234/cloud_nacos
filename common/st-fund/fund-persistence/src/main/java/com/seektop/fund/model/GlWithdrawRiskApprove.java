package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 提现风控审核记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_riskapv")
public class GlWithdrawRiskApprove implements Serializable {

    private static final long serialVersionUID = 4345598911849539680L;
    /**
     * 提现订单ID
     */
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
     * 审核备注
     */
    private String remark;

    /**
     * 审核状态：1通过，2拒绝
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 拒绝出款原因
     */
    @Column(name = "reject_reason")
    private String rejectReason;
}
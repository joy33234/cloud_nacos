package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提现操作申请记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_returnreq")
public class GlWithdrawReturnRequest implements Serializable {

    private static final long serialVersionUID = -6669845654152922437L;
    /**
     * 提现单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户类型：0玩家，1代理
     */
    @Column(name = "user_type")
    private Integer userType;

    /**
     * 提现账号
     */
    private String username;

    /**
     * 退回金额
     */
    private BigDecimal amount;

    /**
     * 操作类型：0提现退回，1强制成功，2手动成功，3手动失败
     */
    private Integer type;

    /**
     * 退回状态：0待审核，1审核通过，2审核拒绝
     */
    private Integer status;
    /**
     * 订单状态
     */
    @Column(name = "withdraw_status")
    private Integer withdrawStatus;
    /**
     * 出款商户名
     */
    private String merchant;
    /**
     * 出款商户ID
     */
    @Column(name = "merchant_id")
    private Integer merchantId;
    /**
     * 申请人
     */
    private String creator;

    /**
     * 申请时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 审核人
     */
    private String approver;

    /**
     * 审核时间
     */
    @Column(name = "approve_time")
    private Date approveTime;

    /**
     * 备注
     */
    private String remark;

    /**
     * 审核备注
     */
    @Column(name = "approve_remark")
    private String approveRemark;
    /**
     * 出款类型
     */
    @Column(name = "withdraw_type")
    private Integer withdrawType;

    /**
     * 拒绝出款原因
     */
    @Column(name = "reject_reason")
    private String rejectReason;

    /**
     * 附件
     */
    @Column(name = "attachments")
    private String attachments;


    /**
     * 会员姓名
     */
    @Transient
    private String reallyName;

    @Transient
    private Integer userLevel;

    @Transient
    private String userLevelName;

    @Transient
    private String coin;

}
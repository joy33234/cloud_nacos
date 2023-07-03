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
 * 提现审核记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_returnapv")
public class GlWithdrawReturnApprove implements Serializable {

    private static final long serialVersionUID = 5923415841492827099L;
    /**
     * 提现订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 审核状态：1同意，2拒绝
     */
    private Integer status;

    /**
     * 审核人
     */
    private String creator;

    /**
     * 审核时间
     */
    @Column(name = "create_time")
    private Date createTime;

    private String remark;

    /**
     * 拒绝出款原因
     */
    @Column(name = "reject_reason")
    private String rejectReason;
}
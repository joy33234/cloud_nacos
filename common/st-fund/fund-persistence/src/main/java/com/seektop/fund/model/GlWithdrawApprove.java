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
 * 手动出款记录(人工出款、拒绝出款)
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_approve")
public class GlWithdrawApprove implements Serializable {

    private static final long serialVersionUID = 2842604786374004194L;
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
     * 出款类型：0人工打款，1自动出款
     */
    @Column(name = "withdraw_type")
    private Integer withdrawType;

    /**
     * 出款渠道名称
     */
    @Column(name = "merchant_name")
    private String merchantName;

    /**
     * 出款渠道账号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 审核状态：1通过，2拒绝（退回）
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

}
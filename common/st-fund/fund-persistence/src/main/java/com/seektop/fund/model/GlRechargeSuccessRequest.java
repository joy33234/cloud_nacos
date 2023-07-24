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
 * 充值补单申请
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge_sucreq")
public class GlRechargeSuccessRequest {

    private static final long serialVersionUID = -1502109981474058046L;
    /**
     * 充值订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 申请人ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 申请人用户名
     */
    private String username;

    /**
     * 审核金额
     */
    private BigDecimal amount;

    /**
     * 申请备注
     */
    private String remark;

    /**
     * 申请状态：0待审核，1已通过，2已拒绝
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 上传图片地址
     */
    @Column(name = "req_img")
    private String reqImg;

}
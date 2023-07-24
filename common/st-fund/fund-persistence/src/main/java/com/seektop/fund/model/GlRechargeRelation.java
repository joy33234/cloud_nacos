package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge_relation")
public class GlRechargeRelation {


    private static final long serialVersionUID = -6980153081232800740L;

    /**
     * 充值订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 充值关联订单号
     */
    @Column(name = "relation_order_id")
    private String relationOrderId;

    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 转账凭证截图
     */
    @Column(name = "img")
    private String img;

    /**
     * 创建订单申请人
     */
    @Column(name = "creator")
    private String creator;

    @Column(name = "create_date")
    private Date createDate;

}
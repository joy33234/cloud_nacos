package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

/**
 * 资金调整申请关联订单
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_change_relation")
public class GlFundChangeRelation implements Serializable {

    /**
     * 订单ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 关联订单号
     */
    @Column(name = "relation_recharge_order_id")
    private String relationRechargeOrderId;

    /**
     * 三方订单号
     */
    @Column(name = "third_order_id")
    private String thirdOrderId;

}
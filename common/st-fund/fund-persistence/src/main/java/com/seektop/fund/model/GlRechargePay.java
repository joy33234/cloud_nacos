package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge_pay")
public class GlRechargePay implements Serializable {


    private static final long serialVersionUID = 5985758814627395200L;

    /**
     * 充值订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 支付金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;

    /**
     * 支付时间
     */
    @Column(name = "pay_date")
    private Date payDate;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;


    /**
     * 第三方订单号
     */
    @Column(name = "third_order_id")
    private String thirdOrderId;

}
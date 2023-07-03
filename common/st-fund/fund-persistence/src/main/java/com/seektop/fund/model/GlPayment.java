package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_payment")
public class GlPayment implements Serializable {

    /**
     * 支付方式ID
     */
    @Id
    @Column(name = "payment_id")
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    @Column(name = "payment_name")
    private String paymentName;

    /**
     * 支付方式Logo
     */
    @Column(name = "payment_logo")
    private String paymentLogo;

    /**
     * 支付方式排序
     */
    @Column(name = "sort")
    private Integer sort;

    /**
     * 状态：0正常，1已禁用
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;

}
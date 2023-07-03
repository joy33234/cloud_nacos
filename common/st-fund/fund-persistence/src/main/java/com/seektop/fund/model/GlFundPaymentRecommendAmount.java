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

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_payment_recommend_amount")
public class GlFundPaymentRecommendAmount implements Serializable {

    private static final long serialVersionUID = -5474806692540189406L;
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
     * 推荐金额
     */
    @Column(name = "recommend_amount")
    private String recommendAmount;

    /**
     * 支付方式名称
     */
    @Column(name = "coin")
    private String coin;


    /**
     * 操作人
     */
    private String operator;

    /**
     * 最后更新时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;
}

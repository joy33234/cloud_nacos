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
 * 资金调整审核记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_changeapv")
public class GlFundChangeApprove implements Serializable {

    private static final long serialVersionUID = 2638266810350383713L;
    /**
     * 订单ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 审核人
     */
    private String creator;

    /**
     * 审核备注
     */
    private String remark;

    /**
     * 审核结果：1通过，2拒绝
     */
    private Integer status;

    /**
     * 审核时间
     */
    @Column(name = "create_time")
    private Date createTime;

}
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

/**
 * 提现拆单记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_split")
public class GlWithdrawSplit implements Serializable {

    /**
     * 订单ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 拆单关联-父ID
     */
    @Column(name = "parent_id")
    private String parentId;


    /**
     * 拆单金额
     */
    @Column(name = "amount")
    private BigDecimal amount;

    /**
     * 提现拆单配置（JSON字符串）
     */
    @Column(name = "split_config")
    private String splitConfig;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

}
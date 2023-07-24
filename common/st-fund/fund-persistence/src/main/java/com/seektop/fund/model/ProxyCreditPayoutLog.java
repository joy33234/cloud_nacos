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

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_proxy_credit_payout_log")
public class ProxyCreditPayoutLog implements Serializable {

    private static final long serialVersionUID = -8843394509209709296L;

    /**
     * ID
     */
    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * 订单ID
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 订单类型: 0 授信账户代充(余额代充) , 1 授信额度代充(额度给会员上分)
     */
    @Column(name = "order_type")
    private Integer orderType;

    /**
     * 上分金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;

    /**
     * 返利
     */
    private BigDecimal rebate;

    /**
     * 代理ID
     */
    @Column(name = "proxy_id")
    private Integer proxyId;

    /**
     * 代理账户名
     */
    @Column(name = "proxy_name")
    private String proxyName;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户帐户名
     */
    @Column(name = "user_name")
    private String userName;

}

package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_recharge_error")
public class GlRechargeError implements Serializable {
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 账户类型(0-会员、1-代理)
     */
    @Column(name = "user_type")
    private Integer userType;

    /**
     * 账户名
     */
    private String username;

    /**
     * 操作端(0PC，1H5，2安卓，3IOS，4PAD)
     */
    @Column(name = "os_type")
    private Integer osType;

    /**
     * 三方商户
     */
    @Column(name = "channel_id")
    private Integer channelId;

    @Column(name = "channel_name")
    private String channelName;

    /**
     * 三方商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;

    /**
     * 异常状态（0-系统异常、1-三方异常）
     */
    @Column(name = "error_status")
    private Integer errorStatus;

    /**
     * 充值时间
     */
    @Column(name = "recharge_date")
    private Date rechargeDate;

    /**
     * 错误原因
     */
    @Column(name = "error_msg")
    private String errorMsg;

    /**
     * 订单号
     */
    @Column(name = "order_id")
    private String orderId;

}
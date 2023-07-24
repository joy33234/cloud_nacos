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
@Table(name = "gl_payment_usercard")
public class GlPaymentUserCard implements Serializable {

    private static final long serialVersionUID = 7646752609873450072L;
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 银行卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 开户人姓名
     */
    @Column(name = "card_username")
    private String cardUsername;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 最后使用时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

}
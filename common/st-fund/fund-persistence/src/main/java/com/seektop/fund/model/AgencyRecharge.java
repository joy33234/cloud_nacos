package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * 代客充值
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_agency_recharge")
public class AgencyRecharge implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(generator = "JDBC")
    private Integer id;

    @Column(name = "user_name")
    private String userName;

    @Column(name = "user_id")
    private Integer userId;

    @Column(name = "user_type")
    private Integer userType;

    @Column(name = "user_level")
    private Integer userLevel;

    @Column(name = "user_level_name")
    private String userLevelName;

    @Column(name = "code")
    private Integer code;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "admin_id")
    private Integer adminId;

    @Column(name = "admin_name")
    private String adminName;

    @Column(name = "create_date")
    private Date createDate;

    @Column(name = "app_type")
    private Integer appType;

    @Transient
    private Long ttl;
}

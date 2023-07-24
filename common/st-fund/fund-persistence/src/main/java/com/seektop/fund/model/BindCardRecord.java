package com.seektop.fund.model;

import lombok.Data;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 后台运维绑上记录
 */
@Data
@Table(name = "gl_fund_bind_card_record")
public class BindCardRecord implements Serializable {

    /**
     * id
     */
    @Id
    private Integer id;
    /**
     * 用户userId
     */
    @Column(name = "user_id")
    private Integer userId;
    /**
     * 用户username
     */
    private String username;
    /**
     * 银行卡号
     */
    @Column(name = "card_no")
    private String cardNo;
    /**
     * 姓名
     */
    private String name;
    /**
     * 银行卡id
     */
    @Column(name = "bank_id")
    private Integer bankId;
    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;
    /**
     * 创建人的userId
     */
    @Column(name = "create_user_id")
    private Integer createUserId;
    /**
     * 创建人的username
     */
    @Column(name = "create_username")
    private String createUsername;
    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;
}

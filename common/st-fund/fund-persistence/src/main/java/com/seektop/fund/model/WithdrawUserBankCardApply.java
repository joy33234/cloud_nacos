package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户申请人工绑卡银行卡信息表
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_userbankcard_apply")
public class WithdrawUserBankCardApply implements Serializable {

    /**
     * 银行卡ID
     */
    @Id
    @Column(name = "card_id")
    private Integer cardId;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户类型：0会员，1代理
     */
    @Transient
    private Integer userType;

    /**
     * 用户名
     */
    @Transient
    private String username;

    /**
     * 银行ID
     */
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 开户省市区
     */
    private String address;

    /**
     * 银行卡图片
     */
    @Column(name = "image_path")
    private String imagePath;

    /**
     * 银行卡状态(0和1暂无使用)：0未删除，1已删除，2审核中，3已拒绝，4已通过
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;
}

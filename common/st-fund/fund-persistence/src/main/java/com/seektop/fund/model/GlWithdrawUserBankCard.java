package com.seektop.fund.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "gl_withdraw_userbankcard")
public class GlWithdrawUserBankCard implements Serializable {


    private static final long serialVersionUID = 877248419825749236L;
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
    @Column(name = "name")
    private String name;

    /**
     * 卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 开户省市区
     */
    @Column(name = "address")
    private String address;

    /**
     * 银行卡状态
     * <p>
     * 0未删除
     * 1已删除
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 是否默认选中
     * <p>
     * 0不是
     * 1是
     */
    @Column(name = "selected")
    private Integer selected;

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

    /**
     * 是否可以删除
     */
    @Transient
    private Boolean canDelete;

}
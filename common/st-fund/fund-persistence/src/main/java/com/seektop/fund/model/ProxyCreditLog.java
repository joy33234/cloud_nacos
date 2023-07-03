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
@Table(name = "gl_proxy_credit_log")
/**
 * 上分报表
 */
public class ProxyCreditLog implements Serializable {

    private static final long serialVersionUID = -8843394509209709296L;

    /**
     * ID
     */
    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * 代理ID
     */
    @Column(name = "proxy_id")
    private Integer proxyId;

    /**
     * 代理用户名: 20190520分支版本后,该字段不再存储,但为兼容旧数据保留
     */
    @Column(name = "proxy_user_name")
    private String proxyUserName;

    /**
     * 配置下级上分额度前
     */
    @Column(name = "credited_amount_before")
    private BigDecimal creditedAmountBefore;

    /**
     * 配置下级上分额度后
     */
    @Column(name = "credited_amount_after")
    private BigDecimal creditedAmountAfter;

    /**
     * 变更金额
     */
    @Column(name = "change_amount")
    private BigDecimal changeAmount;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 操作人Id
     */
    @Column(name = "parent_id")
    private Integer optUserId;

    /**
     * 上分帐变类型：1 上分清算-人工操作清算,2 代理授信,3 公司授信,4 会员代充,5 上分清算-账户余额清算,6 代充清算-银行卡充值清算,7 代充账户入账
     */
    @Column(name = "opt_type")
    private Integer optType;

    /**
     * 操作人
     */
    @Column(name = "opt_user_name")
    private String optUserName;

    /**
     * 操作人类型:-1全部,1公司,2上级代理,3代理本人
     */
    @Column(name = "opt_people_type")
    private Integer optPeopleType;

    /**
     * 交易编号
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 关联单号
     */
    @Column(name = "relation_order_id")
    private String relationOrderId;

    /**
     * 修改的用户名ID
     * 上级代理给下级代理上分  : 下级代理ID
     * 代理给会员上分  :  会员ID
     * 上分清算  :  代理ID
     */
    @Column(name = "modify_user_id")
    private Integer modifyUserId;

    /**
     * 修改的用户名
     * 上级代理给下级代理上分  : 下级代理名称
     * 代理给会员上分  :  会员名称
     * 上分清算  :  代理ID
     */
    @Column(name = "modify_user_name")
    private String modifyUserName;

    /**
     * 修改账户类型  :  0  会员, 1   代理
     */
    @Column(name = "modify_user_type")
    private Integer modifyUserType;

    /**
     * 修改账户的变化前额度 :  会员展示余额  , 代理展示上分额度
     */
    @Column(name = "modify_amount_before")
    private BigDecimal modifyAmountBefore;

    /**
     * 修改账户的变化后额度 :  会员展示余额  , 代理展示上分额度
     */
    @Column(name = "modify_amount_after")
    private BigDecimal modifyAmountAfter;

    /**
     * 帐变类型: 0  代充账户帐变, 1 额度帐变
     */
    @Column(name = "account_type")
    private Integer accountType;

    /**
     * 状态: 0：处理中，1：成功，2失败
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;

}
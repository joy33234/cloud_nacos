package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * 提现审核会员抽检列表
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_user_check")
public class GlWithdrawUserCheck implements Serializable {

    private static final long serialVersionUID = 4201815350787986118L;
    /**
     * 提现订单号
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 提现用户ID
     */
    @Column(name = "uid")
    private Integer uid;

    /**
     * 用户类型
     */
    @Column(name = "userType")
    private Integer userType;

    /**
     * 用户名
     */
    @Column(name = "userName")
    private String userName;

    /**
     * 用户层级
     */
    @Column(name = "levelName")
    private String levelName;

    /**
     * 状态：
     * 0: 待检查
     * 1: 检查通过
     * 2: 检查不通过
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "createDate")
    private Date createDate;

    /**
     * 检查时间
     */
    @Column(name = "checkTime")
    private Date checkTime;

    /**
     * 最后体现时间
     */
    @Column(name = "lastWithDrawTime")
    private Date lastWithDrawTime;

    /**
     * 审核人
     */
    @Column(name = "riskApprover")
    private String riskApprover;

    /**
     * 标签
     */
    private String tag;

    /**
     * 审核备注
     */
    private String remark;
}
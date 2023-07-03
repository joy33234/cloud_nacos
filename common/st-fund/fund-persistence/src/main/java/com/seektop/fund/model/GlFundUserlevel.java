package com.seektop.fund.model;

import com.seektop.fund.group.CommonGroup;
import com.seektop.fund.group.UpdateGroup;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 用户层级
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_fund_userlevel")
public class GlFundUserlevel implements Serializable {

    private static final long serialVersionUID = 5896074028451673180L;

    @Id
    @Column(name = "level_id")
    @NotNull(groups = {UpdateGroup.class}, message = "修改时levelId是必须的参数")
    private Integer levelId;

    /**
     * 名称
     */
    @Size(max = 30, min = 1, groups = {CommonGroup.class}, message = "名称字数限制：1-20")
    @Column(name = "name")
    private String name;

    /**
     * 注册开始时间
     */
    @Column(name = "reg_from_time")
    private Date regFromTime;

    /**
     * 注册截止时间
     */
    @Column(name = "reg_end_time")
    private Date regEndTime;

    /**
     * 充值次数
     */
    @Column(name = "recharge_times")
    private Integer rechargeTimes;

    /**
     * 充值金额
     */
    @Column(name = "recharge_total")
    private BigDecimal rechargeTotal;

    /**
     * 提现次数
     */
    @Column(name = "withdraw_times")
    private Integer withdrawTimes;

    /**
     * 提现总额
     */
    @Column(name = "withdraw_total")
    private BigDecimal withdrawTotal;

    /**
     * 销量总额
     */
    @Column(name = "bet_total")
    private BigDecimal betTotal;

    /**
     * 是否是VIP专享
     * <p>
     * 0不是
     * 1是
     */
    @Column(name = "vip")
    private Byte vip;

    /**
     * 备注
     */
    @Size(max = 200, groups = {CommonGroup.class}, message = "备注字数限制：0-200")
    @Column(name = "remark")
    private String remark;

    /**
     * 充值权限配置
     */
    @Column(name = "payment")
    private String payment;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 创建人ID
     */
    @Column(name = "creator_id")
    private Integer creatorId;

    /**
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 最后修改人ID
     */
    @Column(name = "last_operator_id")
    private Integer lastOperatorId;

    /**
     * 最后修改人
     */
    @Column(name = "last_operator")
    private String lastOperator;

    /**
     * 排序ID
     */
    @Column(name = "sort_id")
    private Integer sortId;

    /**
     * 层级类型
     * <p>
     * 0会员层级
     * 1代理层级
     */
    @Column(name = "level_type")
    private Integer levelType;

    /**
     * 层级是否可以提现（0-开启、1-关闭)默认开启
     */
    @Column(name = "withdraw_off")
    private Integer withdrawOff;

}
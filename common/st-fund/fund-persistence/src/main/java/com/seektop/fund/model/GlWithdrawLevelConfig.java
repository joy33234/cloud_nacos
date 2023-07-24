package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * 用户层级提现风控设置
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_level_config")
public class GlWithdrawLevelConfig implements Serializable {

    private static final long serialVersionUID = -1344712924305521666L;
    /**
     * 主键ID
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 层级ID
     */
    @Column(name = "level_id")
    private Integer levelId;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;

    @Transient
    private String levelName;

    /**
     * 单日提现金额
     */
    @Column(name = "daily_amount")
    private Integer dailyAmount;

    /**
     * 单次提现金额
     */
    private Integer amount;

    /**
     * 7天累计提现金额
     */
    @Column(name = "weekly_amount")
    private Integer weeklyAmount;

    /**
     * 单日提现次数
     */
    @Column(name = "daily_times")
    private Integer dailyTimes;

    /**
     * 首次提现金额 -->  当日首提金额
     */
    @Column(name = "first_amount")
    private Integer firstAmount;

    /**
     * 当日盈利
     */
    @Column(name = "daily_profit")
    private Integer dailyProfit;

    /**
     * 同ip多账号检测:0关闭，1开启
     */
    @Column(name = "same_ip_check")
    private Integer sameIpCheck;

    /**
     * 同设备多账号检测:0关闭，1开启
     */
    @Column(name = "same_device_check")
    private Integer sameDeviceCheck;

    /**
     * 配置状态：0未启用，1启用，2删除
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 最后修改时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 最后操作人
     */
    @Column(name = "last_operator")
    private String lastOperator;

    /**
     * 检测时长
     */
    @Column(name = "time_check")
    private Integer timeCheck;

    /**
     * 用户注册时间
     */
    @Column(name = "register_days")
    private Integer registerDays;

    /**
     * 用户首次提现金额
     */
    @Column(name = "first_withdraw_amount")
    private Integer firstWithdrawAmount;

}
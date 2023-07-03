package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@Table(name = "gl_recharge_betting_level_config")
public class RechargeBettingLevelConfig implements Serializable {

    @Id
    @Column(name = "id")
    private Long id;

    /**
     * 检测层级ID
     */
    @Column(name = "level_id")
    private Integer levelId;

    /**
     * 检测层级名称
     */
    @Column(name = "level_name")
    private String levelName;

    /**
     * 天数
     */
    @Column(name = "days")
    private Integer days;

    /**
     * 充值金额
     */
    @Column(name = "recharge_amount")
    private BigDecimal rechargeAmount;

    /**
     * 流水倍数
     */
    @Column(name = "betting_multiple")
    private BigDecimal bettingMultiple;

    /**
     * 变更层级ID
     */
    @Column(name = "target_level_id")
    private Integer targetLevelId;

    /**
     * 变更层级名称
     */
    @Column(name = "target_level_name")
    private String targetLevelName;

    /**
     * 配置状态
     *
     * 0：启用
     * 1：停用
     * 2：删除
     */
    @Column(name = "status")
    private Integer status;

    /**
     * 更新人
     */
    @Column(name = "updater")
    private String updater;

    /**
     * 更新时间
     */
    @Column(name = "update_date")
    private Date updateDate;

}
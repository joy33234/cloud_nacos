package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@Table(name = "gl_first_recharge_level_config")
public class FirstRechargeLevelConfig implements Serializable {

    @Id
    @Column(name = "level_id")
    private Integer levelId;

    /**
     * 检测层级名称
     */
    @Column(name = "level_name")
    private String levelName;

    /**
     * 检测层级类型
     */
    @Column(name = "level_type")
    private Integer levelType;

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
     * 充值成功次数
     */
    @Column(name = "recharge_success_times")
    private Integer rechargeSuccessTimes;

    /**
     * 状态
     *
     * 0：正常
     * 1：禁用
     */
    @Column(name = "status")
    private Short status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

    /**
     * 更新时间
     */
    @Column(name = "update_date")
    private Date updateDate;

    /**
     * 更新人
     */
    @Column(name = "updater")
    private String updater;

}
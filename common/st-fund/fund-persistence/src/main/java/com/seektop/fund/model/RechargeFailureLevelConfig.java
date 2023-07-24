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
@Table(name = "gl_recharge_failure_level_config")
public class RechargeFailureLevelConfig implements Serializable {

    @Id
    @Column(name = "level_id")
    private Integer levelId;

    /**
     * 新用户失败次数
     */
    @Column(name = "new_user_times")
    private Integer newUserTimes;

    /**
     * 老用户失败次数
     */
    @Column(name = "old_user_times")
    private Integer oldUserTimes;

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
     * 生效的VIP等级
     */
    @Column(name = "vip_level")
    private String vips;

    /**
     * 配置状态
     *
     * 0：正常
     * 1：删除
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
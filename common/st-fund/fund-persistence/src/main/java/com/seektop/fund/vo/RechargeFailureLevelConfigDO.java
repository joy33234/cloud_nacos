package com.seektop.fund.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
public class RechargeFailureLevelConfigDO implements Serializable {

    /**
     * 排序ID
     */
    private Integer sortId;

    /**
     * 层级ID
     */
    private Integer levelId;

    /**
     * 层级名称
     */
    private String levelName;

    /**
     * 层级类型
     *
     * 0：会员层级
     * 1：代理层级
     */
    private Integer levelType;

    /**
     * 新用户失败次数
     */
    private Integer newUserTimes;

    /**
     * 老用户失败次数
     */
    private Integer oldUserTimes;

    /**
     * 变更层级ID
     */
    private Integer targetLevelId;

    /**
     * 变更层级名称
     */
    private String targetLevelName;

    /**
     * 生效的VIP等级
     */
    private String vips;

    /**
     * 更新人
     */
    private String updater;

    /**
     * 更新时间
     */
    private Date updateDate;

}
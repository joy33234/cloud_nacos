package com.seektop.fund.controller.backend.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FundUserLevelListResult extends GlFundUserlevel {

    /**
     * 锁定用户数量
     */
    private Integer lockUsers;

    /**
     * 已设置渠道商户数
     */
    private Integer merchantCount;

    /**
     * 风云出款分层标识
     */
    private String withdrawTag;

    /**
     * 新用户失败次数
     */
    private Integer newUserRechargeFailureTimes;

    /**
     * 老用户失败次数
     */
    private Integer oldUserRechargeFailureTimes;

    /**
     * 目标层级ID
     */
    private Integer targetLevelId;

    /**
     * 目标层级名称
     */
    private String targetLevelName;

    /**
     * 生效的VIP等级
     */
    private String vips;

}
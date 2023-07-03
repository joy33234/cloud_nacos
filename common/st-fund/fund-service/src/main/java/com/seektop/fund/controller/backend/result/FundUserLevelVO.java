package com.seektop.fund.controller.backend.result;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.Data;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class FundUserLevelVO extends GlFundUserlevel {

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

}

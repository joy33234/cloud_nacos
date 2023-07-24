package com.seektop.fund.controller.backend.param.recharge.account;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccountListDO extends ManageParamBaseDO implements Serializable {

    private Integer channelId = -1;

    //商户号
    private String merchantCode;

    private Integer page = 0;

    private Integer size = 10;

    /**
     * 状态
     */
    private Integer status = -1;

    /**
     * 渠道类型
     */
    private Integer limitType = -1;

    /**
     * 是否启用动态脚本 0：不启用（默认），1：启用
     */
    private Integer enableScript = -1;

    /**
     * 开始时间
     */
    private Date startTime;

    /**
     * 结束时间
     */
    private Date endTime;

    /**
     * 币种
     */
    private String coin;

}

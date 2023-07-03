package com.seektop.fund.controller.backend.dto.withdraw.merchant;

import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.fund.vo.ManageParamBase;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
public class WithdrawMerchantAccountQueryDO extends ManageParamBaseDO implements Serializable {

    /**
     * 账号状态：0上架，1下架，2已删除
     */
    private Integer status = -1;
    ;

    /**
     * 开启状态： 0 已开启、 1 已关闭
     */
    private Integer openStatus = -1;

    /**
     * 是否启用动态脚本 0：不启用（默认），1：启用
     */
    private Integer enableScript = -1;

    /**
     * 三方渠道ID
     */
    private Integer channelId = -1;

    //页码
    private Integer page = 0;

    //每页数量
    private Integer size = 20;

    /**
     * 排序
     */
    private String order;

    /**
     * 币种
     */
    private String coin;

}

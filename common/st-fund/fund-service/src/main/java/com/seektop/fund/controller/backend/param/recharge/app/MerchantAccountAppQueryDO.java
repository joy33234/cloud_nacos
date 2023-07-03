package com.seektop.fund.controller.backend.param.recharge.app;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccountAppQueryDO extends ManageParamBaseDO implements Serializable {

    /**
     * 支付方式
     */
    private List<Integer> paymentIds;

    /**
     * 渠道ID
     */
    private Integer channelId = -1;

    /**
     * 商户号
     */
    private String merchantCode;

    /**
     * 状态（0-已上架、1-已下架、2-已删除）
     */
    private Integer status = -1;

    /**
     * 商户状态(0 已开启、1 已关闭)
     */
    private Integer openStatus = -1;

    /**
     * 支付层级
     */
    private List<Integer> levelIds;

    /**
     * 支付类型（-1 全部、0：普通充值、1：大额充值）
     */
    private Integer limitType = -1;

    /**
     * 应用端类型 -1全部、0PC端、1移动端
     */
    private Integer clientType = -1;

    /**
     * 适用方式：0:应用渠道, 1:代客渠道
     */
    private Integer useMode;

    /**
     * 币种
     */
    private String coin;

    private Integer page = 0;
    private Integer size = 20;


}

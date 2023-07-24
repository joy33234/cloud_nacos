package com.seektop.fund.controller.backend.param.recharge.fee;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantFeeListDO extends ManageParamBaseDO implements Serializable {



    /**
     * 三方商户ID
     */
    private Integer merchantId;

    /**
     * 渠道ID
     */
    private Integer channelId;


    /**
     * 额度类型  0：普通充值(默认)，1：大额充值, 2:代理充值
     */
    @NotNull(message = "参数异常:limitType Not Null")
    private Integer limitType;


}

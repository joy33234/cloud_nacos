package com.seektop.fund.controller.backend.param.recharge.app;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MerchantAccountAppEditStatusDO implements Serializable {

    //商户应用ID
    @NotNull(message = "参数异常:merchantAppIds Not Null")
    private List<Integer> merchantAppIds;

    /**
     * 状态（0-已上架、1-已下架、2-已删除）
     */
    @NotNull(message = "参数异常:status Not Null")
    private Integer status;

}

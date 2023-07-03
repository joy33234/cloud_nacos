package com.seektop.fund.controller.backend.param.recharge.account;

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
public class MerchantAccountBatchEditDO implements Serializable {


    //状态
    @NotNull(message = "参数异常:status Not Null")
    private Integer status ;


    //商户IDS
    @NotNull(message = "参数异常:merchantIds Not Null")
    private List<Integer> merchantIds;

}

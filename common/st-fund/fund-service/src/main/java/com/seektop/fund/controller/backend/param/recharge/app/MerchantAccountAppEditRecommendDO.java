package com.seektop.fund.controller.backend.param.recharge.app;

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
public class MerchantAccountAppEditRecommendDO implements Serializable {

    @NotNull(message = "参数异常:id Not Null")
    private Integer id;


    /**
     * 推荐状态（0-已推荐、1-未推荐）
     */
    @NotNull(message = "参数异常:recommendStatus Not Null")
    private Integer recommendStatus;



}

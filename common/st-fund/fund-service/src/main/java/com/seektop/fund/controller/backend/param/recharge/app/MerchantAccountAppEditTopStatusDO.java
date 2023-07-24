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
public class MerchantAccountAppEditTopStatusDO implements Serializable {

    @NotNull(message = "参数异常:id Not Null")
    private Integer id;

    /**
     * 置顶状态（0-已置顶、1-未置顶）
     */
    @NotNull(message = "参数异常:topStatus Not Null")
    private Integer topStatus;

}

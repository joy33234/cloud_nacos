package com.seektop.fund.controller.partner.param;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class PaymentForm implements Serializable {

    private static final long serialVersionUID = 2624987456573011946L;
    /**
     * 用户id
     */
    @NotNull(message = "userId不能为空")
    private Integer userId;
    /**
     * 用户层级id
     */
    private Integer levelId;
    /**
     * 使用端
     */
    private Integer osType;
}

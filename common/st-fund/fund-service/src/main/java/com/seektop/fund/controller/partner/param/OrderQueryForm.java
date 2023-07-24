package com.seektop.fund.controller.partner.param;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

@Data
public class OrderQueryForm implements Serializable {
    private static final long serialVersionUID = -5937961230056970070L;

    @NotNull(message = "userId不能为空")
    private Integer userId;
    @NotBlank(message = "orderId不能为空")
    private String orderId;
}

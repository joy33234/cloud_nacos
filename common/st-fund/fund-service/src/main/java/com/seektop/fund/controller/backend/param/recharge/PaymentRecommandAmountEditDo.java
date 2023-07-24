package com.seektop.fund.controller.backend.param.recharge;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PaymentRecommandAmountEditDo implements Serializable {

    @NotNull(message = "paymentId not null")
    private Integer paymentId;

    @NotNull(message = "recommendAmount not null")
    private List<Integer> recommendAmount;

}

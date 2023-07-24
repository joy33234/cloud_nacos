package com.seektop.fund.controller.backend.param.recharge.fee;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ChannelBankEditDO implements Serializable {

    /**
     * 网银银行卡限额
     */
    @Size(min = 1,message = "bankList 不能为空")
    private List<PaymentChannelBankEditDO> bankList;


}

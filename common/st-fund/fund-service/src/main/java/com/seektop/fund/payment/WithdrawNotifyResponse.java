package com.seektop.fund.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawNotifyResponse {

    private String content;
    private boolean redirect = false;

}

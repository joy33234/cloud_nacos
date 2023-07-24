package com.seektop.fund.payment;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RechargeSubmitResponse {

    private String contentType = "text/html;charset=UTF-8";
    private String content;
    private boolean redirect;
}

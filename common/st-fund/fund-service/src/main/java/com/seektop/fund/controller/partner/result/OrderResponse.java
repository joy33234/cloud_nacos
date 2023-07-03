package com.seektop.fund.controller.partner.result;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderResponse implements Serializable {
    private static final long serialVersionUID = -5898914338159011823L;

    private Boolean innerPay;

    private OrderInfoResponse info;
    private OrderTextResponse text;
}

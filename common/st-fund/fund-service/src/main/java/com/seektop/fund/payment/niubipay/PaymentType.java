package com.seektop.fund.payment.niubipay;

import lombok.Data;

import java.io.Serializable;

@Data
public class PaymentType implements Serializable {

    private String code;

    private Integer id;

    private String name;
}

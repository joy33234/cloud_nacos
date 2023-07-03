package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


@Data
public class FundsExcelReport implements Serializable {

    private String orderId;

    private String relationRechargeOrderId;

    private Integer userType;

    private String userName;

    private Integer changeType;

    private Integer subType;

    private BigDecimal amount;

    private BigDecimal freezeAmount;

    private String creator;

}

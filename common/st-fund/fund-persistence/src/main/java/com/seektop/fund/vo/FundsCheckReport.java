package com.seektop.fund.vo;


import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;


/**
 * 资金调整报表
 */
@Data
public class FundsCheckReport implements Serializable {


    private String secondTime;

    private Integer changeType;

    private Integer subType;

    private BigDecimal sumAmount;

    private Integer sumNum;


}

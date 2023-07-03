package com.seektop.fund.controller.backend.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class NoticeSuccessDto implements Serializable {

    private static final long serialVersionUID = 3140384064859466202L;

    private Integer userId;
    private String userName;
    private BigDecimal amount;
    private String orderId;

    private Integer type;
    private String subTypeName;
    private String remark;
    private String coin;
}

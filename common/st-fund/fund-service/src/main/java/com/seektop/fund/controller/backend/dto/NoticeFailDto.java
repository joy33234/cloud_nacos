package com.seektop.fund.controller.backend.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
public class NoticeFailDto implements Serializable {

    private static final long serialVersionUID = -394372056279198099L;

    private Integer userId; 
    private String userName;
    private BigDecimal amount;
    private String orderId;
    private String rejectReason;
    private String coin;
}

package com.seektop.fund.controller.backend.dto;

import lombok.Data;

import java.io.Serializable;

@Data
public class FundUserLockResult implements Serializable {

    private Integer failNum;
    private String failMessage;

    private Integer successNum;
}

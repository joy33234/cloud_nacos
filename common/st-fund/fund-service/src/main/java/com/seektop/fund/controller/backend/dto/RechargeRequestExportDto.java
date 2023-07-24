package com.seektop.fund.controller.backend.dto;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Data
@ToString
public class RechargeRequestExportDto extends ParamBaseDO implements Serializable {

    private Date startDate;
    private Date endDate;
    private BigDecimal minAmount;
    private BigDecimal maxAmount;
    private Integer userType = -1;
    private List<Integer> changeType;
    private String creator;
    private String keywords;
    private String orderId;
    private String relationOrderId;
    private String firstApprover;
    private String secondApprover;
    private Integer page = 0;
    private Integer size = 10;
    private List<Integer> subType;
    private Integer dateType = 1;

    private Integer userId;

    private List<Integer> statuses;
}

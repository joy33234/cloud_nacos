package com.seektop.fund.dto.param.recharge;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class RebateAwardDto implements Serializable {
    private static final long serialVersionUID = 705808738996398131L;

    private Date startTime;
    private Date endTime;
}

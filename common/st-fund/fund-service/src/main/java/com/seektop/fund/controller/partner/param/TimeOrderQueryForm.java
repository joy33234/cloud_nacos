package com.seektop.fund.controller.partner.param;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class TimeOrderQueryForm implements Serializable {
    private static final long serialVersionUID = -5937961230056970222L;

    @NotNull(message = "userId不能为空")
    private Integer userId;
    private String orderId;
    @NotNull(message = "stime不能为空")
    private Date stime;
    @NotNull(message = "etime不能为空")
    private Date etime;
    private Integer page = 1;
    private Integer size = 10;
    private List<Integer> status;
    private List<Integer> paymentId;
    /**
     * 主状态
     */
    private Boolean mainStatus = false;
}

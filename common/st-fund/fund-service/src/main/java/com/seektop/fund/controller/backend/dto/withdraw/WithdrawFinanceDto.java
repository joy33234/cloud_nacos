package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Data;

import java.io.Serializable;

@Data
public class WithdrawFinanceDto implements Serializable {

    //渠道名称
    private String channelName;

    //商户号
    private String merchantCode;

    //状态
    private Integer status = -1;

    //页码
    private Integer page = 1;

    //数量
    private Integer size = 20;
}

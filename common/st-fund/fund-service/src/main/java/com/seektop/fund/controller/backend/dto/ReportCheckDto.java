package com.seektop.fund.controller.backend.dto;

import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.enumerate.digital.DigitalCoinEnum;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;


@Data
@ToString
public class ReportCheckDto extends ManageParamBaseDO implements Serializable {

    private Date date;  //日期(yyyy-MM-dd)
    private String merchantCode;    //商户号
    private Integer channelId = -1; //渠道ID(-1全部(默认))
    private String coinCode = DigitalCoinEnum.CNY.getCode();
}

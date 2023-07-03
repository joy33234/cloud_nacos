package com.seektop.fund.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

@Data
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class RechargeMonitorResult implements Serializable {

    public Integer channelId;

    public Date createDte;

    public Integer retStatus;
}

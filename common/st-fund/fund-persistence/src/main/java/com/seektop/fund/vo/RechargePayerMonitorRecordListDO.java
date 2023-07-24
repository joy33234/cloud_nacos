package com.seektop.fund.vo;

import com.alibaba.fastjson.JSONArray;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class RechargePayerMonitorRecordListDO implements Serializable {

    private Integer userId;

    private String username;

    private Integer userType;

    private Date registerDate;

    private Date createDate;

    private String payerName;

    private JSONArray payers;

    /**
     * 不同付款人姓名次数
     */
    private Integer times;

}
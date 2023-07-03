package com.seektop.fund.vo;

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
public class RechargePayerMonitorUsernameWhiteListDO implements Serializable {

    private Integer userId;

    private String username;

    private Date registerDate;

    private Date createDate;

    private String creator;

}
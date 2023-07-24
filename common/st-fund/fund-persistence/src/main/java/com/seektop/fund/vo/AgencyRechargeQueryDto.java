package com.seektop.fund.vo;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;


@Getter
@Setter
@NoArgsConstructor
@ToString
public class AgencyRechargeQueryDto implements Serializable {

    private String userName;

    private String code;

    private Integer userId;

    private int page = 1;

    private int size = 10;

}

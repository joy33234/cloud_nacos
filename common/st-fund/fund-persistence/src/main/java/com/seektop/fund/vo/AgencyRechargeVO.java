package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;

@Data
public class AgencyRechargeVO implements Serializable {
    private static final long serialVersionUID = 5853429448253765386L;

    private Integer id;
    /**
     * 代客充值码
     */
    private Integer code;
}

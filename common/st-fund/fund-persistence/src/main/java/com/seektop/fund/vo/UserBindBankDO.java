package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserBindBankDO implements Serializable {

    private String username;

    /**
     *  用户类型 userType 0会员 1代理
     */
    private Integer userType;

    /**
     * 银行卡号
     */
    private String cardNo;

    /**
     * 银行卡状态
     * <p>
     * 0未删除
     * 1已删除
     */
    private Integer status;

    private Date lastUpdate;

}


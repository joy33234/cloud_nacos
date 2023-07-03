package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserBindUsdtDO implements Serializable {

    private String username;

    /**
     * 用户类型 userType 0会员 1代理
     */
    private Integer userType;

    /**
     * 钱包地址
     */
    private String address;

    /**
     * 有效状态(0-有效、1-删除 2删除中) default = 0
     */
    private Integer status;

    private Date lastUpdate;

    /**
     * 币种
     */
    private String coin;

    /**
     * 别名
     */
    private String nickName;

    /**
     * 协议
     */
    private String protocol;

}


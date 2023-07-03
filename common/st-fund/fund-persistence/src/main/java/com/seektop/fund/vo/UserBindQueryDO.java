package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class UserBindQueryDO extends ManageParamBase implements Serializable {

    /**
     * 查询时间段
     */
    private Date startTime;
    private Date endTime;

    /**
     * 查询卡号/usdt地址
     */
    private String searchKey;

    /**
     * 用户类型:userType 0会员 1代理
     */
    private Integer userType = -1;

    private String username;

    private Integer status = -1;

    private Integer page =1;
    private Integer size =20;
}

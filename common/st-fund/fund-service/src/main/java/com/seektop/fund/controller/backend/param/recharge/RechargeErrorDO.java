package com.seektop.fund.controller.backend.param.recharge;

import com.seektop.fund.vo.ManageParamBase;
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
public class RechargeErrorDO extends ManageParamBase implements Serializable {

    private static final long serialVersionUID = 8764336542390652991L;

    //开始时间
    private Date startTime;

    //结束时间
    private Date endTime;


    //用户类型
    private Integer userType;

    //帐号
    private String userName;

    //渠道ID
    private Integer channelId;

    //商户号
    private String merchantCode;

    //状态
    private Integer errorStatus;

    private Integer page;

    private Integer size;


}

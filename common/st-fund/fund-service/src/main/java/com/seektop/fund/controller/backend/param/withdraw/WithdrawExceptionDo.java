package com.seektop.fund.controller.backend.param.withdraw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawExceptionDo implements Serializable {

    /**
     * 主键ID
     */
    private Integer id;

    /**
     * 层级ID
     */
    @NotNull(message = "levelId is Not Null")
    private Integer levelId;


    /**
     * 风控金额配置
     */
    @NotNull(message = "list is Not Null")
    private List<WithdrawExceptionAmountDo> list;


    private String levelName;

    /**
     * 同ip多账号检测:0关闭，1开启
     */
    private Integer sameIpCheck;

    /**
     * 同设备多账号检测:0关闭，1开启
     */
    private Integer sameDeviceCheck;

    /**
     * 配置状态：0未启用，1启用，2删除
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;

    /**
     * 最后操作人
     */
    private String lastOperator;

    /**
     * 检测时长
     */
    private Integer timeCheck;

    /**
     * 用户注册时间
     */
    private Integer registerDays;


}

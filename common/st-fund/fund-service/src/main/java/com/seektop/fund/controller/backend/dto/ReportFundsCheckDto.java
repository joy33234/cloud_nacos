package com.seektop.fund.controller.backend.dto;

import com.seektop.common.mvc.ManageParamBaseDO;
import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;
import java.util.List;


@Data
@ToString
public class ReportFundsCheckDto extends ManageParamBaseDO {

    // 日期(yyyy-MM-dd)
    private Date date;

    // 操作类型 状态码：1009 |加币-计入红利，1018|加币-不计红利，1011|减币
    private Integer changeType;

    /**
     * 子操作类型 1|红包，2|活动红利，3|人工充值，4|提现失败退回，5|转账补分，
     *           6|游戏补分-贝博体育，7|游戏补分-LB彩票，8|上分返利，9|佣金调整，10|系统回扣，
     *           11|错误上分扣回,12|游戏补分-5GM彩票
     */
    private List<Integer> subType;
}

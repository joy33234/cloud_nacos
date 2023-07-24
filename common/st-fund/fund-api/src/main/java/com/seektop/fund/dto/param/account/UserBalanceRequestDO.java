package com.seektop.fund.dto.param.account;

import com.seektop.dto.GlUserDO;
import lombok.Data;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@ToString
public class UserBalanceRequestDO implements Serializable {
    private GlUserDO user;
    private BigDecimal balance;
    private String coinCode;
    private String creator;
    /**
     * @link  FundConstant.ChangeOperateSubType.value
     */
    private Integer subChangeType;
    //上报的备注，25建议使用 "虚拟会员初始化金额"
    String remark;
    private Integer changeType;
}

package com.seektop.fund.controller.backend.param.recharge;

import lombok.Data;
import lombok.ToString;

import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Set;

@Data
@ToString
public class FundRequestAddDto implements Serializable {
    @Size(min=1, message = "用户名不能为空")
    private Set<String> users;
    /**
     * 操作类型 状态码：1009 |加币-计入红利，1018|加币-不计红利，1011|减币
     */
    @NotNull(message = "changeType 不能为空")
    private Integer changeType;
    @NotNull(message = "请输入正确金额")
    private BigDecimal amount;
    private BigDecimal freezeAmount = BigDecimal.ZERO;
    @NotBlank(message="备注不能为空")
    private String remark;
    /**
     * 子操作类型
     * 1|红包，2|活动红利，3|人工充值，4|提现失败退回，5|转账补分，
     * 6|游戏补分-贝博体育，7|游戏补分-LB彩票，8|上分返利，9|佣金调整，10|系统回扣，
     * 11|错误充值扣回（会员）,12|游戏补分-5GM彩票，16|游戏补分， 17|代理提现 ， 18|错误充值扣回（代理）
     * 19|线下充送活动
     */
    @NotNull(message = "subType 不能为空")
    private Integer subType;

    /**
     * 关联单号
     */
    private String relationOrderId;

    /**
     * 三方单号
     */
    private String thirdOrderId;
    /**
     * 提现额度
     */
    @Min(value = 0,message = "提现额度不能小于0")
    private BigDecimal vaildWithdraw;

    /**
     * 代理活动ID
     */
    private Integer actId;

    /**
     * 充值金额
     */
    private BigDecimal rechargeAmount;

}

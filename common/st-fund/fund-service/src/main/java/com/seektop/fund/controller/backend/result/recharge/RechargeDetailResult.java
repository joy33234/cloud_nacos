package com.seektop.fund.controller.backend.result.recharge;

import com.seektop.fund.dto.result.recharge.GlRechargeDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class RechargeDetailResult implements Serializable {

    /**
     * 三方商户订单号
     */
    private String thirdOrderId;

    /**
     * 充值订单信息
     */
    private GlRechargeDO recharge;

    /**
     * 关联单号创建的订单号信息
     */
    private List<GlRechargeDO> relationRecharge;

    /**
     * 充值订单支付信息
     */
    private RechargePayResult rechargePay;

    /**
     * 补单申请信息
     */
    private RechargeSuccessRequestResult reqRecharge;

    /**
     * 补单审核信息
     */
    private RechargeSuccessApproveResult apvRecharge;

    /**
     * 附件（银行凭证截图）
     */
    private List<String> attachments;

    /**
     * 商户订单详情
     */
    private RechargeDigitalResult digitalResult;

    /**
     * 是否是内部支付
     */
    private boolean innerPay;

    /**
     * 待确认到帐时间
     */
    private Date pendingConfirmDate;


}

package com.seektop.fund.vo;

import com.seektop.fund.model.GlWithdraw;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class WithdrawVO extends GlWithdraw {

    private Boolean isSeparate;

    private String telephone;

    private String reallyName;

    /**
     * 渠道ID
     */
    private Integer channelId;
    /**
     * 渠道名称
     */
    private String channelName;
    /**
     * 商户佣金手续费
     */
    private Long merchantFee;
    /**
     * 用户状态：0正常，1完全锁定，2间接锁定
     */
    private Integer userLockStatus;

    /**
     * USDT 出款汇率
     */
    private BigDecimal rate;

    /**
     * USDT金额
     */

    private BigDecimal usdtAmount;


    /**
     * 实际USDT支付数量
     */
    private BigDecimal actualUsdtAmount;

    /**
     * 状态 （内部回调使用）
     */
    private Integer status;

    /**
     * 充值帐号
     */
    private String rechargeName;

    /**
     * 附件
     */
    private String attachments;

    /**
     * 撮合时间
     */
    private Date matchedDate;

    /**
     * 付款时间
     */
    private Date paymentDate;

    /**
     * 收款时间
     */
    private Date receiveDate;

    /**
     * 实际出款金额
     */
    private BigDecimal actualAmount;

    /**
     * 区块链协议
     */
    private String protocol;

}
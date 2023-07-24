package com.seektop.fund.dto.param.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawMerchantAccountDO implements Serializable {

    private static final long serialVersionUID = 1737834558480594263L;

    /**
     * 账号ID
     */
    private Integer merchantId;

    /**
     * 支付渠道ID
     */
    private Integer channelId;

    /**
     * 渠道名称
     */
    private String channelName;

    /**
     * 商户号
     */
    private String merchantCode;

    /**
     * 支付地址
     */
    private String payUrl;

    /**
     * 通知地址
     */
    private String notifyUrl;

    /**
     * 每日出款上限
     */
    private Integer dailyLimit;

    /**
     * 出款最小金额
     */
    private Integer minAmount;

    /**
     * 出款最大金额
     */
    private Integer maxAmount;

    /**
     * 备注
     */
    private String remark;

    /**
     * 账号状态：0上架，1下架，2已删除
     */
    private Integer status;

    /**
     * 开启状态： 0 已开启、 1 已关闭
     */
    private Integer openStatus;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改人
     */
    private String lastOperator;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;

    /**
     * 公钥
     */
    private String publicKey;

    /**
     * 私钥
     */
    private String privateKey;

    private String displayName;

    /**
     * 三方商户余额
     */
    private BigDecimal balance;


    private Integer merchantFeeType;


    private BigDecimal merchantFee;

}
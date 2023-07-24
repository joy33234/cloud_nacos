package com.seektop.fund.vo;

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
public class GlWithdrawDetailDto implements Serializable {

    private Integer userId;
    /**
     * 提現單號
     */
    private String orderId;

    private Integer userType;
    /**
     * 賬戶名
     */
    private String userName;
    /**
     * 用戶層級
     */
    private String userLevel;

    private BigDecimal amount;

    private BigDecimal fee;

    private String name;

    private String cardNo;

    private String address;

    private Integer withdrawType;

    private Integer status;
    /**
     * 提現時間
     */
    private Date createDate;
    /**
     * 出款時間
     */
    private Date lastUpdate;

    private Integer aisleType;

    private Integer splitStatus;

    private String ip;

    private Integer channelId;

    private String channelName;

    private String merchant;

    private String merchantCode;

    private Date approveTime;
    /**
     * 出款卡姓名
     */
    private String transferName;
    /**
     * 出款卡银行
     */
    private String transferBankName;
    /**
     * 出款银行卡号
     */
    private String transferBankCardNo;

    private String remark;
}
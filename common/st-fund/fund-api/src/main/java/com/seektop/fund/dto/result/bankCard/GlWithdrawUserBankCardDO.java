package com.seektop.fund.dto.result.bankCard;

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
public class GlWithdrawUserBankCardDO implements Serializable {
    /**
     * 银行卡ID
     */
    private Integer cardId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 银行ID
     */
    private Integer bankId;

    /**
     * 银行名称
     */
    private String bankName;

    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 卡号
     */
    private String cardNo;

    /**
     * 开户省市区
     */
    private String address;

    /**
     * 银行卡状态
     *
     * 0未删除
     * 1已删除
     */
    private Integer status;

    /**
     * 是否默认选中
     *
     * 0不是
     * 1是
     */
    private Integer selected;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;
}

package com.seektop.fund.dto.param.bankCard;

import lombok.Data;

import java.io.Serializable;

@Data
public class DeleteBankCardDto implements Serializable {
    /**
     * 用户id
     */
    private Integer userId;
    /**
     * 银行卡id
     */
    private Integer cardId;
    /**
     * 审核状态
     */
    private Integer status;
}

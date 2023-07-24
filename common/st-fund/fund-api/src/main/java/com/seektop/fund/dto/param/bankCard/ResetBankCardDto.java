package com.seektop.fund.dto.param.bankCard;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class ResetBankCardDto implements Serializable {

    /**
     * 用户id
     */
    private Integer userId;
    /**
     * 审核状态
     */
    private Integer status;
    /**
     * 操作id
     */
    private Integer manageId;
    /**
     * 审核人
     */
    private String approver;
    /**
     * 审核备注
     */
    private String remark;
    /**
     * 审核时间
     */
    private Date approverTime;
}

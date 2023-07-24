package com.seektop.fund.dto.param.bankCard;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class BankCardApplyDto implements Serializable {

    /**
     * 操作数据
     */
    private String optData;
    /**
     * 审核状态
     */
    private Integer status;
    /**
     * 银行卡Id
     */
    private Integer bankId;
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

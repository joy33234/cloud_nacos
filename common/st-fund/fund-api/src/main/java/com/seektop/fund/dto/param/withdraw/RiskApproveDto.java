package com.seektop.fund.dto.param.withdraw;

import lombok.Data;

import java.io.Serializable;

@Data
public class RiskApproveDto implements Serializable {
    private static final long serialVersionUID = -1626851735758341685L;

    private Integer userId; // 用户userId
    private Integer userType; // 用户类型
    private Integer status; //（审核通过、审核拒绝）、
    private String remark; // 备注
    private String rejectReason;

    private Integer operatorUserId; // 操作人userId
    private String operator; //操作人
}

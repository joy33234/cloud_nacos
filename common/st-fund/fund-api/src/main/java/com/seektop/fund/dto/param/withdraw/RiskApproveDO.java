package com.seektop.fund.dto.param.withdraw;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;

@Data
public class RiskApproveDO implements Serializable {
    private static final long serialVersionUID = -2896544250997190073L;
    /**
     * 提现订单ID
     */
    private String orderId;
    /**
     * 审核人ID
     */
    private Integer userId;
    /**
     * 审核人用户名
     */
    private String username;
    /**
     * 审核备注
     */
    private String remark;
    /**
     * 审核状态：1通过，2拒绝
     */
    private Integer status;
    /**
     * 创建时间
     */
    private Date createDate;
    /**
     * 拒绝出款原因
     */
    private String rejectReason;
}

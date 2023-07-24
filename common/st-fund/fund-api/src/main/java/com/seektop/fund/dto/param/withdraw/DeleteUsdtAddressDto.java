package com.seektop.fund.dto.param.withdraw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class DeleteUsdtAddressDto implements Serializable {
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

    /**
     * 操作数据
     */
    private String optData;

    /**
     * 是否需要保存manage信息
     */

    private boolean saveManage = true;

}

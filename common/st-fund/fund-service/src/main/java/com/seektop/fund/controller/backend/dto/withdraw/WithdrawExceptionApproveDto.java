package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawExceptionApproveDto implements Serializable {

    private static final long serialVersionUID = 3985002244492983640L;

    /**
     * 提现订单号
     */
    @NotNull(message = "orderIds is null")
    private List<String> orderId;

    /**
     * 审核状态(1通过，2拒绝,4搁置，5取消)
     */
    @NotNull(message = "status is null")
    private Integer status;

    /**
     * 拒绝出款理由
     */
    private String rejectReason;

    /**
     * 审核备注
     */
    @Size(max = 45, message = "备注超出长度")
    private String remark;

    private Date updateTime;

    private Integer systemId; // 子系统
    /**
     * 勾选的标记的id
     */
    private List<Integer> labelIds;
    /**
     * 拒绝时账号操作：0 无操作，1 完全锁定，2 间接锁定
     */
    private Integer operation = 0;

    /**
     * 极速提现转普通提现
     */
    private boolean c2cToNormal = false;
}

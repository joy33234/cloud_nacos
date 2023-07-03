package com.seektop.fund.controller.backend.result.recharge;

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
public class RechargeSuccessRequestResult implements Serializable {

    private static final long serialVersionUID = 2047178961866003150L;
    /**
     * 充值订单号
     */
    private String orderId;

    /**
     * 申请人ID
     */
    private Integer userId;

    /**
     * 申请人用户名
     */
    private String username;

    /**
     * 审核金额
     */
    private BigDecimal amount;

    /**
     * 申请备注
     */
    private String remark;

    /**
     * 申请状态：0待审核，1已通过，2已拒绝
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;

    /**
     * 上传图片地址
     */
    private String reqImg;

    private Boolean isFirst;

}
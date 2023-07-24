package com.seektop.fund.vo;

import com.seektop.fund.model.GlRecharge;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Date;

@Data
public class RechargeVO extends GlRecharge {

    private static final long serialVersionUID = -4012479185962368570L;

    private String paymentName;

    /**
     * 到账金额
     */
    private BigDecimal payAmount;
    /**
     * 申请补单时间
     */
    private Date sucReqTime;
    /**
     * 申请补单操作人
     */
    private String sucReqOperator;
    /**
     * 申请补单金额
     */
    private BigDecimal sucReqAmount;
    /**
     * 申请补单备注
     */
    private String sucReqRemark;
    /**
     * 审核补单时间
     */
    private Date sucApvTime;
    /**
     * 审核补单操作人
     */
    private String sucApvOperator;
    /**
     * 审核补单金额
     */
    private BigDecimal sucApvAmount;
    /**
     * 审核补单备注
     */
    private String sucApvRemark;
    /**
     * 补单审核状态：0待审核，1审核通过，2审核拒绝
     */
    private Integer sucStatus;

    private String reallyName;
    private String telephone;
    /**
     * 原始订单号
     */
    private String originalOrderId;

}
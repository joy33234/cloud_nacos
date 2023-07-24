package com.seektop.fund.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawRecordListQueryDO implements Serializable {

    private static final long serialVersionUID = -7998058591768301862L;
    /**
     * 分页参数
     */
    private Integer page = 1;
    private Integer size = 10;


    /**
     * 查询时间段
     */
    private Date startTime;
    private Date endTime;

    /**
     * 用户类型
     */
    private Integer userType = -1;
    /**
     * 订单号
     */
    private String orderId;

    /**
     * 订单状态
     */
    private Integer status;

    /**
     * 出款方式：-1全部、0:人工出款、1:三方自动出款、2:三方手动出款
     */
    private Integer withdrawType;

    /**
     * 出款商户
     */
    private String merchant;


    /**
     * 用户id,前端无关
     */
    private Integer userId;

    /**
     * 提现银行卡用户姓名
     */
    private String bankUserName;
    private String bankId;

    /**
     * 出款记录：-1:全部、0:待处理、1:已处理
     */
    private Integer transferStatus = -1;

    /**
     * 用户名
     */
    private String userName;


    /**
     *  查询金额区间
     */
    private BigDecimal minAmount;
    private BigDecimal maxAmount;

    /**
     * 币种
     */
    private String coinCode = "-1";

}

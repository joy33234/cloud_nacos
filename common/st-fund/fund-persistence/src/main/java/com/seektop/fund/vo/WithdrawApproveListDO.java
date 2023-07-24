package com.seektop.fund.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawApproveListDO implements Serializable {

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
     * 用户名
     */
    private String userName;

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
     * 订单状态 - 多选
     */
    private List<Integer> withdrawStatus;


    /**
     * 出款方式：-1全部、0:人工出款、1:三方自动出款、2:三方手动出款
     */
    private Integer withdrawType;


    /**
     * 审核状态：null:全部 ,0待审核，1审核通过，2审核拒绝
     */
    private List<Integer> approvalStatus;


    /**
     * 出款商户
     */
    private String merchant;


    private Integer userId;

    private String attachments;

    private String coinCode = "-1";

}

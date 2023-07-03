package com.seektop.fund.vo;

import com.seektop.enumerate.Language;
import lombok.Data;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class RechargePendingQueryDto extends ManageParamBase implements Serializable {

    private static final long serialVersionUID = 5269725920381930276L;

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
    //订单号
    private String orderId;
    //账户名
    private String userName;
    //姓名
    private String reallyName;

    /**
     * 用户类型
     */
    private Integer userType = -1;

    private List<Integer> userIdList;

    /**
     * 0：普通充值，1：大额充值
     */
    private Integer limitType = -1;

    /**
     * 三方商户ChannelID
     */
    private Integer channelId;

    /**
     * 记录界面查询状态
     * 支付状态:0-待支付、1-支付成功、2-补单审核成功、3-补单审核拒绝、4-人工拒绝补单、5-补单审核中、6-用户撤销、7-超时撤销
     */
    private List<Integer> orderStatus;

    /**
     * 充值订单状态
     */
    private List<Integer> status;

    /**
     * 充值订单子状态
     */
    private List<Integer> subStatus;

    private Integer userId;
}

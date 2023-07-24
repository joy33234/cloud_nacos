package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class RechargeQueryDto extends ManageParamBase implements Serializable {


    private static final long serialVersionUID = -1945754394042655209L;
    /**
     * 分页参数
     */
    private Integer page = 1;
    private Integer size = 10;
    /**
     * 查询时间类型：1-充值时间、2-到账时间
     */
    private Integer dateType = 1;
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
     * 支付状态:0-待支付、1-支付成功、2-补单审核成功、3-补单审核拒绝、4-人工拒绝补单、5-补单审核中、6-用户撤销、7-超时撤销、8-待确认到帐、 9-超时未确认到帐
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

    /**
     * 充值到账状态查询：-1：支付成功&补单审核成功, 1：支付成功、2：补单审核成功、
     */
    private Integer succStatus = -1;
    /**
     * 手机号
     */
    private String telephone;

    //收款人姓名
    private String cardUsername;

    //充值方式
    private List<Integer> paymentIdList;

    //操作端
    private List<Integer> clientTypeList;

    private Integer userId;

    //下载列表id
    private Integer exportId;

    //列表搜索关键字 帐号或手机号
    private String keywords;

    //用户id
    private List<Integer> idList;

    private Integer agentType = -1;
    /**
     * 根据用户名查询 0=非必填、1=必填
     */
    private Integer needName = 0;
    /**
     * 是否包含统计
     */
    private Boolean includeTotal = false;


    /**
     * 付款人
     */
    private String fromUsername;


    /**
     * 排序字段：-1.充值时间(创建时间) 0.充值金额 1.手续费 2.到账时间
     */
    private Integer sortType;

    /**
     * 排序 0正序 1倒序
     */
    private Integer sortOrder;

    // 排序拼接
    private String orderByClause;

    /**
     * 排序
     */
    private String sortStr;

    /**
     * 三方商户ID
     */
    private Integer merchantId;

    /**
     * 主状态（客服系统）
     */
    private Boolean mainStatus = false;

    /**
     * 币种编码
     */
    private String coinCode;

    /**
     * 交易哈希值
     */
    private String txHash;

}
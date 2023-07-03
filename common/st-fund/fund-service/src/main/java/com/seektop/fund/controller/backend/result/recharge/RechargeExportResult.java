package com.seektop.fund.controller.backend.result.recharge;

import com.seektop.fund.controller.backend.param.recharge.RechargePayDO;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
public class RechargeExportResult {

    private static final long serialVersionUID = -4307988970937570651L;
    /**
     * 订单ID
     */
    private String orderId;

    /**
     * 用户ID
     */
    private Integer userId;

    /**
     * 用户类型：0会员，1代理
     */
    private Integer userType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 充值金额
     */
    private BigDecimal amount;

    /**
     * 手续费
     */
    private BigDecimal fee;

    /**
     * 到账金额
     */
    private BigDecimal payAmount;

    /**
     * 附言
     */
    private String keyword;

    /**
     * 充值方式ID
     */
    private Integer paymentId;

    /**
     * 充值方式名称
     */
    private String paymentName;

    /**
     * 收款渠道ID或收款银行ID
     */
    private Integer channelId;

    /**
     * 收款渠道名称或收款银行名称
     */
    private String channelName;

    /**
     * 收款账号ID或收款银行卡ID
     */
    private Integer merchantId;

    /**
     * 收款商户号或银行卡号
     */
    private String merchantCode;

    /**
     * 收款账号姓名
     */
    private String merchantName;

    /**
     * 银行ID：0其他
     */
    private Integer bankId;

    /**
     * 银行名称
     */
    private String bankName;

    /**
     * 客户端类型：0PC，1H5，2安卓，3IOS，4PAD
     */
    private Integer clientType;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 支付状态：0：待支付，1：支付成功，2：支付失败，3：补单审核中
     */
    private Integer status;

    /**
     * 支付子状态，用于对状态的补充，根据场景细分列表的展示
     * 1：支付成功，2：补单审核成功，3：补单审核拒绝，4：人工拒绝补单，5：用户撤销，6：超时撤销
     */
    private Integer subStatus;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改时间
     */
    private Date lastUpdate;

    /**
     * 充值额度类型，0：普通充值，1：大额充值
     */
    private Integer limitType;

    /**
     * 加币时间
     */
    private Date payTime;

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
    /**
     * APP类型：1，体育APP
     */
    private Integer appType;

    /**
     * 用户层级名称
     */
    private String userLevel;

    /**
     * 收款银行卡号
     */
    private String cardNo;

    /**
     * 银行卡号，快捷支付时需要
     */
    private String fromCardNo;


    /**
     * 收款人姓名
     */
    private String cardUsername;
    /**
     * 1代客充值  0普通充值
     */
    private Integer agentType;
    /**
     * 开户人姓名，快捷支付时可能需要
     */
    private String fromCardUserName;

    /**
     * 身份证号，快捷支付时可能需要
     */
    private String idNumber;

    /**
     * 手机号码，快捷支付时可能需要
     */
    private String phoneNumber;

    /**
     * 第三方订单号，冗余字段
     */
    private String thirdOrderId;

    /**
     * 是否首次
     */
    private Integer First;

    /**
     * 额外的备注信息，目前用于充值订单被直接拒绝补单的情况
     */
    private String remark;

    /**
     * 充值回显方式： 0 - 正常； 1 - 详情
     */
    private Integer showType;


    private RechargePayDO rechargePay;

    /**
     * 原始订单号
     */
    private String originalOrderId;

    /**
     * 姓名
     */
    private String reallyName;

    /**
     * 电话
     */
    private String telephone;

    /**
     * 数据类型: -1全部, 0创建订单, 1补单申请
     */
    private Integer dataType;

    /**
     * 收款卡姓名
     */
    private String payeeName;

    /**
     * 收款卡银行ID
     */
    private Integer payeeBankId;

    /**
     * 收款卡银行名称
     */
    private String payeeBankName;

    /**
     *新财务系统的备注字段
     */
    private String newRemark;

    /**
     * 用户层级转换
     */
    private String userLevelName;

    /**
     * 关联单号
     */
    private String relationOrderId;

}
package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlWithdrawDO implements Serializable {

    /**
     * 提现订单号
     */
    private String orderId;

    /**
     * 提现用户ID
     */
    private Integer userId;

    /**
     * 账户类型：0玩家，1代理
     */
    private Integer userType;

    /**
     * 用户名
     */
    private String username;

    /**
     * 提现金额
     */
    private BigDecimal amount;

    /**
     * 提现手续费
     */
    private BigDecimal fee;

    /**
     * 提现银行ID
     */
    private Integer bankId;

    /**
     * 提现银行名称
     */
    private String bankName;

    /**
     * 开户人姓名
     */
    private String name;

    /**
     * 提现银行卡号
     */
    private String cardNo;

    /**
     * 卡户省市区
     */
    private String address;

    /**
     * 风险类型：0正常提现，其他风险提现
     */
    private String riskType;

    /**
     * 客户端类型：0PC，1H5，2安卓，3IOS，4PAD
     */
    private Integer clientType;

    /**
     * 出款方式：
     * 0: 人工打款
     * 1: 自动出款
     * 2: 三方手动出款
     */
    private Integer withdrawType;

    /**
     * 状态流转图: https://qenpqs.axshare.com/#g=1&p=%E7%8A%B6%E6%80%81%E6%B5%81%E7%A8%8B%E5%9B%BE
     * <p>
     * 提现状态：
     * -4 搁置
     * -3: 风险待审核
     * -2: 风险审核拒绝
     * 0: 风险审核通过（待出款）
     * 1: 出款成功
     * 2: 出款失败
     * 3: 拒绝出款（退回）
     * 4: 已退回
     * 5: 拒绝退回
     * 6: 申请强制成功中
     * 7: 待处理,自动出款失败
     * 8: 已经强制成功
     * 9: 拒绝强制成功
     * 10: 已处理-三方自动出款中
     * 11: 出款专员处理中
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后更新时间
     */
    private Date lastUpdate;

    /**
     * 风险审核人
     */
    private String riskApprover;

    /**
     * 风险审核时间
     */
    private Date riskApvTime;

    /**
     * 风险审核备注
     */
    private String riskApvRemark;

    /**
     * 提现审核人
     */
    private String approver;

    /**
     * 提现审核时间
     */
    private Date approveTime;

    /**
     * 提现审核备注
     */
    private String remark;

    /**
     * 出款商户名
     */
    private String merchant;

    /**
     * 出款商户ID
     */
    private Integer merchantId;

    /**
     * 出款商户号
     */
    private String merchantCode;
    /**
     * 出款卡姓名
     */
    private String transferName;
    /**
     * 出款卡银行
     */
    private String transferBankName;
    /**
     * 出款银行卡号,风云聚合出款设置卡号
     */
    private String transferBankCardNo;
    /**
     * 用户层级ID
     */
    private String userLevel;
    /**
     * 第三方订单号
     */
    private String thirdOrderId;

    /**
     * 标签（后台用）
     */
    private String tag;

    /**
     * 标识本次提现是否免费（0-免费提现、1-收费提现）
     */
    private Integer freeStatus;

    /**
     * 提现申请类型（1-普通提现、2-快速提现、3-代理提现）
     */
    private Integer aisleType;

    /**
     * 标识本次提现是否拆单（0-未拆单、1-拆单）
     */
    private Integer splitStatus;

    /**
     * IP地址
     */
    private String ip;

    /**
     * 提现批次号
     */
    private String batchNumber;

    /**
     * 提现分单创建人
     */
    private String seperateCreator;

    /**
     * 提现分单处理人
     */
    private String seperator;

    /**
     * 提现分单时间
     */
    private Date seperateDate;

    /**
     * 未分单原因
     */
    private String seperateReason;

    /**
     * 拒绝出款理由
     */
    private String rejectReason;

    /**
     * 设备号
     */
    private String deviceId;

    /**
     * 针对拆单记录:本次提现总金额
     */
    private BigDecimal totalAmount;

    /**
     * 用户状态：0正常，1完全锁定，2间接锁定
     */
    private Integer userLockStatus;

    /**
     * 用户注册时间
     */
    private Date registerDate;

    /**
     * 针对拆单记录:本次提现总手续费
     */
    private BigDecimal totalFee;

    /**
     * 正在查看的人
     */
    private List<String> userList;

    /**
     * 币种
     */
    private String coin;
}

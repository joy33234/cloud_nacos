package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 提现记录
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw")
public class GlWithdraw implements Serializable {

    /**
     * 提现订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 提现用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 账户类型：0玩家，1代理
     */
    @Column(name = "user_type")
    private Integer userType;

    /**
     * 用户名
     */
    @Column(name = "username")
    private String username;

    /**
     * 币种代码
     */
    @Column(name = "coin")
    private String coin;

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
    @Column(name = "bank_id")
    private Integer bankId;

    /**
     * 提现银行名称
     */
    @Column(name = "bank_name")
    private String bankName;

    /**
     * 开户人姓名
     */
    @Column(name = "name")
    private String name;

    /**
     * 提现银行卡号
     */
    @Column(name = "card_no")
    private String cardNo;

    /**
     * 卡户省市区
     */
    private String address;

    /**
     * 风险类型：0正常提现，其他风险提现
     */
    @Column(name = "risk_type")
    private String riskType;

    /**
     * 客户端类型：0PC，1H5，2安卓，3IOS，4PAD
     */
    @Column(name = "client_type")
    private Integer clientType;

    /**
     * 出款方式：
     * 0: 人工打款
     * 1: 自动出款
     * 2: 三方手动出款
     */
    @Column(name = "withdraw_type")
    private Integer withdrawType;

    /**
     * 状态流转图: https://qenpqs.axshare.com/#g=1&p=%E7%8A%B6%E6%80%81%E6%B5%81%E7%A8%8B%E5%9B%BE
     * <p>
     * 提现状态：
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
     * 12: 超时未确认（极速转卡）
     */
    private Integer status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后更新时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

    /**
     * 风险审核人
     */
    @Column(name = "risk_approver")
    private String riskApprover;

    /**
     * 风险审核时间
     */
    @Column(name = "risk_apv_time")
    private Date riskApvTime;

    /**
     * 风险审核备注
     */
    @Column(name = "risk_apv_remark")
    private String riskApvRemark;

    /**
     * 提现审核人
     */
    private String approver;

    /**
     * 提现审核时间
     */
    @Column(name = "approve_time")
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
    @Column(name = "merchant_id")
    private Integer merchantId;

    /**
     * 出款商户号
     */
    @Column(name = "merchant_code")
    private String merchantCode;
    /**
     * 出款卡姓名
     */
    @Column(name = "transfer_name")
    private String transferName;
    /**
     * 出款卡银行
     */
    @Column(name = "transfer_bank_name")
    private String transferBankName;
    /**
     * 出款银行卡号,风云聚合出款设置卡号
     */
    @Column(name = "transfer_bank_card_no")
    private String transferBankCardNo;
    /**
     * 用户层级ID
     */
    @Column(name = "user_level")
    private String userLevel;
    /**
     * 第三方订单号
     */
    @Column(name = "third_order_id")
    private String thirdOrderId;

    /**
     * 标签（后台用）
     */
    @Column(name = "tag")
    private String tag;

    /**
     * 标识本次提现是否免费（0-免费提现、1-收费提现）
     */
    @Column(name = "free_status")
    private Integer freeStatus;

    /**
     * 提现申请类型（1-普通提现、2-快速提现、3-代理提现、4-极速提现）
     */
    @Column(name = "aisle_type")
    private Integer aisleType;

    /**
     * 标识本次提现是否拆单（0-未拆单、1-拆单）
     */
    @Column(name = "split_status")
    private Integer splitStatus;

    /**
         * IP地址
     */
    @Column(name = "ip")
    private String ip;

    /**
     * 提现批次号
     */
    @Column(name = "batch_number")
    private String batchNumber;

    /**
     * 提现分单创建人
     */
    @Column(name = "seperate_creator")
    private String seperateCreator;

    /**
     * 提现分单处理人
     */
    @Column(name = "seperator")
    private String seperator;

    /**
     * 提现分单时间
     */
    @Column(name = "seperate_date")
    private Date seperateDate;

    /**
     * 未分单原因
     */
    @Column(name = "seperate_reason")
    private String seperateReason;

    /**
     * 拒绝出款理由
     */
    @Column(name = "reject_reason")
    private String rejectReason;

    /**
     * 交易哈希值
     */
    @Column(name = "tx_hash")
    private String txHash;

}
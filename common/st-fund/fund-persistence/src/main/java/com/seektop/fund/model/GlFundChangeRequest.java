package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import org.apache.commons.lang3.RegExUtils;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 资金调整申请记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_changereq")
public class GlFundChangeRequest implements Serializable {

    private static final long serialVersionUID = 2322771748907391910L;
    /**
     * 主键ID
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 用户名
     */
    private String username;

    /**
     * 用户类型：0玩家，1代理
     */
    @Column(name = "user_type")
    private Integer userType;

    /**
     * 操作类型 状态码：1009 |加币-计入红利，1018|加币-不计红利，1011|减币
     */
    @Column(name = "change_type")
    private Integer changeType;

    /**
     * 子操作类型 1|红包，2|活动红利，3|人工充值，4|提现失败退回，5|转账补分，" +
     * "6|游戏补分-贝博体育，7|游戏补分-LB彩票，8|上分返利，9|佣金调整，10|系统回扣，11|错误上分扣回。25|虚拟额度
     * 19|线下充送活动
     */
    @Column(name = "sub_type")
    private Integer subType;


    /**
     * 调整金额
     */
    private BigDecimal amount;

    /**
     * 需求流水金额
     */
    @Column(name = "freeze_amount")
    private BigDecimal freezeAmount;

    /**
     * 申请原因
     */
    private String remark;

    /**
     * 审核状态：0待审核，1一审通过，2一审拒绝，3二审通过，4二审拒绝，5搁置
     */
    private Integer status;

    /**
     * 申请人
     */
    private String creator;

    /**
     * 申请时间
     */
    @Column(name = "create_time")
    private Date createTime;

    /**
     * 一审人
     */
    @Column(name = "first_approver")
    private String firstApprover;

    /**
     * 一审时间
     */
    @Column(name = "first_time")
    private Date firstTime;

    /**
     * 一审备注
     */
    @Column(name = "first_remark")
    private String firstRemark;

    /**
     * 二审人
     */
    @Column(name = "second_approver")
    private String secondApprover;

    /**
     * 二审时间
     */
    @Column(name = "second_time")
    private Date secondTime;

    /**
     * 二审备注
     */
    @Column(name = "second_remark")
    private String secondRemark;

    /**
     * 提现额度
     */
    @Column(name = "valid_withdraw")
    private BigDecimal validWithdraw;

    /**
     * 关联的充值订单ID
     */
    @Transient
    private String relationOrderId;

    /**
     * 关联的三方订单ID
     */
    @Transient
    private String thirdOrderId;

    /**
     * 姓名
     */
    @Transient
    private String reallyName;

    /**
     * 收款银行
     */
    @Transient
    private String bankName;

    /**
     * 收款人姓名
     */
    @Transient
    private String name;

    /**
     * 收款商户号
     */
    @Transient
    private String merchantCode;

    /**
     * 收款商户
     */
    @Transient
    private String merchant;

    /**
     * 原订单金额
     */
    @Transient
    private BigDecimal originalOrderAmount;

    /**
     * 代理活动id
     */
    @Transient
    private Integer actId;

    /**
     * 充值金额
     */
    @Transient
    private BigDecimal rechargeAmount;

    /**
     * 资金调整的原因，去掉前缀
     * @return
     */
    public String getFinanceAdjustReason() {
        return RegExUtils.replaceAll(this.remark, "[^,]+,[^,]+,(.+)", "$1");
    }
}
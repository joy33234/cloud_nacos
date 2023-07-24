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

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_fund_proxyaccount")
public class FundProxyAccount implements Serializable {

    private static final long serialVersionUID = 2967275800849895533L;
    /**
     * 代理ID
     */
    @Id
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 代理类型：0 外部代理 (默认),1  内部代理
     */
    @Column(name = "type")
    private Integer type;

    /**
     * 信用额度
     */
    @Column(name = "credit_amount")
    private BigDecimal creditAmount;

    /**
     * 可提现额度
     */
    @Column(name = "valid_withdrawal")
    private BigDecimal validWithdrawal;

    /**
     * 会员代充的权限：1开启(默认),0关闭
     */
    @Column(name = "payout_status")
    private Integer payoutStatus;

    /**
     * 转账下级代理权限：1 开启,0 关闭(默认)
     */
    @Column(name = "transfer_proxy_status")
    private Integer transferProxyStatus;

    /**
     * 转账会员权限：1 开启,0 关闭(默认)
     */
    @Column(name = "transfer_member_status")
    private Integer transferMemberStatus;

    /**
     * 创建下级代理权限：1 开启,0 关闭(默认)
     */
    @Column(name = "create_proxy_status")
    private Integer createProxyStatus;

    /**
     * 更新时间
     */
    @Column(name = "last_update")
    private Date lastUpdate;

}
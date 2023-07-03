package com.seektop.fund.dto.param.proxy;

import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
public class FundProxyAccountDO implements Serializable {
    /**
     * 代理ID
     */
    private Integer userId;

    /**
     * 代理类型：0 外部代理 (默认),1  内部代理
     */
    private Integer type;

    /**
     * 信用额度
     */
    private BigDecimal creditAmount;

    /**
     * 可提现额度
     */
    private BigDecimal validWithdrawal;

    /**
     * 会员代充的权限：1开启(默认),0关闭
     */
    private Integer payoutStatus;

    /**
     * 转账下级代理权限：1 开启,0 关闭(默认)
     */
    private Integer transferProxyStatus;

    /**
     * 转账会员权限：1 开启,0 关闭(默认)
     */
    private Integer transferMemberStatus;

    /**
     * 创建下级代理权限：1 开启,0 关闭(默认)
     */
    private Integer createProxyStatus;

    /**
     * 更新时间
     */
    private Date lastUpdate;
}

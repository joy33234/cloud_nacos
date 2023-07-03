package com.seektop.fund.dto.param.proxy;

import com.seektop.dto.GlAdminDO;
import lombok.*;

import java.io.Serializable;
import java.math.BigDecimal;

@Setter
@Getter
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class QuotaAdjDO implements Serializable {

    private static final long serialVersionUID = 670088220767587701L;

    /**
     * 代理Id
     */
    private Integer proxyId;

    /**
     * 代理用户名
     */
    private String proxyName;

    /**
     * 代理中心钱包
     */
    private FundProxyAccountDO fundProxyAccountDO;

    /**
     * 金额
     */
    private BigDecimal amount;

    /**
     * 调整类型
     */
    private Integer type;

    /**
     * 操作人
     */
    private GlAdminDO adminDO;
}

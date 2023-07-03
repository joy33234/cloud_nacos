package com.seektop.fund.controller.partner.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * @author darren
 * @create 2019-03-30
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class PaymentMerchantResponse implements Serializable {

    private static final long serialVersionUID = 975953608325396824L;
    /**
     * 三方商户应用ID
     */
    private Integer id;
    /**
     * 三方商户ID
     */
    private Integer merchantId;
    /**
     * 三方商户号
     */
    private String merchantCode;
    /**
     * 三方商户渠道ID
     */
    private Integer channelId;
    /**
     * 三方商户渠道名称
     */
    private String channelName;
    /**
     * 通道名称
     */
    private String aisleName;
    /**
     * 充值手续费
     */
    private BigDecimal fee;
    /**
     * 最低金额
     */
    private BigDecimal minAmount;
    /**
     * 最高金额
     */
    private BigDecimal maxAmount;
    /**
     * 支持的银行列表
     */
    private List<PaymentBankResponse> banks;
    /**
     * 是否是内部支付
     */
    private boolean innerPay;
    /**
     * 快捷支付是否需要卡号
     */
    private boolean needCardNo;

    /**
     * 银行卡转账-是否需要姓名
     */
    private boolean needName;
    /**
     * 成功率（万分之）
     */
    private Integer successRate;
    /**
     * 剩余收款额度
     */
    private Long leftAmount;

    /**
     * 虚拟货币新增字段-区块协议
     */
    private Map<String, String> protocolMap;

}

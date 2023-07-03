package com.seektop.fund.controller.backend.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.List;

/**
 * 支付方式
 *
 * @author darren
 * @create 2019-03-30
 */

@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlPaymentResult implements Serializable {

    private static final long serialVersionUID = 789137903557606736L;
    /**
     * 支付方式ID
     */
    private Integer paymentId;

    /**
     * 支付方式名称
     */
    private String paymentName;

    /**
     * 是否是大额充值
     */
    private Integer limitType;

    /**
     * 充值通道
     */
    private List<GlPaymentMerchantResult> merchantList;

}

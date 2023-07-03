package com.seektop.fund.model;

import lombok.Data;

import javax.persistence.*;
import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 自动出款条件的商户设置(WithdrawAutoConditionMerchantAccount)实体类
 *
 * @author makejava
 * @since 2021-06-19 14:44:19
 */
@Data
@Table(name = "withdraw_auto_condition_merchant_account")
public class WithdrawAutoConditionMerchantAccount implements Serializable {
    private static final long serialVersionUID = 338203688614966349L;
    /**
     * id
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;
    /**
     * 自动出款条件id
     */
    @Column(name = "condition_id")
    private Integer conditionId;
    /**
     * 出款商户id
     */
    @Column(name = "merchant_id")
    private Integer merchantId;
    /**
     * 出款最低限额，0 无限制
     */
    @Column(name = "limit_amount")
    private BigDecimal limitAmount;

}


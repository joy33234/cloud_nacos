package com.seektop.fund.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_recharge_payer_monitor_record")
public class RechargePayerMonitorRecord implements Serializable {

    /**
     * 用户ID
     */
    @Id
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 不同付款人姓名次数
     */
    @Column(name = "times")
    private Integer times;

    /**
     * 触发时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 付款人姓名
     */
    @Column(name = "payer_name")
    private String payerName;

}
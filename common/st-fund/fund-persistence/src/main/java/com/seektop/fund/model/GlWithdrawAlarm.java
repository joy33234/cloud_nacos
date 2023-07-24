package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_alarm")
public class GlWithdrawAlarm implements Serializable {


    private static final long serialVersionUID = -4358010603837775432L;
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;


    /**
     * 提现订单号
     */
    @Column(name = "order_id")
    private String orderId;

    @Column(name = "withdraw_card_name")
    private String withdrawCardName;

    @Column(name = "withdraw_card_no")
    private String withdrawCardNo;

    @Column(name = "card_name")
    private String cardName;

    @Column(name = "card_no")
    private String cardNo;

    @Column(name = "create_time")
    private Date createTime;
}

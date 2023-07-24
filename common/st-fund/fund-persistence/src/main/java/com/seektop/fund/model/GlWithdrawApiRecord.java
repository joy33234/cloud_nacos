package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

/**
 * 提现订单API事物控制
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_api_record")
public class GlWithdrawApiRecord implements Serializable {

    private static final long serialVersionUID = 4201815350787986118L;
    /**
     * 提现订单号
     */
    @Id
    @Column(name = "order_id")
    private String orderId;

    /**
     * 操作人
     */
    @Column(name = "username")
    private String username;


    /**
     * 备注
     */
    @Column(name = "remark")
    private String remark;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

}
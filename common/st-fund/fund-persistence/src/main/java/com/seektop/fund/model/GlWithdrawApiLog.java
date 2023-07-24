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
 * 出款订单三方API调用历史记录
 */
@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_apilog")
public class GlWithdrawApiLog implements Serializable {

    private static final long serialVersionUID = -8482307923366072256L;
    /**
     * 自动出款日志ID
     */
    @Id
    @Column(name = "log_id")
    private Integer logId;

    /**
     * 提现交易ID
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 请求报文
     */
    @Column(name = "req_data")
    private String reqData;

    /**
     * 响应报文
     */
    @Column(name = "res_data")
    private String resData;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

}
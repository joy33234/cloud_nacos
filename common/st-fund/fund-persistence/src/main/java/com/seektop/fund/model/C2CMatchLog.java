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
@Table(name = "gl_c2c_match_log")
public class C2CMatchLog implements Serializable {

    @Id
    @Column(name = "id")
    private Integer id;

    /**
     * 订单号
     */
    @Column(name = "order_id")
    private String orderId;

    /**
     * 关联的订单号
     */
    @Column(name = "linked_order_id")
    private String linkedOrderId;

    /***
     * 日志类型
     *
     * @see com.seektop.enumerate.fund.C2CMatchLogTypeEnum
     */
    @Column(name = "type")
    private Short type;

    /**
     * 日志内容
     */
    @Column(name = "content")
    private String content;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

}
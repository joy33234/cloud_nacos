package com.seektop.fund.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;

/**
 * 游戏转账、主播打赏等事件申请记录
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "gl_fund_transfer_record")
public class GlFundTransferRecord implements Serializable {

    @Id
    @Column(name = "id")
    private String id;

    @Column(name = "order_id")
    private String orderId;

    @Column(name = "user_id")
    private Integer userId;
    //1是传入， -1是回滚
    private Integer type;

    //订单金额
    private BigDecimal amount;

    //返回结果  json
    private String result;
    // 0 加币， 1 减币
    @Column(name = "change_type")
    private Integer changeType;

    /**
     * 0 处理中  1 处理完成 2处理失败
     */
    private Integer status;

    private String remark;

    //最后更新时间
    private Date lastupdate;
    /**
     * 查询列表使用
     */
    @Transient
    private String userName;


}
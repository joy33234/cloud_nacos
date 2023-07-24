package com.seektop.fund.model;

import lombok.*;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.Date;

/**
 * 游戏转账、主播打赏等事件响应信息
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Table(name = "gl_fund_report_record")
public class GlFundReportRecord {
    /**
     * 事务id
     */
    @Id
    @Column(name = "transaction_id")
    private String transactionId;

    /**
     * 事件
     */
    private Integer event;

    /**
     * 时间戳
     */
    @Column(name = "report_date")
    private Long reportDate;

    /**
     * 最后更新时间
     */
    private Date lastupdate;

    /**
     * 0 处理中  1 处理完成 2处理失败
     */
    private Integer status;

    /**
     * 回调时间
     */
    @Column(name = "callback_date")
    private Date callbackDate;

    /**
     * 回调内容
     */
    @Column(name = "callback_text")
    private String callbackText;


}
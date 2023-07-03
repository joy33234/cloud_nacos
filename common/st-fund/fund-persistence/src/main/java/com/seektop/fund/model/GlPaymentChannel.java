package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
@Table(name = "gl_payment_channel")
public class GlPaymentChannel implements Serializable {

    private static final long serialVersionUID = 7965571504560692347L;
    /**
     * 渠道ID
     */
    @Id
    @Column(name = "channel_id")
    private Integer channelId;

    /**
     * 渠道名称
     */
    @Column(name = "channel_name")
    private String channelName;

    /**
     * 渠道排序
     */
    private Integer sort;

    /**
     * 渠道状态：0正常，1禁用
     */
    private Integer status;

}
package com.seektop.common.email.dto;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
public class MailSendResult implements Serializable {

    /**
     * 发送时间
     */
    private Date sentDate;

    /**
     * 状态
     *
     * 0：成功
     * 1：失败
     */
    private Integer states;

    /**
     * 报错信息
     */
    private String error;

}
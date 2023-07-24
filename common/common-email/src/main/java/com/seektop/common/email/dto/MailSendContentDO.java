package com.seektop.common.email.dto;

import lombok.Getter;
import lombok.Setter;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

@Getter
@Setter
public class MailSendContentDO implements Serializable {

    /**
     * 邮件发送人
     */
    @NotBlank(message = "邮件发送人不能为空")
    private String from;

    /**
     * 邮件接收人
     */
    @NotBlank(message = "邮件接收人不能为空")
    private String to;

    /**
     * 邮件主题
     */
    @NotBlank(message = "邮件主题不能为空")
    private String subject;

    /**
     * 邮件内容
     */
    @NotBlank(message = "邮件内容不能为空")
    private String content;

}
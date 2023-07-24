package com.seektop.common.email.configure;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class MailConfigDO implements Serializable {

    /**
     * SMTP服务器地址
     */
    private String host;

    /**
     * SMTP服务器端口号
     */
    private int port;

    /**
     * 邮箱用户名
     */
    private String username;

    /**
     * 密码
     */
    private String password;

    /**
     * 发件人邮箱地址
     */
    private String fromEmail;

}
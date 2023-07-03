package com.seektop.common.email.adapter;

import com.seektop.common.email.configure.MailConfigDO;
import com.seektop.common.email.dto.MailSendContentDO;
import com.seektop.common.email.dto.MailSendResult;
import com.seektop.constant.ContentType;
import com.sun.mail.util.MailSSLSocketFactory;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import javax.mail.*;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.security.GeneralSecurityException;
import java.util.Date;
import java.util.Properties;

@Slf4j
@Component
public class MailAdapter {

    public MailSendResult sendMultipleContent(String subject, String content, String to, MailConfigDO mailConfig) {
        MailSendResult result = new MailSendResult();
        try {
            // 获取邮件服务器配置
            Properties props = getConfigureProperties(mailConfig);
            Authenticator auth = new SMTPAuthenticator(mailConfig.getFromEmail(), mailConfig.getPassword());
            // 创建会话
            Session session = Session.getInstance(props, auth);
            MimeMessage msg = new MimeMessage(session);
            msg.setFrom(new InternetAddress(mailConfig.getFromEmail()));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(to));
            msg.setSubject(subject);
            msg.setContent(content, ContentType.HTML);
            Transport.send(msg);
            result.setStates(0);
            result.setSentDate(new Date());
        } catch (Exception ex) {
            result.setStates(1);
            result.setError(ex.getMessage());
            log.error("富文本邮件发送异常", ex);
        }
        return result;
    }

    public MailSendResult send(@Validated MailSendContentDO mailSendContent, MailConfigDO mailConfig) {
        MailSendResult result = new MailSendResult();
        try {
            // 获取邮件服务器配置
            Properties props = getConfigureProperties(mailConfig);
            Authenticator auth = new SMTPAuthenticator(mailConfig.getFromEmail(), mailConfig.getPassword());
            // 创建会话
            Session session = Session.getInstance(props, auth);
            MimeMessage msg = new MimeMessage(session);
            msg.setText(mailSendContent.getContent());
            msg.setSubject(mailSendContent.getSubject());
            msg.setFrom(new InternetAddress(mailConfig.getFromEmail()));
            msg.addRecipient(Message.RecipientType.TO, new InternetAddress(mailSendContent.getTo()));
            Transport.send(msg);
            result.setStates(0);
            result.setSentDate(new Date());
        } catch (Exception e) {
            result.setStates(1);
            result.setError(e.getMessage());
            log.error("邮件发送异常", e);
        }
        return result;
    }

    private Properties getConfigureProperties(MailConfigDO mailConfig) throws GeneralSecurityException {
        Properties props = new Properties();
        MailSSLSocketFactory sf = new MailSSLSocketFactory();
        sf.setTrustAllHosts(true);
        props.put("mail.smtp.user", mailConfig.getFromEmail());
        props.put("mail.smtp.ssl.enable", "true");
        props.put("mail.smtp.ssl.socketFactory", sf);
        props.setProperty("mail.smtp.host", mailConfig.getHost());
        props.setProperty("mail.smtp.auth", "true");
        props.setProperty("mail.debug", "true");
        props.setProperty("mail.smtp.timeout", "25000");
        props.setProperty("mail.smtp.port", String.valueOf(mailConfig.getPort()));
        props.setProperty("mail.smtp.socketFactory.port", String.valueOf(mailConfig.getPort()));
        props.setProperty("mail.smtp.socketFactory.fallback", "false");
        props.setProperty("mail.smtp.socketFactory.class", "javax.net.ssl.SSLSocketFactory");
        return props;
    }

    private class SMTPAuthenticator extends javax.mail.Authenticator {

        private String username, password;

        public SMTPAuthenticator(String username, String password) {
            this.username = username;
            this.password = password;
        }

        public PasswordAuthentication getPasswordAuthentication() {
            return new PasswordAuthentication(this.username, this.password);
        }
    }

}
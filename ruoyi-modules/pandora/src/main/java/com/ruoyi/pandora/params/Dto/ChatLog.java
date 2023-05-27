package com.ruoyi.pandora.params.Dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 *
 * @author ruoyi
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatLog implements Serializable
{
    private static final long serialVersionUID = 12212354123L;

    private Long chatLogType;
    private Long senderGuId;
    private Long receiverGuId;
    private String chatLogContent;
    private String sender;
    private Date chatLogSendTime;



}

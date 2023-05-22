package com.ruoyi.system.domain.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.ruoyi.system.api.domain.SysDept;
import com.ruoyi.system.domain.SysMenu;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

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

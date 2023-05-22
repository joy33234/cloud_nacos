package com.ruoyi.system.service.impl;


import com.ruoyi.common.core.constant.RedisConstants;
import com.ruoyi.common.core.utils.DateUtil;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.system.domain.vo.ChatLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Component
@Slf4j
public class ChatBusiness {


    @Autowired
    private RedisService redisService;

    public List<ChatLog> updateChat(ChatLog chatLog) {
        Date now = new Date();
        chatLog.setChatLogSendTime(now);
        ChatLog gptRes = getGptContent(chatLog);
        String key = RedisConstants.CHAT_USER + chatLog.getSenderGuId();
        List<ChatLog> chatLogList = redisService.getCacheList(key);
        log.info(chatLogList.size() + "--1");

        chatLogList.add(0,chatLog);
        chatLogList.add(0,gptRes);
        log.info(chatLogList.size() + "=--2");

        chatLogList.sort((b, a) -> (int) (a.getChatLogSendTime().getTime() - b.getChatLogSendTime().getTime()));
        if (chatLogList.size() > 10) {
            chatLogList = chatLogList.subList(0,9);
        }
        log.info(chatLogList.size() + "--3");
        redisService.setCacheList(RedisConstants.CHAT_USER + chatLog.getSenderGuId() ,chatLogList);
        return chatLogList;
    }

    private ChatLog getGptContent(ChatLog chatLog) {
        return new ChatLog(1L,-1L,chatLog.getSenderGuId(),"gptAnswer","gpt", DateUtil.addMinutes(chatLog.getChatLogSendTime() ,1));
    }

}

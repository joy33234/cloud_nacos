package com.ruoyi.pandora.business;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.pandora.domain.PandoraOpenaiUser;
import com.ruoyi.pandora.mapper.PandoraOpenaiUserMapper;
import com.ruoyi.pandora.params.Dto.ChatLog;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.*;

/**
 * openai user
 * 
 * @author ruoyi
 */
@Component
@Slf4j
public class OpenAiBusiness extends ServiceImpl<PandoraOpenaiUserMapper, PandoraOpenaiUser> {

    @Resource
    private PandoraOpenaiUserMapper openAiUserMapper;

    @Resource
    private OpenAiKeyBusiness openAiKeyBusiness;

    /**
     * 根据部门ID查询信息
     * 
     * @param userId 部门ID
     * @return 部门信息
     */
    public PandoraOpenaiUser selectAiByUserId(Long userId) {
        return openAiUserMapper.selectAiByUserId(userId);
    }

    public R<?>  updateChat(ChatLog chatLog) {
        if (checkUserBalance() == false) {
            return R.fail("试用已结束，请充值。");
        }
        ChatLog chatGptChat = getGptContent(chatLog);
        return R.ok(Arrays.asList(chatLog,chatGptChat));
    }

    private ChatLog getGptContent(ChatLog chatLog) {
        return new ChatLog(1L,-1L,chatLog.getSenderGuId(),getChatGptRes(chatLog.getChatLogContent()),"gpt", null);
    }

    public void initOpenAiUser (Long userId, String username) {
        PandoraOpenaiUser userOpenAi = openAiUserMapper.selectAiByUserId(userId);
        if (ObjectUtils.isEmpty(userOpenAi)) {
            return;
        }
        openAiUserMapper.insert(new PandoraOpenaiUser(userId,username,0,10));
    }

    public boolean checkUserBalance() {
        PandoraOpenaiUser sysUserAi = selectAiByUserId(SecurityUtils.getUserId());
        if (sysUserAi == null) {

            initOpenAiUser(SecurityUtils.getUserId(), SecurityUtils.getUsername());
            return true;
        }
        if (sysUserAi.getType() == 0 && sysUserAi.getCount() <= 0) {
            return false;
        }
        return true;
    }

    public String getChatGptRes (String question) {

        String result = "连接异常，请重试";
        try {
            Map<String, Object> params = new HashMap(8);
            params.put( "model" , "gpt-3.5-turbo");

            JSONObject jsonObject = new JSONObject();
            jsonObject.put("role", "user");
            jsonObject.put("content", question);
            JSONArray array = new JSONArray();
            array.add(jsonObject);
            params.put("messages", array);

            String str = HttpUtil.postOpenAi("/v1/chat/completions", params, openAiKeyBusiness.getkey());
            log.info("getChatGptRes request Response:{}", str);
            JSONObject resJSON = JSONObject.parseObject(str);
            if (resJSON == null) {
                log.info("postOpenAi  gpt request err res:{}", str);
            }

            JSONArray jsonArray = resJSON.getJSONArray("choices");
            String message =  jsonArray.getJSONObject(0).getString("message");
            result = JSONObject.parseObject(message).getString("content");
        } catch (Exception e) {
            log.error("getChatGptRes error:{} ",e);
        }
        return result ;
    }

}

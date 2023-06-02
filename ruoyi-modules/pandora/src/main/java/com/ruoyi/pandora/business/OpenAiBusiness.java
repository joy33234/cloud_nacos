package com.ruoyi.pandora.business;

import cn.hutool.core.util.NumberUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.StringUtils;
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
        log.info("updateChat:{}",JSON.toJSONString(chatGptChat));
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
            log.info("getChatGptRes request params:{}", question);

            String str = HttpUtil.postOpenAi("/v1/chat/completions", params, openAiKeyBusiness.getkey());
            log.info("getChatGptRes request Response:{}", str);
            JSONObject resJSON = JSONObject.parseObject(str);
            if (resJSON == null) {
                log.info("postOpenAi request err:{}", str);
            }

            JSONArray jsonArray = resJSON.getJSONArray("choices");
            String message =  jsonArray.getJSONObject(0).getString("message");
            result = JSONObject.parseObject(message).getString("content");
        } catch (Exception e) {
            log.error("getChatGptRes error:{} ",e);
        }
        log.info("getChatGptRes result:{}", result);
        return result;
    }


    public static void main(String[] args) {
        String str = "{\"id\":\"chatcmpl-7MpobZZe9lBM5sKEdSSVtQzfwijF8\",\"object\":\"chat.completion\",\"created\":1685676749,\"model\":\"gpt-3.5-turbo-0301\",\"usage\":{\"prompt_tokens\":113,\"completion_tokens\":37,\"total_tokens\":150},\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"In autumn, yellow leaves fall down from the sky like a dream. Stepping on these fragile yellow leaves, making a crisp sound, creates an indescribable sense of loneliness.\"},\"finish_reason\":\"stop\",\"index\":0}]}";
        JSONObject resJSON = JSONObject.parseObject(str);
        if (resJSON == null) {
            log.info("postOpenAi request err:{}", str);
        }

        JSONArray jsonArray = resJSON.getJSONArray("choices");
        String message =  jsonArray.getJSONObject(0).getString("message");
        System.out.println(JSONObject.parseObject(message).getString("content"));

    }
    public String formatRes(String res ) {
        StringBuilder stringBuilder = new StringBuilder();
        try {
            if (StringUtils.isEmpty(res)) {
                return "";
            }
            List<String> list = Arrays.asList(".",")");
            for (String str: list) {
                int index = res.indexOf(str);
                while (index != -1) {
                    String number = res.substring(index -1, index);
                    if (NumberUtil.isNumber(number)) {
                        stringBuilder.append(res.substring(0,index - 1) + "\r\n" + res.substring(index -1,index + 1) );
                        res = res.substring(index + 1 );
                    }
                    index = res.indexOf (str);
                    if (index == -1) {
                        stringBuilder.append(res);
                    }
                }
            }
        } catch (Exception e) {
            log.error("formatRes erros:{}",e);
        }
       return StringUtils.isEmpty(stringBuilder.toString()) ? res : stringBuilder.toString();
    }
}

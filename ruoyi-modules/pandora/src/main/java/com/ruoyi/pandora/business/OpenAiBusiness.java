package com.ruoyi.pandora.business;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.shaded.com.google.common.collect.Lists;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.core.utils.HttpUtil;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.core.utils.file.FileTypeUtils;
import com.ruoyi.common.core.utils.file.MimeTypeUtils;
import com.ruoyi.common.security.utils.SecurityUtils;
import com.ruoyi.pandora.domain.PandoraOpenaiUser;
import com.ruoyi.pandora.mapper.PandoraOpenaiUserMapper;
import com.ruoyi.pandora.params.Dto.ChatLog;
import com.ruoyi.pandora.utils.Common;
import com.ruoyi.system.api.RemoteFileService;
import com.ruoyi.system.api.domain.DownLoad;
import com.ruoyi.system.api.domain.SysFile;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.ObjectUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.URL;
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

    @Autowired
    private RemoteFileService remoteFileService;

    @Resource
    private Common common;



    @Value("${pandora.chat.image.size}")
    public String size;


    @Value("${pandora.chat.image.n}")
    public Integer n;

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
        ChatLog chatGptChat = new ChatLog(1L,-1L,chatLog.getSenderGuId(),getChatRes(chatLog.getChatLogContent()),"gpt", null,null);
        return R.ok(Arrays.asList(chatLog,chatGptChat));
    }


    public R<?>  fix(ChatLog chatLog) {
        if (checkUserBalance() == false) {
            return R.fail("试用已结束，请充值。");
        }
        ChatLog chatGptChat = new ChatLog(1L,-1L,chatLog.getSenderGuId(),getFixRes(chatLog.getChatLogContent()),"gpt", null,null);
        return R.ok(Arrays.asList(chatLog,chatGptChat));
    }


    public R<?>  images(ChatLog chatLog) {
        if (checkUserBalance() == false) {
            return R.fail("试用已结束，请充值。");
        }
        List<String> urls = Lists.newArrayList();
        if (CollectionUtil.isNotEmpty(chatLog.getImages())) {
            urls = getImagesResWithOriginal(chatLog.getChatLogContent(),chatLog.getImages().get(0));
        } else {
            urls = getImagesRes(chatLog.getChatLogContent());
        }
        ChatLog chatGptChat = new ChatLog(1L,-1L,chatLog.getSenderGuId(),null,"gpt", null, urls);
        return R.ok(Arrays.asList(chatLog,chatGptChat));
    }


    public R<?>  imagesUpload(MultipartFile file) {
        String url = "";
        try {
            if (!file.isEmpty())  {
                String extension = FileTypeUtils.getExtension(file);
                if (!StringUtils.equalsAnyIgnoreCase(extension, MimeTypeUtils.IMAGE_EXTENSION)) {
                    return R.fail("文件格式不正确，请上传" + Arrays.toString(MimeTypeUtils.IMAGE_EXTENSION) + "格式");
                }
                R<SysFile> fileResult = remoteFileService.upload(file);
                if (StringUtils.isNull(fileResult) || StringUtils.isNull(fileResult.getData())) {
                    return R.fail("文件服务异常，请联系管理员");
                }
                url = fileResult.getData().getUrl();
            }

        } catch (Exception e) {
            log.error("imagesUpload error:{} ",e);
        }
        return R.ok(url);
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

    public String getChatRes (String question) {

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

            JSONObject resJSON = getGptResponse(params, "/v1/chat/completions");
            if (resJSON == null) {
                log.info("postOpenAi request err");
                return result;
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



    public String getFixRes (String input) {

        String result = "连接异常，请重试";
        try {
            Map<String, Object> params = new HashMap(3);
            params.put( "model" , "text-davinci-edit-001");
            params.put( "input" , input);
            params.put( "instruction" , "Fix the spelling mistakes");

            JSONObject resJSON = getGptResponse(params, "/v1/edits");
            if (resJSON == null) {
                log.info("postOpenAi request err");
                return result;
            }
            JSONArray jsonArray = resJSON.getJSONArray("choices");
            result =  jsonArray.getJSONObject(0).getString("text");
        } catch (Exception e) {
            log.error("getChatGptRes error:{} ",e);
        }
        return result;
    }


    public List<String> getImagesRes (String input) {

        List<String> urls = Lists.newArrayList();
        try {
            Map<String, Object> params = new HashMap(3);
            params.put( "size" , size);
            params.put( "prompt" , input);
            params.put( "n" , n);

            JSONObject resJSON = getGptResponse(params, "/v1/images/generations");
            if (resJSON == null) {
                log.info("postOpenAi request err");
                return urls;
            }
            JSONArray jsonArray = resJSON.getJSONArray("data");
            for (int i = 0; i < jsonArray.size(); i++) {
                String gptImageUrl = jsonArray.getJSONObject(i).getString("url");
                //下载到本地
                List<String> downloadUrls = remoteFileService.getDownUrl(new DownLoad(Arrays.asList(gptImageUrl))).getData();
                urls.addAll(downloadUrls);
            }
        } catch (Exception e) {
            log.error("getChatGptRes error:{} ",e);
        }
        return urls;
    }

    public List<String> getImagesResWithOriginal (String input,String original) {

        List<String> urls = Lists.newArrayList();
        try {
            Map<String, Object> params = new HashMap(3);
            params.put( "size" , size);
            params.put( "prompt" , input);
            params.put( "image" , original);
            params.put( "n" , n);

            JSONObject resJSON = getGptResponse(params, "/v1/images/edits");
            if (resJSON == null) {
                log.info("getImagesResWithOriginal request err");
                return urls;
            }
            JSONArray jsonArray = resJSON.getJSONArray("data");
            for (int i = 0; i < jsonArray.size(); i++) {
                String gptImageUrl = jsonArray.getJSONObject(i).getString("url");
                //下载到本地
                List<String> downloadUrls = remoteFileService.getDownUrl(new DownLoad(Arrays.asList(gptImageUrl))).getData();
                urls.addAll(downloadUrls);
            }
        } catch (Exception e) {
            log.error("getImagesResWithOriginal error:{} ",e);
        }
        return urls;
    }

    public JSONObject getGptResponse(Map<String, Object> params, String url) {
        JSONObject resJSON = new JSONObject();
        try {
            log.info("getChatGptRes request params:{}", JSON.toJSONString(params));
            String result = HttpUtil.postOpenAi(url, params, openAiKeyBusiness.getkey());
            log.info("getChatGptRes request Response:{}", result);
            resJSON = JSONObject.parseObject(result);
        } catch (Exception e) {
            log.error("getChatGptRes error:{} ",e);
        }
        return resJSON;

    }
}

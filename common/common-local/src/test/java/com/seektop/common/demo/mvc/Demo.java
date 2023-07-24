package com.seektop.common.demo.mvc;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.BaseTest;
import com.seektop.common.demo.mvc.model.ActivityParam;
import com.seektop.common.local.base.dto.LanguageDTO;

import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import javax.annotation.Resource;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

@Slf4j
@AutoConfigureMockMvc
@RunWith(SpringJUnit4ClassRunner.class)
public class Demo extends BaseTest {

    @Resource
    private MockMvc mockMvc;

    @Test
    public void testJSON() throws Exception {
        ActivityParam activityParam = new ActivityParam();
        final LanguageDTO nameLocal = new LanguageDTO();
        nameLocal.put("en","English name");
        nameLocal.put("zh_CN","中文名字");
        activityParam.setNameLocal(nameLocal);

        final String content = JSONObject.toJSONString(activityParam);
        log.info("传递的参数：{}",content);
        final MvcResult mvcResult = mockMvc.perform(post("/local/test/json/add")
                .content(content)
                .contentType(MediaType.APPLICATION_JSON))
                .andReturn();

    }

    @Test
    public void testForm() throws Exception {
        final MvcResult mvcResult = mockMvc.perform(post("/local/test/form/add")
                .content("nameEn=english&nameZh_cn=sdjkj&&id=3")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED))
                .andReturn();

    }
}

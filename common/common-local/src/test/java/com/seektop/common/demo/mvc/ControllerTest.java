package com.seektop.common.demo.mvc;

import com.alibaba.fastjson.JSONObject;
import com.seektop.common.demo.mvc.model.ActivityParam;
import com.seektop.common.demo.mvc.model.ActivityParamForm;
import com.seektop.common.local.base.annotation.LanguageBackendParam;
import com.seektop.common.local.base.dto.LanguageDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("local/test")
public class ControllerTest {


    /**
     * json传参
     * @param activityParam
     */
    @PostMapping("json/add")
    public void mockAdd(@RequestBody ActivityParam activityParam){
        log.info("接受到了前端传递的参数：{}",activityParam);
        final JSONObject enable = activityParam.getNameLocal().getEnable();
        log.info("接受到了目前支持name字段支持的多语言：{}",enable);

    }

    @PostMapping("form/add")
    public void mockAdd(ActivityParamForm activityParam,
                        @LanguageBackendParam(name = "name") LanguageDTO nameLocal){
        log.info("接受到了前端传递的参数：{}",activityParam);

        log.info("接受到了目前支持name字段支持的多语言：{}",nameLocal);
    }
}

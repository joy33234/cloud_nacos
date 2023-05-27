package com.ruoyi.pandora.business;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.pandora.domain.OpenAiKey;
import com.ruoyi.pandora.mapper.OpenAiKeyMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * openai user
 * 
 * @author ruoyi
 */
@Component
@Slf4j
public class OpenAiKeyBusiness extends ServiceImpl<OpenAiKeyMapper, OpenAiKey> {

    /**
     * openai keys
     */
    @Value("${file.keys}")
    public String keys;


    public String getkey() {
        String[] keyArr = StringUtils.split(keys,",");
        Integer keyIndex = RandomUtils.nextInt(0,keyArr.length);
        return keyArr[keyIndex];
    }

}

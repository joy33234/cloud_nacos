package com.seektop.common.nacos.adapter;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.seektop.enumerate.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class LanguageResourcesConfigAdapter {

    private final static String dataId = "language-resources-config";

    private final NacosConfigProperties properties;

    private JSONObject configObj;

    public String getContent(String key, Language language) {
        if (configObj.containsKey(key) == false) {
            return null;
        }
        return configObj.getJSONObject(key).getString(language.getCode());
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        configObj = JSON.parseObject(sourceData);
    }

    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(dataId, properties.getGroup(), new RuleListener());
            String sourceData = configService.getConfig(dataId, properties.getGroup(), properties.getTimeout());
            updateCache(sourceData);
        } catch (NacosException e) {
            log.error("LanguageResourcesConfigAdapter.init()", e);
        }
    }

    class RuleListener implements Listener {

        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            updateCache(configInfo);
        }

    }

    public static class Key {

        /**
         * 聊天室充值限制提示
         */
        public final static String CHAT_RECHARGE_RULE_TIPS = "CHAT_RECHARGE_RULE_TIPS";

        /**
         * 聊天室VIP限制提示
         */
        public final static String CHAT_VIP_RULE_TIPS = "CHAT_VIP_RULE_TIPS";

    }

}
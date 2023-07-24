package com.seektop.fund.common;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class FirstRechargeSuccessUserLevelChangeConfigAdapter {

    @Autowired
    private NacosConfigProperties properties;

    /**
     * Nacos上的DataId
     */
    private final static String ruleDataId = "first-recharge-success-user-leve-change-config";

    private JSONObject configObj;

    /**
     * 获取变更的目标层级
     *
     * @return
     */
    public Integer getTargetUserLevelId() {
        if (ObjectUtils.isEmpty(configObj)) {
            return null;
        }
        if (configObj.containsKey("targetUserLevelId") == false) {
            return null;
        }
        return configObj.getInteger("targetUserLevelId");
    }

    /**
     * 检查用户层级是否在配置范围内
     *
     * @param userLevelId
     * @return
     */
    public Boolean checkSourceUserLevel(Integer userLevelId) {
        if (ObjectUtils.isEmpty(configObj)) {
            return false;
        }
        if (configObj.containsKey("sourceUserLevelId") == false) {
            return false;
        }
        JSONArray sourceUserLevelIdArray = configObj.getJSONArray("sourceUserLevelId");
        if (CollectionUtils.isEmpty(sourceUserLevelIdArray)) {
            return false;
        }
        return sourceUserLevelIdArray.contains(userLevelId);
    }

    /**
     * 是否启用
     *
     * @return
     */
    public Boolean isEnable() {
        if (ObjectUtils.isEmpty(configObj)) {
            return false;
        }
        return configObj.containsKey("isEnable") ? configObj.getBoolean("isEnable") : false;
    }

    protected void updateConfig(String source) {
        if (StringUtils.isEmpty(source)) {
            return;
        }
        configObj = JSON.parseObject(source);
    }

    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(ruleDataId, properties.getGroup(), new RuleListener());
            String ruleSource = configService.getConfig(ruleDataId, properties.getGroup(), properties.getTimeout());
            updateConfig(ruleSource);
        } catch (NacosException e) {
            log.error("FirstRechargeSuccessUserLevelChangeConfigAdapter.init()", e);
        }
    }

    class RuleListener implements Listener {

        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            log.debug("RuleListener收到的内容是{}", configInfo);
            updateConfig(configInfo);
        }

    }

}
package com.seektop.common.nacos;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.seektop.dto.AreaCodeConfigDO;
import com.seektop.dto.AreaCodeDO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class AreaCodeConfig {

    @Autowired
    private NacosConfigProperties properties;

    private final List<String> commonAreaCode = Lists.newArrayList();

    private final String ruleDataId = "area-code-config";
    private List<AreaCodeConfigDO> areaCodeList = Lists.newArrayList();
    private List<AreaCodeConfigDO> commonList = Lists.newArrayList();
    private Set<String> areaCodeSet = Sets.newHashSet();

    /**
     * 检查是否是常用的有效区号
     *
     * @param telArea
     * @return
     */
    public boolean checkSupportCommonAreaCode(final String telArea) {
        return commonAreaCode.contains(telArea);
    }

    /**
     * 检查区号是否支持
     *
     * @param telArea
     * @return
     */
    public boolean checkSupportAreaCode(final String telArea) {
        if (CollectionUtils.isEmpty(areaCodeSet)) {
            return false;
        }
        return areaCodeSet.contains(telArea);
    }

    public List<AreaCodeDO> getBackendCommonAreaCode() {
        List<AreaCodeDO> resultList = Lists.newArrayList();
        for (AreaCodeConfigDO codeConfig : commonList) {
            if (codeConfig.getIsBackend() == false) {
                continue;
            }
            resultList.add(new AreaCodeDO(codeConfig.getCode(), codeConfig.getZhName(), codeConfig.getEnName(), codeConfig.getVietnamName()));
        }
        return resultList;
    }

    public List<AreaCodeDO> getForeheadCommonAreaCode() {
        List<AreaCodeDO> resultList = Lists.newArrayList();
        for (AreaCodeConfigDO codeConfig : commonList) {
            if (codeConfig.getIsForehead() == false) {
                continue;
            }
            resultList.add(new AreaCodeDO(codeConfig.getCode(), codeConfig.getZhName(), codeConfig.getEnName(),codeConfig.getVietnamName()));
        }
        return resultList;
    }

    public List<AreaCodeDO> getBackendAreaCode() {
        List<AreaCodeDO> resultList = Lists.newArrayList();
        for (AreaCodeConfigDO codeConfig : areaCodeList) {
            if (codeConfig.getIsBackend() == false) {
                continue;
            }
            resultList.add(new AreaCodeDO(codeConfig.getCode(), codeConfig.getZhName(), codeConfig.getEnName(),codeConfig.getVietnamName()));
        }
        return resultList;
    }

    public List<AreaCodeDO> getForeheadAreaCode() {
        List<AreaCodeDO> resultList = Lists.newArrayList();
        for (AreaCodeConfigDO codeConfig : areaCodeList) {
            if (codeConfig.getIsForehead() == false) {
                continue;
            }
            resultList.add(new AreaCodeDO(codeConfig.getCode(), codeConfig.getZhName(), codeConfig.getEnName(),codeConfig.getVietnamName()));
        }
        return resultList;
    }

    private void updateAreaConfig(String source) {
        synchronized (areaCodeList) {
            areaCodeList.clear();
            areaCodeSet.clear();
            commonList.clear();
            JSONArray areaArray = JSON.parseArray(source);
            for (int i = 0, len = areaArray.size(); i < len; i++) {
                JSONObject areaObj = areaArray.getJSONObject(i);
                AreaCodeConfigDO areaCodeConfig = new AreaCodeConfigDO();
                areaCodeConfig.setCode(areaObj.getString("code"));
                areaCodeConfig.setEnName(areaObj.getString("en_name"));
                areaCodeConfig.setZhName(areaObj.getString("zh_name"));
                areaCodeConfig.setIsBackend(areaObj.getBoolean("isBackend"));
                areaCodeConfig.setIsForehead(areaObj.getBoolean("isForehead"));
                areaCodeConfig.setVietnamName(areaObj.getString("vietnamName"));
                areaCodeList.add(areaCodeConfig);
                areaCodeSet.add(areaCodeConfig.getCode());
                if (areaCodeConfig.getIsForehead()) {
                    commonAreaCode.add(areaCodeConfig.getCode());
                    commonList.add(areaCodeConfig);
                }
            }
        }
    }

    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(ruleDataId, properties.getGroup(), new RuleListener());
            String ruleSource = configService.getConfig(ruleDataId, properties.getGroup(), properties.getTimeout());
            log.debug("规则加载成功:{}", ruleSource);
            updateAreaConfig(ruleSource);
        } catch (NacosException e) {
            log.error("AreaCodeHandler.init()", e);
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
            updateAreaConfig(configInfo);
        }
    }

}
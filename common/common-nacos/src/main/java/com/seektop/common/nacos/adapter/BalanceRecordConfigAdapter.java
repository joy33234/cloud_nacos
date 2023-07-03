package com.seektop.common.nacos.adapter;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.seektop.dto.adapter.BalanceRecordSubTypeNacosDO;
import com.seektop.dto.adapter.BalanceRecordTypeNacosDO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class BalanceRecordConfigAdapter {

    private final static String dataId = "balance-record-config";

    private final NacosConfigProperties properties;

    private Map<Integer, BalanceRecordTypeNacosDO> typeMap = Maps.newHashMap();

    private Map<Integer, BalanceRecordSubTypeNacosDO> subTypeMap = Maps.newHashMap();

    public BalanceRecordTypeNacosDO getBalanceRecordTypeNacosDO(Integer typeId) {
        return typeMap.get(typeId);
    }

    public BalanceRecordSubTypeNacosDO getBalanceRecordSubTypeNacosDO(Integer subTypeId) {
        return subTypeMap.get(subTypeId);
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        JSONObject configObj = JSON.parseObject(sourceData);
        JSONArray typeArray = configObj.getJSONArray("types");
        synchronized (typeMap) {
            typeMap.clear();
            BalanceRecordTypeNacosDO typeNacosDO;
            for (int i = 0, len = typeArray.size(); i < len; i++) {
                JSONObject typeObj = typeArray.getJSONObject(i);
                typeNacosDO = new BalanceRecordTypeNacosDO();
                typeNacosDO.setId(typeObj.getInteger("id"));
                typeNacosDO.setIsViewInForehead(typeObj.getBoolean("isViewInForehead"));
                typeNacosDO.setIsViewInBackend(typeObj.getBoolean("isViewInBackend"));
                typeNacosDO.setName(typeObj.getJSONObject("name"));
                typeMap.put(typeNacosDO.getId(), typeNacosDO);
            }
        }
        JSONArray subTypeArray = configObj.getJSONArray("subTypes");
        synchronized (subTypeMap) {
            subTypeMap.clear();
            List<BalanceRecordSubTypeNacosDO> subTypeList;
            BalanceRecordTypeNacosDO typeNacosDO; BalanceRecordSubTypeNacosDO subTypeNacosDO;
            for (int i = 0, len = subTypeArray.size(); i < len; i++) {
                JSONObject subTypeObj = subTypeArray.getJSONObject(i);
                subTypeNacosDO = new BalanceRecordSubTypeNacosDO();
                subTypeNacosDO.setId(subTypeObj.getInteger("id"));
                subTypeNacosDO.setParentId(subTypeObj.getInteger("parentId"));
                subTypeNacosDO.setIsViewInForehead(subTypeObj.getBoolean("isViewInForehead"));
                subTypeNacosDO.setIsViewInBackend(subTypeObj.getBoolean("isViewInBackend"));
                subTypeNacosDO.setName(subTypeObj.getJSONObject("name"));
                subTypeMap.put(subTypeNacosDO.getId(), subTypeNacosDO);
                typeNacosDO = typeMap.get(subTypeNacosDO.getParentId());
                if (ObjectUtils.isEmpty(typeNacosDO)) {
                    continue;
                }
                subTypeList = typeNacosDO.getSubTypeList();
                if (CollectionUtils.isEmpty(subTypeList)) {
                    subTypeList = Lists.newArrayList();
                }
                subTypeList.add(subTypeNacosDO);
                typeNacosDO.setSubTypeList(subTypeList);
                typeMap.put(typeNacosDO.getId(), typeNacosDO);
            }
        }
    }

    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(dataId, properties.getGroup(), new RuleListener());
            String sourceData = configService.getConfig(dataId, properties.getGroup(), properties.getTimeout());
            updateCache(sourceData);
        } catch (NacosException e) {
            log.error("BalanceRecordConfigAdapter.init()", e);
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

}
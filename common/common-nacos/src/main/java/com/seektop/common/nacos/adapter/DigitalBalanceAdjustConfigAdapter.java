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
import com.seektop.dto.adapter.DigitalBalanceAdjustSubTypeNacosDO;
import com.seektop.dto.adapter.DigitalBalanceAdjustTypeNacosDO;
import com.seektop.dto.digital.DigitalBalanceAdjustSubTypeDO;
import com.seektop.dto.digital.DigitalBalanceAdjustTypeDO;
import com.seektop.enumerate.Language;
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
public class DigitalBalanceAdjustConfigAdapter {

    private final static String dataId = "digital-balance-adjust-config";

    private final NacosConfigProperties properties;

    private Map<Short, DigitalBalanceAdjustSubTypeNacosDO> subTypesMap = Maps.newHashMap();

    private Map<Short, DigitalBalanceAdjustTypeNacosDO> typesMap = Maps.newHashMap();

    /**
     * 获取类型名称
     *
     * @param typeId
     * @param language
     * @return
     */
    public String getTypeName(Short typeId, Language language) {
        DigitalBalanceAdjustTypeNacosDO typeNacosDO = typesMap.get(typeId);
        if (ObjectUtils.isEmpty(typeNacosDO)) {
            return null;
        }
        return typeNacosDO.getName().getString(language.getCode());
    }

    /**
     * 获取子类型名称
     *
     * @param subTypeId
     * @param language
     * @return
     */
    public String getSubTypeName(Short subTypeId, Language language) {
        DigitalBalanceAdjustSubTypeNacosDO subTypeNacosDO = subTypesMap.get(subTypeId);
        if (ObjectUtils.isEmpty(subTypeNacosDO)) {
            return null;
        }
        return subTypeNacosDO.getName().getString(language.getCode());
    }

    /**
     * 检查子类型是否存在
     *
     * @param subTypeId
     * @return
     */
    public Boolean isExistSubType(Short subTypeId) {
        return subTypesMap.containsKey(subTypeId);
    }

    /**
     * 获取子类型的配置对象
     *
     * @param subTypeId
     * @return
     */
    public DigitalBalanceAdjustSubTypeNacosDO getSubTypeConfigDO(Short subTypeId) {
        return subTypesMap.get(subTypeId);
    }

    /**
     * 获取主类型配置对象
     *
     * @param typeId
     * @return
     */
    public DigitalBalanceAdjustTypeNacosDO getTypeConfigDO(Short typeId) {
        return typesMap.get(typeId);
    }

    /**
     * 获取资金调整的类型
     *
     * @param language
     * @return
     */
    public List<DigitalBalanceAdjustTypeDO> getTypeList(Language language) {
        List<DigitalBalanceAdjustTypeDO> resultList = Lists.newArrayList();
        if (CollectionUtils.isEmpty(typesMap)) {
            return resultList;
        }
        for (Short typeId : typesMap.keySet()) {
            DigitalBalanceAdjustTypeNacosDO typeNacosDO = typesMap.get(typeId);
            if (ObjectUtils.isEmpty(typeNacosDO)) {
                continue;
            }
            if (typeNacosDO.getIsEnable() == false) {
                continue;
            }
            DigitalBalanceAdjustTypeDO typeDO = new DigitalBalanceAdjustTypeDO();
            typeDO.setId(typeNacosDO.getId());
            typeDO.setName(typeNacosDO.getName().getString(language.getCode()));
            List<DigitalBalanceAdjustSubTypeDO> subTypeList = Lists.newArrayList();
            List<DigitalBalanceAdjustSubTypeNacosDO> subTypeNacosList = typeNacosDO.getSubTypeList();
            if (CollectionUtils.isEmpty(subTypeNacosList) == false) {
                for (DigitalBalanceAdjustSubTypeNacosDO subTypeNacosDO : subTypeNacosList) {
                    if (ObjectUtils.isEmpty(subTypeNacosDO)) {
                        continue;
                    }
                    if (subTypeNacosDO.getIsEnable() == false) {
                        continue;
                    }
                    DigitalBalanceAdjustSubTypeDO subTypeDO = new DigitalBalanceAdjustSubTypeDO();
                    subTypeDO.setId(subTypeNacosDO.getId());
                    subTypeDO.setParentId(subTypeNacosDO.getParentId());
                    subTypeDO.setName(subTypeNacosDO.getName().getString(language.getCode()));
                    subTypeDO.setIsRecordProfit(subTypeNacosDO.getIsRecordProfit());
                    subTypeList.add(subTypeDO);
                }
            }
            typeDO.setSubTypeList(subTypeList);
            resultList.add(typeDO);
        }
        return resultList;
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        JSONObject configObj = JSON.parseObject(sourceData);
        JSONArray typeArray = configObj.getJSONArray("types");
        synchronized (typesMap) {
            typesMap.clear();
            for (int i = 0, len = typeArray.size(); i < len; i++) {
                JSONObject typeObj = typeArray.getJSONObject(i);
                DigitalBalanceAdjustTypeNacosDO typeDO = new DigitalBalanceAdjustTypeNacosDO();
                typeDO.setId(typeObj.getShort("id"));
                typeDO.setIsEnable(typeObj.getBoolean("isEnable"));
                typeDO.setIsNegate(typeObj.getBoolean("isNegate"));
                typeDO.setReportEventId(typeObj.getInteger("reportEventId"));
                typeDO.setTransactionTypeId(typeObj.getInteger("transactionTypeId"));
                typeDO.setName(typeObj.getJSONObject("name"));
                List<DigitalBalanceAdjustSubTypeNacosDO> subTypeList = Lists.newArrayList();
                JSONArray subTypeArray = typeObj.getJSONArray("subTypes");
                for (int j = 0, jlen = subTypeArray.size(); j < jlen; j++) {
                    JSONObject subTypeObj = subTypeArray.getJSONObject(j);
                    DigitalBalanceAdjustSubTypeNacosDO subTypeDO = new DigitalBalanceAdjustSubTypeNacosDO();
                    subTypeDO.setId(subTypeObj.getShort("id"));
                    subTypeDO.setParentId(typeDO.getId());
                    subTypeDO.setTransactionSubTypeId(subTypeObj.getInteger("transactionSubTypeId"));
                    subTypeDO.setIsEnable(subTypeObj.getBoolean("isEnable"));
                    subTypeDO.setIsRecordProfit(subTypeObj.getBoolean("isRecordProfit"));
                    subTypeDO.setName(subTypeObj.getJSONObject("name"));
                    subTypeList.add(subTypeDO);
                    subTypesMap.put(subTypeDO.getId(), subTypeDO);
                }
                typeDO.setSubTypeList(subTypeList);
                typesMap.put(typeDO.getId(), typeDO);
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
            log.error("DigitalBalanceAdjustConfigAdapter.init()", e);
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
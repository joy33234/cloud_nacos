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
import com.seektop.dto.adapter.DigitalRechargeModeNacosDO;
import com.seektop.dto.adapter.DigitalRechargeTypeNacosDO;
import com.seektop.dto.digital.CNYRechargeModeDO;
import com.seektop.dto.digital.CNYRechargeTypeDO;
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
public class DigitalRechargeConfigAdapter {

    private final static String dataId = "digital-recharge-config";

    private final NacosConfigProperties properties;

    private Map<Short, DigitalRechargeTypeNacosDO> typeMap = Maps.newHashMap();

    private Map<Short, DigitalRechargeModeNacosDO> modeMap = Maps.newHashMap();

    public Map<Short, DigitalRechargeTypeNacosDO> getTypeMap() {
        return typeMap;
    }

    public List<CNYRechargeTypeDO> getAllType(Language language) {
        List<CNYRechargeTypeDO> resultList = Lists.newArrayList();
        for (Short typeId : typeMap.keySet()) {
            DigitalRechargeTypeNacosDO nacosDO = typeMap.get(typeId);
            CNYRechargeTypeDO typeDO = new CNYRechargeTypeDO();
            typeDO.setId(typeId);
            typeDO.setName(nacosDO.getName().getString(language.getCode()));
            typeDO.setModes(getModeByTypeId(typeId, language));
            resultList.add(typeDO);
        }
        return resultList;
    }

    public List<CNYRechargeModeDO> getModeByTypeId(Short typeId, Language language) {
        DigitalRechargeTypeNacosDO nacosDO = typeMap.get(typeId);
        if (ObjectUtils.isEmpty(nacosDO)) {
            return Lists.newArrayList();
        }
        List<DigitalRechargeModeNacosDO> modeList = nacosDO.getModeList();
        if (CollectionUtils.isEmpty(modeList)) {
            return Lists.newArrayList();
        }
        List<CNYRechargeModeDO> resultList = Lists.newArrayList();
        for (DigitalRechargeModeNacosDO modeNacosDO : modeList) {
            CNYRechargeModeDO modeDO = new CNYRechargeModeDO();
            modeDO.setId(modeNacosDO.getId());
            modeDO.setName(modeNacosDO.getName().getString(language.getCode()));
            modeDO.setIcon(modeNacosDO.getIcon());
            modeDO.setPaymentTypeId(modeNacosDO.getPaymentTypeId());
            modeDO.setRechargeTypeId(modeNacosDO.getRechargeTypeId());
            resultList.add(modeDO);
        }
        return resultList;
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        JSONObject configObj = JSON.parseObject(sourceData);
        // 处理充值类型
        JSONArray typeArray = configObj.getJSONArray("types");
        synchronized (typeMap) {
            typeMap.clear();
            for (int i = 0, len = typeArray.size(); i < len; i++) {
                JSONObject typeObj = typeArray.getJSONObject(i);
                DigitalRechargeTypeNacosDO typeNacosDO = new DigitalRechargeTypeNacosDO();
                typeNacosDO.setId(typeObj.getShort("id"));
                typeNacosDO.setIsViewInForehead(typeObj.getBoolean("isViewInForehead"));
                typeNacosDO.setIsViewInBackend(typeObj.getBoolean("isViewInBackend"));
                typeNacosDO.setName(typeObj.getJSONObject("name"));
                typeMap.put(typeNacosDO.getId(), typeNacosDO);
            }
        }
        // 处理充值方式
        JSONArray modeArray = configObj.getJSONArray("modes");
        synchronized (modeMap) {
            modeMap.clear();
            for (int i = 0, len = modeArray.size(); i < len; i++) {
                JSONObject modeObj = modeArray.getJSONObject(i);
                DigitalRechargeModeNacosDO modeNacosDO = new DigitalRechargeModeNacosDO();
                modeNacosDO.setId(modeObj.getShort("id"));
                modeNacosDO.setPaymentTypeId(modeObj.getInteger("paymentTypeId"));
                modeNacosDO.setRechargeTypeId(modeObj.getShort("rechargeTypeId"));
                modeNacosDO.setIsViewInForehead(modeObj.getBoolean("isViewInForehead"));
                modeNacosDO.setIsViewInBackend(modeObj.getBoolean("isViewInBackend"));
                modeNacosDO.setIcon(modeObj.getString("icon"));
                modeNacosDO.setName(modeObj.getJSONObject("name"));
                modeMap.put(modeNacosDO.getId(), modeNacosDO);

                DigitalRechargeTypeNacosDO typeNacosDO = typeMap.get(modeNacosDO.getRechargeTypeId());
                if (ObjectUtils.isEmpty(typeNacosDO)) {
                    continue;
                }
                List<DigitalRechargeModeNacosDO> modeList = typeNacosDO.getModeList();
                if (CollectionUtils.isEmpty(modeList)) {
                    modeList = Lists.newArrayList();
                    modeList.add(modeNacosDO);
                    typeNacosDO.setModeList(modeList);
                    typeMap.put(typeNacosDO.getId(), typeNacosDO);
                }
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
            log.error("DigitalRechargeConfigAdapter.init()", e);
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
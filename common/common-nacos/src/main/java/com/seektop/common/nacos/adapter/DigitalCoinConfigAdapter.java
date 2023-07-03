package com.seektop.common.nacos.adapter;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.google.common.collect.Maps;
import com.seektop.dto.adapter.CoinConfigNacosDO;
import com.seektop.enumerate.Language;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class DigitalCoinConfigAdapter {

    private final static String dataId = "digital-coin-config";

    private final NacosConfigProperties properties;

    private Map<String, CoinConfigNacosDO> coinMap = Maps.newHashMap();

    /**
     * 全部币种配置
     *
     * @return
     */
    public Map<String, CoinConfigNacosDO> getCoinConfigMap() {
        return coinMap;
    }

    /**
     * 币种代码是否存在
     *
     * @param coin
     * @return
     */
    public Boolean isExist(final String coin) {
        return coinMap.containsKey(coin);
    }

    /**
     * 获取币种配置
     *
     * @param coin
     * @return
     */
    public CoinConfigNacosDO getCoinConfig(final String coin) {
        return coinMap.get(coin);
    }

    /**
     * 通过语言获取币名称
     *
     * @param coin
     * @param language
     * @return
     */
    public String getCoinName(final String coin, Language language) {
        CoinConfigNacosDO configNacosDO = coinMap.get(coin);
        if (ObjectUtils.isEmpty(configNacosDO)) {
            return null;
        }
        return configNacosDO.getName().getString(language.getCode());
    }

    /**
     * 获取币种图标
     *
     * @param coin
     * @return
     */
    public String getCoinIcon(final String coin) {
        CoinConfigNacosDO configNacosDO = coinMap.get(coin);
        if (ObjectUtils.isEmpty(configNacosDO)) {
            return null;
        }
        return configNacosDO.getIcon();
    }

    /**
     * 获取币种小数点精度
     *
     * @param coin
     * @return
     */
    public Integer getCoinScale(final String coin) {
        CoinConfigNacosDO configNacosDO = coinMap.get(coin);
        if (ObjectUtils.isEmpty(configNacosDO)) {
            return null;
        }
        return configNacosDO.getScale();
    }

    /**
     * 获取支持的协议
     *
     * @param coin
     * @return
     */
    public List<String> getProtocol(final String coin) {
        CoinConfigNacosDO configNacosDO = coinMap.get(coin);
        if (ObjectUtils.isEmpty(configNacosDO)) {
            return null;
        }
        return configNacosDO.getProtocols();
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        JSONArray configArray = JSON.parseArray(sourceData);
        synchronized (coinMap) {
            coinMap.clear();
            for (int i = 0, len = configArray.size(); i < len; i++) {
                JSONObject configObj = configArray.getJSONObject(i);
                coinMap.put(configObj.getString("coin"), configObj.toJavaObject(CoinConfigNacosDO.class));
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
            log.error("CoinConfigAdapter.init()", e);
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
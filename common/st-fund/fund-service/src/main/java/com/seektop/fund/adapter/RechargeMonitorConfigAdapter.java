package com.seektop.fund.adapter;

import com.alibaba.cloud.nacos.NacosConfigProperties;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.nacos.api.NacosFactory;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.listener.Listener;
import com.alibaba.nacos.api.exception.NacosException;
import com.seektop.fund.adapter.dto.FailureChangeFundLevelConfigDO;
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
public class RechargeMonitorConfigAdapter {

    private final static String dataId = "recharge-monitor-config";

    private final NacosConfigProperties properties;

    private JSONObject configObj = new JSONObject();

    private FailureChangeFundLevelConfigDO failureChangeFundLevelConfig;

    /**
     * 获取充值失败变更财务层级的配置
     *
     * @return
     */
    public FailureChangeFundLevelConfigDO getFailureChangeFundLevelConfig() {
        return this.failureChangeFundLevelConfig;
    }

    public void updateCache(String sourceData) {
        if (StringUtils.isEmpty(sourceData)) {
            return;
        }
        synchronized (configObj) {
            this.configObj = JSON.parseObject(sourceData);

            JSONObject failureChangeConfigObj = configObj.getJSONObject("failure_change_fund_level");
            JSONObject allowFailureTimesConfigObj = failureChangeConfigObj.getJSONObject("allow_failure_times");
            failureChangeFundLevelConfig = new FailureChangeFundLevelConfigDO();
            failureChangeFundLevelConfig.setTargetLevelId(failureChangeConfigObj.getInteger("target_level_id"));
            failureChangeFundLevelConfig.setNewUserAllowFailureTimes(allowFailureTimesConfigObj.getLong("new_user"));
            failureChangeFundLevelConfig.setOldUserAllowFailureTimes(allowFailureTimesConfigObj.getLong("old_user"));
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
            log.error("RechargeMonitorConfigAdapter.init()", e);
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
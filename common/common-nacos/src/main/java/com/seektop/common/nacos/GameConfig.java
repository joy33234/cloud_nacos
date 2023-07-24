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
import com.google.common.collect.Maps;
import com.seektop.constant.game.GameChannelConstants;
import com.seektop.dto.GameInfoDO;
import com.seektop.enumerate.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

@Slf4j
@Component
public class GameConfig {

    private final static String dataId = "global-game-info";

    @Autowired
    private NacosConfigProperties properties;

    private List<GameInfoDO> gameInfos = Lists.newArrayList();

    private Map<Integer, GameInfoDO> valuesOfPlatformId = Maps.newHashMap();

    private Map<Integer, List<GameInfoDO>> valuesOfChannelId = Maps.newHashMap();

    @PostConstruct
    private void init() {
        try {
            ConfigService configService = NacosFactory.createConfigService(properties.getServerAddr());
            configService.addListener(dataId, properties.getGroup(), new DataSourceListener());
            String source = configService.getConfig(dataId, properties.getGroup(), properties.getTimeout());
            setDataSource(source);
        } catch (NacosException e) {
            log.error("GameInfoConfig.init()", e);
        }
    }

    class DataSourceListener implements Listener {

        @Override
        public Executor getExecutor() {
            return null;
        }

        @Override
        public void receiveConfigInfo(String configInfo) {
            setDataSource(configInfo);
        }

    }

    private void setDataSource(final String source) {
        if (StringUtils.isEmpty(source)) {
            return;
        }
        synchronized (gameInfos) {
            gameInfos.clear();
            JSONArray dataArray = JSON.parseArray(source);
            for (int i = 0, len = dataArray.size(); i < len; i++) {
                JSONObject dataObj = dataArray.getJSONObject(i);
                if (ObjectUtils.isEmpty(dataObj)) {
                    continue;
                }
                gameInfos.add(dataObj.toJavaObject(GameInfoDO.class));
            }
        }
        synchronized (valuesOfPlatformId) {
            valuesOfPlatformId.clear();
            for (GameInfoDO gameInfo : gameInfos) {
                valuesOfPlatformId.put(gameInfo.getPlatformId(), gameInfo);
            }
        }
        synchronized (valuesOfChannelId) {
            valuesOfChannelId.clear();
            for (GameInfoDO gameInfo : gameInfos) {
                List<GameInfoDO> gameInfoListByChannelId = valuesOfChannelId.get(gameInfo.getChannelId());
                if (CollectionUtils.isEmpty(gameInfoListByChannelId)) {
                    gameInfoListByChannelId = Lists.newArrayList();
                }
                gameInfoListByChannelId.add(gameInfo);
                valuesOfChannelId.put(gameInfo.getChannelId(), gameInfoListByChannelId);
            }
        }
    }

    public List<GameInfoDO> getGameInfos() {
        return gameInfos;
    }

    public Map<Integer, GameInfoDO> getPlatformIdMap() {
        return valuesOfPlatformId;
    }

    public Map<Integer, List<GameInfoDO>> getChannelIdMap() {
        return valuesOfChannelId;
    }

    public GameInfoDO getGameInfoByPlatformId(int platformId) {
        return valuesOfPlatformId.get(platformId);
    }

    public List<GameInfoDO> getGameInfoByChannelId(int channelId) {
        return valuesOfChannelId.get(channelId);
    }

    /**
     * 根据channelId获取channelName
     *
     * @param channelId
     * @return
     */
    public String getChannelName(Integer channelId) {
        if (channelId == GameChannelConstants.CHANNEL_SELF) {
            return "中心";
        }
        List<GameInfoDO> gameInfoDOS = valuesOfChannelId.get(channelId);
        if (!CollectionUtils.isEmpty(gameInfoDOS)) {
            return gameInfoDOS.get(0).getChannelName();
        }
        return "未知游戏";
    }
    public String getChannelName(Integer channelId,Language language) {
        if (channelId == GameChannelConstants.CHANNEL_SELF) {
            return "中心";
        }
        List<GameInfoDO> gameInfoDOS = valuesOfChannelId.get(channelId);
        if (!CollectionUtils.isEmpty(gameInfoDOS)) {
            final GameInfoDO gameInfoDO = gameInfoDOS.get(0);
            if(null != gameInfoDO){
                final String channelName = gameInfoDO.getLocalInfo().getJSONObject("channelName").getString(language.getCode());
                if(!StringUtils.isEmpty(channelName)) {
                    return channelName;
                }
            }
            return gameInfoDOS.get(0).getChannelName();
        }
        return "未知游戏";
    }

    /**
     * 获取客户端的游戏渠道名称
     *
     * @param channelId
     * @return
     */
    public String getClientChannelName(Integer channelId) {
        if (channelId == GameChannelConstants.CHANNEL_SELF) {
            return "中心";
        }
        List<GameInfoDO> gameInfoDOS = valuesOfChannelId.get(channelId);

        if (CollectionUtils.isEmpty(gameInfoDOS)) {
            return "未知游戏";
        } else {
            GameInfoDO gameInfoDO = gameInfoDOS.get(0);
            if (StringUtils.hasText(gameInfoDO.getClientChannelName())) {
                return gameInfoDO.getClientChannelName();
            } else {
                return gameInfoDO.getChannelName();
            }
        }
    }

    /**
     * 根据channelId返回游戏转账开放状态
     *
     * @param channelId
     * @return
     */
    public boolean getOpenStatusByChannelId(Integer channelId) {
        //中心钱包默认开启
        if (channelId == GameChannelConstants.CHANNEL_SELF) {
            return true;
        }
        List<GameInfoDO> gameInfoDOS = valuesOfChannelId.get(channelId);
        if (!CollectionUtils.isEmpty(gameInfoDOS)) {
            //channelId无法区分AG，但是AG是一致的
            return gameInfoDOS.get(0).isOpen();
        }
        return false;
    }

    /**
     * 获取游戏转账开放状态的channel集合
     *
     * @return
     */
    public List<Integer> getOpenChannelList() {
        List channelList = new ArrayList();
        //遍历map
        for (Map.Entry<Integer, List<GameInfoDO>> entry : valuesOfChannelId.entrySet()) {
            List<GameInfoDO> gameInfoDOS = entry.getValue();
            if (!CollectionUtils.isEmpty(gameInfoDOS) && gameInfoDOS.get(0).isOpen()) {
                //channelId无法区分AG，但是AG是一致的
                channelList.add(entry.getKey());
            }
        }
        return channelList;
    }

    /**
     * 根据type获取游戏或者的体育对的gamePlatformCode
     *
     * @param type 0 所有 , 1 娱乐 , 2 体育(包含 5 电竞)
     */
    public List<Integer> getGamePlatformCodeByType(int type) {
        List<Integer> platformIdList = new ArrayList<>();
        Integer code = null;
        //遍历map
        for (Integer gamePlatformId : valuesOfPlatformId.keySet()) {
            code = gamePlatformId;
            // 通过最后一个数字判断是否体育
            if ((0 == type || 1 == type) && (2 != code % 10 && 5 != code % 10)) {
                platformIdList.add(gamePlatformId);
            } else if ((0 == type || 2 == type) && (2 == code % 10 || 5 == code % 10)) {
                platformIdList.add(gamePlatformId);
            }
        }
        return platformIdList;
    }


}
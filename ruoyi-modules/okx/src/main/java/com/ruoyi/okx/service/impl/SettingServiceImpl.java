package com.ruoyi.okx.service.impl;

import cn.hutool.setting.Setting;
import com.alibaba.fastjson.JSON;
import com.alibaba.nacos.common.utils.CollectionUtils;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.OkxConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.mapper.SettingMapper;
import com.ruoyi.okx.service.SettingService;
import com.ruoyi.okx.utils.DtoUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Caching;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.collectingAndThen;
import static java.util.stream.Collectors.toCollection;

/**
 * 参数配置 服务层实现
 * 
 * @author ruoyi
 */
@Service
@Slf4j
public class SettingServiceImpl implements SettingService
{
    @Autowired
    private SettingMapper settingMapper;

    @Autowired
    private RedisService redisService;

    /**
     * 项目启动时，初始化参数到缓存
     */
    @PostConstruct
    public void init()
    {
        loadingSettingCache();
    }

    /**
     * 查询参数配置信息
     * 
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    @Override
    public OkxSetting selectSettingById(Long configId)
    {
        OkxSetting setting = new OkxSetting();
        setting.setSettingId(configId);
        return settingMapper.selectSetting(setting);
    }

    @Override
    public List<OkxSetting> selectSettingByIds(Long[] settingIds)
    {
        return settingMapper.selectSettingListByIds(settingIds);
    }

    /**
     * 根据键名查询参数配置信息
     * 
     * @param settingKey 参数key
     * @return 参数键值
     */
    @Override
    public String selectSettingByKey(String settingKey)
    {
        String configValue = Convert.toStr(redisService.getCacheObject(getCacheKey(settingKey)));
        if (StringUtils.isNotEmpty(configValue))
        {
            return configValue;
        }
        OkxSetting setting = new OkxSetting();
        setting.setSettingKey(settingKey);
        OkxSetting retSetting = settingMapper.selectSetting(setting);
        if (StringUtils.isNotNull(retSetting))
        {
            redisService.setCacheObject(getCacheKey(settingKey), retSetting.getSettingValue());
            return retSetting.getSettingValue();
        }
        return StringUtils.EMPTY;
    }

    /**
     * 查询参数配置列表
     * 
     * @param setting 参数配置信息
     * @return 参数配置集合
     */
    @Override
    public List<OkxSetting> selectSettingList(OkxSetting setting)
    {
        return settingMapper.selectSettingList(setting);
    }

    /**
     * 新增参数配置
     * 
     * @param setting 参数配置信息
     * @return 结果
     */
    @Override
    public int insertSetting(OkxSetting setting)
    {
        int row = settingMapper.insertSetting(setting);
        if (row > 0)
        {
            redisService.setCacheObject(getCacheKey(setting.getSettingKey()), setting.getSettingValue());
        }
        return row;
    }

    /**
     * 修改参数配置
     * 
     * @param setting 参数配置信息
     * @return 结果
     */
    @Override
    public int updateSetting(OkxSetting setting)
    {
        int row = settingMapper.updateSetting(setting);
        if (row > 0)
        {
            redisService.setCacheObject(getCacheKey(setting.getSettingKey()), setting.getSettingValue());
        }
        return row;
    }

    /**
     * 批量删除参数信息
     * 
     * @param configIds 需要删除的参数ID
     */
    @Override
    public void deleteSettingByIds(Long[] configIds)
    {
        for (Long configId : configIds)
        {
            OkxSetting setting = selectSettingById(configId);

            settingMapper.deleteSettingById(configId);
            redisService.deleteObject(getCacheKey(setting.getSettingKey()));
        }
    }

    /**
     * 加载参数缓存数据
     */
    @Override
    public void loadingSettingCache()
    {
        List<OkxSetting> configsList = settingMapper.selectSettingList(new OkxSetting());
        for (OkxSetting setting : configsList)
        {
            redisService.setCacheObject(getCacheKey(setting.getSettingKey()), setting.getSettingValue());
        }
    }

    /**
     * 清空参数缓存数据
     */
    @Override
    public void clearSettingCache()
    {
        Collection<String> keys = redisService.keys(CacheConstants.OKX_SETTING_KEY + "*");
        redisService.deleteObject(keys);
    }

    /**
     * 重置参数缓存数据
     */
    @Override
    public void resetSettingCache()
    {
        clearSettingCache();
        loadingSettingCache();
    }

    /**
     * 校验参数键名是否唯一
     * 
     * @param setting 参数配置信息
     * @return 结果
     */
    @Override
    public String checkSettingKeyUnique(OkxSetting setting)
    {
        Long configId = StringUtils.isNull(setting.getSettingId()) ? -1L : setting.getSettingId();
        OkxSetting info = settingMapper.checkSettingKeyUnique(setting.getSettingKey());
        if (StringUtils.isNotNull(info) && info.getSettingId().longValue() != configId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验参数键名是否唯一
     *
     * @param settingIds 参数配置信息
     * @return 结果
     */
    @Override
    public boolean checkSettingKey(Long[] settingIds){
        if (StringUtils.isEmpty(settingIds)) {
            return false;
        }
        List<OkxSetting> uniqueSettings = settingMapper.selectSettingList(new OkxSetting()).stream().collect(
                collectingAndThen(toCollection(() -> new TreeSet<>(comparing(OkxSetting::getSettingKey))), ArrayList::new));
        //部分配置未勾选
        if (uniqueSettings.size() > settingIds.length) {
            log.info("部分配置未勾选");
            return false;
        }
        List<OkxSetting> settingList = settingMapper.selectSettingListByIds(settingIds).stream().filter(item -> item.getSettingUnique() == 0).collect(Collectors.toList());
        List<OkxSetting> uniqueSettingList = settingList.stream().collect(
                collectingAndThen(toCollection(() -> new TreeSet<>(comparing(OkxSetting::getSettingKey))), ArrayList::new));

        if (settingList.size() != uniqueSettingList.size()) {
            log.info("部分配置重 唯一性配置错误");
            return false;
        }
        return true;
    }

    /**
     * 设置cache key
     * 
     * @param settingKey 参数键
     * @return 缓存键key
     */
    private String getCacheKey(String settingKey)
    {
        return CacheConstants.OKX_SETTING_KEY + settingKey;
    }

}

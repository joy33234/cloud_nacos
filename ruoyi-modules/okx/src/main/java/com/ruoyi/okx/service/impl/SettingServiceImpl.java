package com.ruoyi.okx.service.impl;

import com.alibaba.fastjson.JSON;
import com.ruoyi.common.core.constant.CacheConstants;
import com.ruoyi.common.core.constant.UserConstants;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.core.utils.StringUtils;
import com.ruoyi.common.redis.service.RedisService;
import com.ruoyi.okx.domain.OkxSetting;
import com.ruoyi.okx.mapper.SettingMapper;
import com.ruoyi.okx.service.SettingService;
import org.apache.poi.ss.formula.functions.T;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.Collection;
import java.util.List;

/**
 * 参数配置 服务层实现
 * 
 * @author ruoyi
 */
@Service
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

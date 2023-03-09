package com.ruoyi.okx.service;



import com.ruoyi.okx.domain.OkxSetting;
import org.apache.poi.ss.formula.functions.T;

import java.math.BigDecimal;
import java.util.List;

/**
 * 参数配置 服务层
 * 
 * @author ruoyi
 */
public interface SettingService
{
    /**
     * 查询参数配置信息
     * 
     * @param configId 参数配置ID
     * @return 参数配置信息
     */
    public OkxSetting selectSettingById(Long configId);

    /**
     * 查询参数配置信息
     *
     * @param settingIds 参数配置ID
     * @return 参数配置信息
     */
    public List<OkxSetting> selectSettingByIds(Long[] settingIds);


    /**
     * 根据键名查询参数配置信息
     * 
     * @param configKey 参数键名
     * @return 参数键值
     */
    public String selectSettingByKey(String configKey);

    /**
     * 查询参数配置列表
     * 
     * @param config 参数配置信息
     * @return 参数配置集合
     */
    public List<OkxSetting> selectSettingList(OkxSetting config);

    /**
     * 新增参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    public int insertSetting(OkxSetting config);

    /**
     * 修改参数配置
     * 
     * @param config 参数配置信息
     * @return 结果
     */
    public int updateSetting(OkxSetting config);

    /**
     * 批量删除参数信息
     * 
     * @param configIds 需要删除的参数ID
     */
    public void deleteSettingByIds(Long[] configIds);

    /**
     * 加载参数缓存数据
     */
    public void loadingSettingCache();

    /**
     * 清空参数缓存数据
     */
    public void clearSettingCache();

    /**
     * 重置参数缓存数据
     */
    public void resetSettingCache();

    /**
     * 校验参数键名是否唯一
     * 
     * @param config 参数信息
     * @return 结果
     */
    public String checkSettingKeyUnique(OkxSetting config);

}

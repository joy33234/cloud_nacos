package com.ruoyi.okx.mapper;


import com.ruoyi.okx.domain.OkxSetting;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 参数配置 数据层
 * 
 * @author ruoyi
 */
public interface SettingMapper
{
    /**
     * 查询参数配置信息
     * 
     * @param setting 参数配置信息
     * @return 参数配置信息
     */
    public OkxSetting selectSetting(OkxSetting setting);

    /**
     * 查询参数配置信息
     *
     * @param settingIds 参数配置信息
     * @return 参数配置信息
     */
    public List<OkxSetting> selectSettingListByIds(@Param("settingIds") Long[] settingIds);

    /**
     * 查询参数配置列表
     * 
     * @param setting 参数配置信息
     * @return 参数配置集合
     */
    public List<OkxSetting> selectSettingList(OkxSetting setting);

    /**
     * 根据键名查询参数配置信息
     * 
     * @param configKey 参数键名
     * @return 参数配置信息
     */
    public OkxSetting checkSettingKeyUnique(String configKey);

    /**
     * 新增参数配置
     * 
     * @param setting 参数配置信息
     * @return 结果
     */
    public int insertSetting(OkxSetting setting);

    /**
     * 修改参数配置
     * 
     * @param setting 参数配置信息
     * @return 结果
     */
    public int updateSetting(OkxSetting setting);

    /**
     * 删除参数配置
     * 
     * @param configId 参数ID
     * @return 结果
     */
    public int deleteSettingById(Long configId);

    /**
     * 批量删除参数信息
     * 
     * @param configIds 需要删除的参数ID
     * @return 结果
     */
    public int deleteSettingByIds(Long[] configIds);
}
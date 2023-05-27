package com.ruoyi.pandora.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.ruoyi.pandora.domain.PandoraOpenaiUser;

/**
 * open ai
 * 
 * @author ruoyi
 */


public interface PandoraOpenaiUserMapper extends BaseMapper<PandoraOpenaiUser>
{

    /**
     * 根据userID查询信息
     * 
     * @param userId
     * @return 信息
     */
    public PandoraOpenaiUser selectAiByUserId(Long userId);

}

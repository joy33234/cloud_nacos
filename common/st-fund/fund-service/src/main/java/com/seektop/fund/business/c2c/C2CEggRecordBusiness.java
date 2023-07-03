package com.seektop.fund.business.c2c;

import com.alibaba.fastjson.JSONObject;
import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.fund.C2CEggStatusEnum;
import com.seektop.enumerate.fund.C2CEggTypeEnum;
import com.seektop.fund.controller.backend.param.c2c.C2CEggRecordListParamDO;
import com.seektop.fund.mapper.C2CEggRecordMapper;
import com.seektop.fund.model.C2CEggRecord;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import java.util.Date;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor(onConstructor = @__(@Autowired))
public class C2CEggRecordBusiness extends AbstractBusiness<C2CEggRecord> {

    private final C2CEggRecordMapper c2CEggRecordMapper;

    /**
     * 检查是否在彩蛋时间内的订单
     *
     * @param date
     * @param eggTypeEnum
     * @return
     */
    public Boolean isAccord(Date date, C2CEggTypeEnum eggTypeEnum) {
        return c2CEggRecordMapper.isAccord(date, eggTypeEnum.getType());
    }

    /**
     * 分页查询彩蛋记录
     *
     * @param adminDO
     * @param paramDO
     * @return
     */
    public PageInfo<C2CEggRecord> findPage(GlAdminDO adminDO, C2CEggRecordListParamDO paramDO) {
        PageHelper.startPage(paramDO.getPage(), paramDO.getSize());
        Condition condition = new Condition(C2CEggRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        if (paramDO.getStartDate() != null && paramDO.getEndDate() != null) {
            criteria.andBetween("createDate", paramDO.getStartDate(), paramDO.getEndDate());
        }
        if (paramDO.getType() != null) {
            criteria.andEqualTo("type", paramDO.getType());
        }
        if (StringUtils.hasText(paramDO.getCreator())) {
            criteria.andEqualTo("creator", paramDO.getCreator());
        }
        if (StringUtils.hasText(paramDO.getUpdater())) {
            criteria.andEqualTo("updater", paramDO.getUpdater());
        }
        condition.setOrderByClause(" create_date desc");
        return new PageInfo<>(findByCondition(condition));
    }

    /**
     * 完成彩蛋活动记录
     *
     * @param id
     * @param admin
     */
    public void confirmFinished(Integer id, String admin) {
        C2CEggRecord eggRecord = new C2CEggRecord();
        eggRecord.setId(id);
        eggRecord.setStatus(C2CEggStatusEnum.FINISHED.getStatus());
        eggRecord.setEndDate(new Date());
        eggRecord.setUpdater(admin);
        eggRecord.setUpdateDate(new Date());
        updateByPrimaryKeySelective(eggRecord);
    }

    /**
     * 创建彩蛋活动记录
     *
     * @param admin
     * @param eggTypeEnum
     * @param duration
     * @return
     */
    public Integer confirmCreate(String admin, C2CEggTypeEnum eggTypeEnum, Integer duration, JSONObject configObj) {
        C2CEggRecord eggRecord = new C2CEggRecord();
        eggRecord.setType(eggTypeEnum.getType());
        eggRecord.setStartDate(new Date());
        eggRecord.setDuration(duration);
        eggRecord.setStatus(C2CEggStatusEnum.PROCESSING.getStatus());
        eggRecord.setCreateDate(eggRecord.getStartDate());
        eggRecord.setCreator(admin);
        eggRecord.setConfig(configObj.toJSONString());
        save(eggRecord);
        return eggRecord.getId();
    }

    /**
     * 获取一个有效的彩蛋活动
     *
     * @return
     */
    public C2CEggRecord getAvailableEggRecord() {
        Condition condition = new Condition(C2CEggRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", C2CEggStatusEnum.PROCESSING.getStatus());
        condition.setOrderByClause(" create_date desc");
        List<C2CEggRecord> eggRecords = findByCondition(condition);
        return CollectionUtils.isEmpty(eggRecords) ? null : eggRecords.get(0);
    }

    /**
     * 获取一个有效的彩蛋活动
     *
     * @param typeEnum
     * @return
     */
    public C2CEggRecord getAvailableEggRecord(C2CEggTypeEnum typeEnum) {
        Condition condition = new Condition(C2CEggRecord.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("type", typeEnum.getType());
        criteria.andEqualTo("status", C2CEggStatusEnum.PROCESSING.getStatus());
        condition.setOrderByClause(" create_date desc");
        List<C2CEggRecord> eggRecords = findByCondition(condition);
        return CollectionUtils.isEmpty(eggRecords) ? null : eggRecords.get(0);
    }

    /**
     * 获取最后一次开启的记录
     *
     * @return
     */
    public C2CEggRecord getLastC2CEggRecord() {
        Condition condition = new Condition(C2CEggRecord.class);
        condition.setOrderByClause(" create_date desc limit 1");
        List<C2CEggRecord> eggRecords = findByCondition(condition);
        return CollectionUtils.isEmpty(eggRecords) ? null : eggRecords.get(0);
    }

}
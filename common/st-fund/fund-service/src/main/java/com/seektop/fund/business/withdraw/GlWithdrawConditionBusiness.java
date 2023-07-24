package com.seektop.fund.business.withdraw;

import com.seektop.common.local.tools.LanguageLocalParser;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.common.FundLanguageUtils;
import com.seektop.fund.controller.backend.dto.withdraw.condition.*;
import com.seektop.fund.enums.FundLanguageDicEnum;
import com.seektop.fund.mapper.GlWithdrawConditionMapper;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlWithdrawCondition;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class GlWithdrawConditionBusiness {

    @Resource
    private GlWithdrawConditionMapper glWithdrawConditionMapper;

    @Resource
    private GlFundUserlevelBusiness userlevelBusiness;

    public List<GlWithdrawConditionDO> list(WithdrawCondtionQueryDO queryDO) {
        List<GlWithdrawCondition> conditionList = findList(queryDO.getConditionName(), queryDO.getUserLevel(), queryDO.getWithdrawType(), queryDO.getCoin());

        List<GlWithdrawConditionDO> result = DtoUtils.transformList(conditionList, GlWithdrawConditionDO.class);

        for (GlWithdrawConditionDO conditionDO : result) {
            String[] levelIds = conditionDO.getLevelId().split(",");
            StringBuffer levelName = new StringBuffer();
            for (int i = 0; i < levelIds.length; i++) {
                if (i < 3) {
                    GlFundUserlevel userlevel = userlevelBusiness.findById(levelIds[i]);
                    if (userlevel != null) {
                        levelName.append(userlevel.getName() + ",");
                    }
                }
            }
            if (StringUtils.isNotEmpty(levelName)) {
                conditionDO.setLevelName(levelName.deleteCharAt(levelName.length() - 1).toString());
            }
        }
        return result;
    }

    public GlWithdrawCondition findWithdrawCondition(String levelId, BigDecimal amount) {
        Condition condition = new Condition(GlWithdrawCondition.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andCondition("find_in_set('" + levelId + "',level_id)");
        criteria.andLessThanOrEqualTo("minAmount", amount);
        criteria.andGreaterThan("maxAmount", amount);
        criteria.andEqualTo("status", 1);
        return glWithdrawConditionMapper.selectOneByExample(condition);
    }

    public List<GlWithdrawCondition> findList(String conditionName, List<Integer> levelIds, Integer withdrawType, String coin) {
        Condition condition = new Condition(GlWithdrawCondition.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", 1);
        if (StringUtils.isNotEmpty(conditionName)) {
            criteria.andEqualTo("conditionName", conditionName);
        }
        if (StringUtils.isNotEmpty(coin)) {
            criteria.andEqualTo("coin", coin);
        }
        if (null != withdrawType && withdrawType != -1) {
            criteria.andEqualTo("withdrawType", withdrawType);
        }
        StringBuilder conditionQuery = new StringBuilder();
        boolean isFirst = true;
        if (levelIds != null && levelIds.size() != 0) {
            conditionQuery.append("(");
            for (Integer levelId : levelIds) {
                if (isFirst) {
                    conditionQuery.append("find_in_set('" + levelId + "',level_id)");
                    isFirst = false;
                } else {
                    conditionQuery.append("OR find_in_set('" + levelId + "',level_id)");
                }
            }
            conditionQuery.append(")");
            criteria.andCondition(conditionQuery.toString());
        }
        condition.setOrderByClause("create_date desc");
        return glWithdrawConditionMapper.selectByExample(condition);
    }


    public void updateByLevel(List<GlWithdrawCondition> list) {
        for (GlWithdrawCondition condition : list) {
            glWithdrawConditionMapper.updateByLevel(condition.getId(), condition.getLevelId());
        }
    }

    /**
     * 保存分单条件设置
     *
     * @param addDO
     * @param adminDO
     * @throws GlobalException
     */
    public void save(WithdrawConditionAddDO addDO, GlAdminDO adminDO) throws GlobalException {
        if (addDO.getMinAmount().compareTo(addDO.getMaxAmount()) != -1) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "金额区间设置错误：最低金额必须小于最高金额");
        }
        GlWithdrawCondition condition = new GlWithdrawCondition();
        condition.setConditionName(addDO.getConditionName());
        condition.setWithdrawType(addDO.getWithdrawType());
        condition.setMinAmount(addDO.getMinAmount());
        condition.setMaxAmount(addDO.getMaxAmount());
        condition.setLevelId(addDO.getLevelId());
        condition.setRemark(addDO.getRemark());
        condition.setCoin(addDO.getCoin());

        String errMessage = checkCondition(condition);
        if (StringUtils.isNotEmpty(errMessage)) {
            throw new GlobalException(errMessage);
        }
        Date now = new Date();
        condition.setStatus(1);
        condition.setCreator(adminDO.getUsername());
        condition.setCreateDate(now);
        condition.setLastOperator(adminDO.getUsername());
        condition.setLastUpdate(now);
        glWithdrawConditionMapper.insert(condition);
    }

    /**
     * 编辑分单条件设置
     *
     * @param editDO
     * @param adminDO
     * @throws GlobalException
     */
    public void update(WithdrawConditionEditDO editDO, GlAdminDO adminDO) throws GlobalException {
        if (editDO.getMinAmount().compareTo(editDO.getMaxAmount()) != -1) {
            throw new GlobalException(ResultCode.PARAM_ERROR, "金额区间设置错误：最低金额不能大于或等于最高金额");
        }
        GlWithdrawCondition condition = glWithdrawConditionMapper.selectByPrimaryKey(editDO.getId());
        if (null == condition) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        condition.setConditionName(editDO.getConditionName());
        condition.setWithdrawType(editDO.getWithdrawType());
        condition.setMinAmount(editDO.getMinAmount());
        condition.setMaxAmount(editDO.getMaxAmount());
        condition.setLevelId(editDO.getLevelId());
        condition.setRemark(editDO.getRemark());

        String errMessage = checkCondition(condition);
        if (StringUtils.isNotEmpty(errMessage)) {
            throw new GlobalException(errMessage);
        }
        condition.setLastOperator(adminDO.getUsername());
        condition.setLastUpdate(new Date());
        glWithdrawConditionMapper.updateByPrimaryKeySelective(condition);
    }

    /**
     * 分单条件验证
     *
     * @param condition
     * @return
     */
    private String checkCondition(GlWithdrawCondition condition) {
        //更新分单条件
        List<GlWithdrawCondition> allCondition = glWithdrawConditionMapper.selectAll();
        String levelId = condition.getLevelId();
        String[] levelIds = levelId.split(",");
        boolean checkAmount = true;
        for (String level : levelIds) {
            for (GlWithdrawCondition withdrawCondition : allCondition) {
                if (null != condition.getId() && withdrawCondition.getId().equals(condition.getId())) {
                    continue;
                }
                if (withdrawCondition.getConditionName().equals(condition.getConditionName())) {
                    return "条件名称重复请重新输入";
                }
                List<String> levelList = Arrays.asList(withdrawCondition.getLevelId().split(","));
                //刷选出同层级的条件(有效)
                if (levelList.contains(level) && withdrawCondition.getStatus() == 1) {
                    //同层级的金额不能重复
                    // 1.新条件的最低金额 > 已有 条件的最低金额
                    if (condition.getMinAmount().compareTo(withdrawCondition.getMinAmount()) == 1) {
                        if (condition.getMinAmount().compareTo(withdrawCondition.getMaxAmount()) == -1) {
                            checkAmount = false;
                        }
                    }
                    // 2.新条件的最低金额 < 已有 条件的最低金额
                    else if (condition.getMinAmount().compareTo(withdrawCondition.getMinAmount()) == -1) {
                        if (condition.getMaxAmount().compareTo(withdrawCondition.getMinAmount()) == 1) {
                            checkAmount = false;
                        }
                    }
                    // 3. 新条件的最低金额 = 已有 条件的最低金额
                    else {
                        checkAmount = false;
                    }
                }
                if (!checkAmount) {
                    StringBuffer failMessage = new StringBuffer();
                    failMessage.append(condition.getConditionName() + " 与 " + withdrawCondition.getConditionName());
                    failMessage.append("中出款金额区间有重叠,请重新配置;");
                    return failMessage.toString();
                }
            }
        }
        return null;
    }

    /**
     * 删除分单条件
     *
     * @param deleteDO
     * @param adminDO
     * @throws GlobalException
     */
    public void delete(WithdrawConditionDeleteDO deleteDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawCondition condition = glWithdrawConditionMapper.selectByPrimaryKey(deleteDO.getId());
        if (condition == null) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        condition.setStatus(0);
        condition.setLastOperator(adminDO.getUsername());
        condition.setCreateDate(new Date());
        glWithdrawConditionMapper.updateByPrimaryKeySelective(condition);
    }
}

package com.seektop.fund.business.withdraw;

import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.dto.GlAdminDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.controller.backend.dto.withdraw.condition.*;
import com.seektop.fund.mapper.GlWithdrawAutoConditionMapper;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlWithdrawAutoCondition;
import com.seektop.fund.model.WithdrawAutoConditionMerchantAccount;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlWithdrawAutoConditionBusiness {

    @Resource
    private GlWithdrawAutoConditionMapper glWithdrawAutoConditionMapper;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;
    @Autowired
    private WithdrawAutoConditionMerchantAccountBusiness conditionMerchantAccountBusiness;

    /**
     * 查询自动出款条件
     *
     * @param levelId
     * @param amount
     * @return
     */
    public GlWithdrawAutoCondition findAutoCondition(String levelId, BigDecimal amount) {
        Condition condition = new Condition(GlWithdrawAutoCondition.class);
        Condition.Criteria criteria = condition.createCriteria();
        criteria.andCondition("find_in_set('" + levelId + "',level_id)");
        criteria.andLessThanOrEqualTo("minAmount", amount);
        criteria.andGreaterThan("maxAmount", amount);
        criteria.andEqualTo("status", 1);
        return glWithdrawAutoConditionMapper.selectOneByExample(condition);
    }

    /**
     * 自动提现设置列表查询
     *
     * @param queryDO
     * @return
     */
    public List<GlWithdrawAutoConditionDO> getWithdrawAutoConditionList(WithdrawAutoCondtionQueryDO queryDO) {
        List<GlWithdrawAutoCondition> conditionList = findList(queryDO);
        List<GlWithdrawAutoConditionDO> result = DtoUtils.transformList(conditionList, GlWithdrawAutoConditionDO.class);
        for (GlWithdrawAutoConditionDO autoCondition : result) {
            String[] levelIds = autoCondition.getLevelId().split(",");
            StringBuffer levelName = new StringBuffer();
            for (int i = 0; i < levelIds.length; i++) {
                if (i > 3) {
                    break;
                }
                GlFundUserlevel userlevel = glFundUserlevelBusiness.findById(levelIds[i]);
                if (userlevel != null) {
                    levelName.append(userlevel.getName() + ",");
                }
            }
            if (StringUtils.isNotEmpty(levelName)) {
                autoCondition.setLevelName(levelName.deleteCharAt(levelName.length() - 1).toString());
            }
        }
        return result;
    }

    public List<GlWithdrawAutoCondition> findList(WithdrawAutoCondtionQueryDO dto) {
        Condition condition = new Condition(GlWithdrawAutoCondition.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andEqualTo("status", 1);

        if (StringUtils.isNotEmpty(dto.getConditionName())) {
            criteria.andEqualTo("conditionName", dto.getConditionName());
        }
        if (StringUtils.isNotEmpty(dto.getCoin())) {
            criteria.andEqualTo("coin", dto.getCoin());
        }
        StringBuilder levelQuery = new StringBuilder();
        boolean isLevelFirst = true;
        if (dto.getUserLevel() != null && dto.getUserLevel().size() != 0) {
            levelQuery.append("(");
            for (Integer levelId : dto.getUserLevel()) {
                if (isLevelFirst) {
                    levelQuery.append("find_in_set('" + levelId + "',level_id)");
                    isLevelFirst = false;
                } else {
                    levelQuery.append("OR find_in_set('" + levelId + "',level_id)");
                }
            }
            levelQuery.append(")");
            criteria.andCondition(levelQuery.toString());
        }
//        StringBuilder merchantQuery = new StringBuilder();
//        boolean isMerchantFirst = true;
//        if (!dto.getMerchantId().isEmpty()) {
//            merchantQuery.append("(");
//            for (Integer merchantId : dto.getMerchantId()) {
//                if (isMerchantFirst) {
//                    merchantQuery.append("find_in_set('" + merchantId + "',merchant_id)");
//                    isMerchantFirst = false;
//                } else {
//                    merchantQuery.append("OR find_in_set('" + merchantId + "',merchant_id)");
//                }
//            }
//            levelQuery.append(")");
//            criteria.andCondition(merchantQuery.toString());
//        }
        condition.setOrderByClause("create_date desc");
        return glWithdrawAutoConditionMapper.selectByCondition(condition);
    }

    /**
     * 保存自动出款条件
     *
     * @param addDO
     * @param adminDO
     * @throws GlobalException
     */
    public void save(WithdrawAutoConditionAddDO addDO, GlAdminDO adminDO) throws GlobalException {
        if (addDO.getMinAmount().compareTo(addDO.getMaxAmount()) >= 0) {
            throw new GlobalException("最小金额必须小于最大金额");
        }
        GlWithdrawAutoCondition condition = new GlWithdrawAutoCondition();
        condition.setConditionName(addDO.getConditionName());
        condition.setMerchantId(addDO.getMerchantId());
        condition.setLevelId(addDO.getLevelId());
        condition.setMinAmount(addDO.getMinAmount());
        condition.setMaxAmount(addDO.getMaxAmount());
        condition.setCoin(addDO.getCoin());
        String errMessage = checkCondition(condition);

        if (StringUtils.isNotEmpty(errMessage)) {
            throw new GlobalException(errMessage);
        }

        condition.setStatus(1);
        condition.setCreator(adminDO.getUsername());
        condition.setCreateDate(new Date());
        condition.setLastOperator(adminDO.getUsername());
        condition.setLastUpdate(new Date());
        condition.setRemark(StringUtils.isBlank(addDO.getRemark()) ? "" : addDO.getRemark());
        glWithdrawAutoConditionMapper.insert(condition);
    }


    /**
     * 编辑自动出款条件
     *
     * @param editDO
     * @param adminDO
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void update(WithdrawAutoConditionEditDO editDO, GlAdminDO adminDO) throws GlobalException {
        if (editDO.getMinAmount().compareTo(editDO.getMaxAmount()) >= 0) {
            throw new GlobalException("最小金额必须小于最大金额");
        }
        GlWithdrawAutoCondition condition = glWithdrawAutoConditionMapper.selectByPrimaryKey(editDO.getId());
        if (null == condition) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        condition.setConditionName(editDO.getConditionName());
        condition.setMerchantId(editDO.getMerchantId());
        condition.setLevelId(editDO.getLevelId());
        condition.setMinAmount(editDO.getMinAmount());
        condition.setMaxAmount(editDO.getMaxAmount());

        String errMessage = checkCondition(condition);
        if (StringUtils.isNotEmpty(errMessage)) {
            throw new GlobalException(errMessage);
        }
        condition.setLastOperator(adminDO.getUsername());
        condition.setLastUpdate(new Date());
        condition.setRemark(StringUtils.isBlank(editDO.getRemark()) ? "" : editDO.getRemark());
        glWithdrawAutoConditionMapper.updateByPrimaryKeySelective(condition);
        List<WithdrawAutoConditionMerchantAccount> merchantAccounts = CollectionUtils.isEmpty(editDO.getMerchantAccounts()) ? new ArrayList<>() : editDO.getMerchantAccounts();
        conditionMerchantAccountBusiness.save(condition.getId(), merchantAccounts);
    }


    /**
     * 校验自动出款条件参数
     * 1、新增条件金额
     * 2、名称不能重复
     * 3、同一层级新增条件与已有条件金额是否行重叠
     *
     * @param condition
     * @return
     * @throws GlobalException
     */
    public String checkCondition(GlWithdrawAutoCondition condition) {
        List<GlWithdrawAutoCondition> allCondition = glWithdrawAutoConditionMapper.selectAll().stream()
                .filter(item -> item.getCoin().equals(condition.getCoin())).collect(Collectors.toList());
        String levelId = condition.getLevelId();
        String[] levelIds = levelId.split(",");
        boolean checkAmount = true;

        for (String level : levelIds) {
            for (GlWithdrawAutoCondition autoCondition : allCondition) {
                if (null != condition.getId() && autoCondition.getId().equals(condition.getId())) {
                    continue;
                }
                if (autoCondition.getConditionName().equals(condition.getConditionName())) {
                    return "条件名称重复请重新输入";
                }
                //刷选出同层级的条件(有效)
                if (Arrays.asList(autoCondition.getLevelId().split(",")).contains(level) && autoCondition.getStatus() == 1) {
                    //同层级的金额不能重复
                    // 1.新条件的最低金额 > 已有 条件的最低金额
                    if (condition.getMinAmount().compareTo(autoCondition.getMinAmount()) == 1) {
                        if (condition.getMinAmount().compareTo(autoCondition.getMaxAmount()) == -1) {
                            checkAmount = false;
                        }
                    }
                    // 2.新条件的最低金额 < 已有 条件的最低金额
                    else if (condition.getMinAmount().compareTo(autoCondition.getMinAmount()) == -1) {
                        if (condition.getMaxAmount().compareTo(autoCondition.getMinAmount()) == 1) {
                            checkAmount = false;
                        }
                    }
                    // 3. 新条件的最低金额 = 已有条件的最低金额
                    else {
                        checkAmount = false;
                    }
                }
                if (!checkAmount) {
                    StringBuffer failMessage = new StringBuffer();
                    failMessage.append(condition.getConditionName() + " 与 " + autoCondition.getConditionName());
                    failMessage.append("出款金额区间有重叠,请重新配置；");
                    return failMessage.toString();
                }
            }
        }
        return null;
    }

    /**
     * 删除自动出款条件
     * @param deleteDO
     * @param adminDO
     * @throws GlobalException
     */
    public void delete(WithdrawAutoConditionDeleteDO deleteDO, GlAdminDO adminDO) throws GlobalException {
        GlWithdrawAutoCondition condition = glWithdrawAutoConditionMapper.selectByPrimaryKey(deleteDO.getId());
        if (condition == null || condition.getStatus() == 0) {
            throw new GlobalException(ResultCode.DATA_ERROR);
        }
        condition.setStatus(0);
        condition.setLastOperator(adminDO.getUsername());
        condition.setCreateDate(new Date());
        glWithdrawAutoConditionMapper.updateByPrimaryKeySelective(condition);
    }

    public void updateByLevel(List<GlWithdrawAutoCondition> list) {
        for (GlWithdrawAutoCondition autoCondition : list) {
            glWithdrawAutoConditionMapper.updateByLevel(autoCondition.getId(), autoCondition.getLevelId());
        }
    }
}

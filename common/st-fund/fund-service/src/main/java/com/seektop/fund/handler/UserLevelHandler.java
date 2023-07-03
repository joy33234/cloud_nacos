package com.seektop.fund.handler;

import com.google.common.collect.Lists;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserLevelLockBusiness;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawAutoConditionBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawConditionBusiness;
import com.seektop.fund.business.withdraw.GlWithdrawLevelConfigBusiness;
import com.seektop.fund.controller.backend.dto.FundUserLeveLockDto;
import com.seektop.fund.controller.backend.dto.FundUserLockResult;
import com.seektop.fund.controller.backend.dto.withdraw.condition.WithdrawAutoCondtionQueryDO;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.GlWithdrawAutoCondition;
import com.seektop.fund.model.GlWithdrawCondition;
import com.seektop.fund.model.GlWithdrawLevelConfig;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;

@Slf4j
@Component
public class UserLevelHandler {

    @Autowired
    private GlFundUserlevelBusiness fundUserlevelBusiness;
    @Autowired
    private GlFundUserLevelLockBusiness fundUserLevelLockBusiness;
    @Autowired
    private GlWithdrawConditionBusiness withdrawConditionBusiness;
    @Autowired
    private GlWithdrawAutoConditionBusiness glWithdrawAutoConditionBusiness;
    @Resource
    private GlWithdrawLevelConfigBusiness glWithdrawLevelConfigBusiness;
    @Reference(retries = 1, timeout = 5000)
    private GlUserService userService;

    /**
     * 删除用户层级处理
     *
     * @param levelId
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void delete(Integer levelId) throws GlobalException {
        if (levelId == 1) {
            throw new GlobalException("默认层级不能删除");
        }
        fundUserlevelBusiness.delete(levelId);

        //同时需要逻辑删除，会员提现风控中相应的层级规则
        Date now = new Date();
        List<GlWithdrawLevelConfig> configs = glWithdrawLevelConfigBusiness.findAllBy("levelId", levelId);
        configs.forEach(config -> {
            config.setStatus(2);
            config.setLastOperator("所在层级被删除，提现层级配置自动禁用");
            config.setLastUpdate(now);
            glWithdrawLevelConfigBusiness.updateByPrimaryKeySelective(config);
        });

        //删除提现分单条件层级、自动出款条件设置
        List<GlWithdrawCondition> conditionList = withdrawConditionBusiness.findList(null, Arrays.asList(levelId), null, null);
        for (GlWithdrawCondition condition : conditionList) {
            List<String> newList = new ArrayList<>();
            for (String temp : Arrays.asList(condition.getLevelId().split(","))) {
                newList.add(temp);
            }
            Iterator<String> iter = newList.iterator();
            while (iter.hasNext()) {
                Integer item = Integer.valueOf(iter.next());
                if (item.equals(levelId)) {
                    iter.remove();
                }
            }
            condition.setLevelId(String.join(",", newList));
        }
        withdrawConditionBusiness.updateByLevel(conditionList);

        WithdrawAutoCondtionQueryDO dto = new WithdrawAutoCondtionQueryDO();
        dto.setUserLevel(Arrays.asList(levelId));
        List<GlWithdrawAutoCondition> autoConditionList = glWithdrawAutoConditionBusiness.findList(dto);
        for (GlWithdrawAutoCondition autoCondition : autoConditionList) {
            List<String> newList = new ArrayList<>();
            for (String temp : Arrays.asList(autoCondition.getLevelId().split(","))) {
                newList.add(temp);
            }
            Iterator<String> iter = newList.iterator();
            while (iter.hasNext()) {
                Integer item = Integer.valueOf(iter.next());
                if (item == levelId) {
                    iter.remove();
                }
            }
            autoCondition.setLevelId(String.join(",", newList));
        }
        glWithdrawAutoConditionBusiness.updateByLevel(autoConditionList);

        // 更新缓存
        fundUserlevelBusiness.updateCache();
    }

    /**
     * 锁定层级
     *
     * @param lockDto
     * @param admin
     * @return
     * @throws GlobalException
     */
    public FundUserLockResult lock(FundUserLeveLockDto lockDto, GlAdminDO admin) throws GlobalException {
        List<String> usernames = lockDto.getUsername();
        if (usernames.size() > 10000)
            throw new GlobalException("操作用户数量过大!");

        Integer levelId = lockDto.getLevelId();
        GlFundUserlevel level = fundUserlevelBusiness.findById(levelId);
        if (ObjectUtils.isEmpty(level)) {
            throw new GlobalException("用户层级不存在");
        }

        StringBuffer failMessage = new StringBuffer();
        int failNum = 0;
        List<GlUserDO> users = new ArrayList<>();
        List<GlUserDO> userList = Lists.newArrayList();
        if (!CollectionUtils.isEmpty(usernames)) {
            userList = RPCResponseUtils.getData(userService.findByUsernames(usernames));
        }

        for (GlUserDO glUser : userList) {
            if (!level.getLevelType().equals(glUser.getUserType())) {
                failMessage.append(glUser + "用户类型与层级类型不匹配; ");
                failNum++;
                continue;
            }
            users.add(glUser);
        }
        // 循环匹配用户名,找出不存在的用户
        if (!CollectionUtils.isEmpty(usernames)) {
            for (String u : usernames) {
                if (userList.stream().noneMatch(t -> u.equals(t.getUsername()))) {
                    failMessage.append(u + " 不存在; ");
                    failNum++;
                }
            }
        }
        lockDto.setUsers(users);
        lockDto.setLevel(level);
        lockDto.setAdmin(admin.getUsername());
        FundUserLockResult result;
        try {
            result = fundUserLevelLockBusiness.doUserLock(lockDto);
        } catch (Exception e) {
            log.error("批量修改层级报错: [totalNum: {},failNum: {},failMessage: {},errorMessage: {}]", usernames.size(), failNum, failMessage, e);
            throw new GlobalException("批量修改层级失败");
        }

        result.setFailNum(failNum);
        result.setFailMessage(failMessage.toString());
        return result;
    }
}

package com.seektop.fund.business;

import com.github.pagehelper.PageHelper;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.rabbitmq.service.ReportService;
import com.seektop.common.rest.rpc.RPCResponseUtils;
import com.seektop.common.utils.DateUtils;
import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.fund.common.UserFundUtils;
import com.seektop.fund.controller.backend.dto.FundUserLeveLockDto;
import com.seektop.fund.controller.backend.dto.FundUserLockResult;
import com.seektop.fund.mapper.GlFundUserLevelLockMapper;
import com.seektop.fund.mapper.GlFundUserlevelMapper;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.report.user.UserSynch;
import com.seektop.user.dto.GlUserManageDO;
import com.seektop.user.service.GlUserService;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlFundUserLevelLockBusiness extends AbstractBusiness<GlFundUserLevelLock> {

    @Resource
    private GlFundUserLevelLockMapper glFundUserLevelLockMapper;
    @Resource
    private GlFundUserlevelMapper glFundUserlevelMapper;
    @Resource
    private ReportService reportService;

    @DubboReference(retries = 1, timeout = 5000)
    private GlUserService glUserService;

    @DubboReference(retries = 1, timeout = 5000)
    private UserManageService userManageService;

    @Autowired
    private UserFundUtils userFundUtils;

    public List<GlFundUserLevelLock> findByPage(Integer levelId, Integer page, Integer size) {
        PageHelper.startPage(page, size);
        Condition condition = new Condition(GlFundUserLevelLock.class);
        Example.Criteria criteria =  condition.createCriteria();
        criteria.andEqualTo("levelId", levelId);
        condition.setOrderByClause(" user_id asc");
        return findByCondition(condition);
    }

    public void updateUserLevel(Integer userId, Integer levelId) {
        glFundUserLevelLockMapper.updateUserLevel(userId, levelId);
    }

    public List<GlFundUserLevelLock> findByUserIds(List<Integer> userIds) {
        if (CollectionUtils.isEmpty(userIds))
            return Lists.newArrayList();
        return glFundUserLevelLockMapper.findByUserIds(userIds.stream().distinct().collect(Collectors.toList()));
    }

    public Integer count(Integer levelId, Integer status) {
        GlFundUserLevelLock record = new GlFundUserLevelLock();
        record.setLevelId(levelId);
        record.setStatus(status);
        return glFundUserLevelLockMapper.selectCount(record);
    }

    public void deleteLevelLocks(Integer levelId) {
        GlFundUserLevelLock record = new GlFundUserLevelLock();
        record.setLevelId(levelId);
        glFundUserLevelLockMapper.delete(record);
    }

    public GlFundUserLevelLock findByUsername(String username) throws GlobalException {
        GlUserDO user = RPCResponseUtils.getData(glUserService.getUserInfoByUsername(username));
        if (null == user) {
            throw new GlobalException(ResultCode.DATA_ERROR, "用户不存在");
        }
        Date now = new Date();
        Date fromDate = org.apache.commons.lang3.time.DateUtils.addDays(now, -30);
        if (user.getRegisterDate().after(fromDate)) {
            fromDate = user.getRegisterDate();
        }
        String startDay = DateUtils.format(fromDate, "yyyy-MM-dd");
        String endDay = DateUtils.format(now, "yyyy-MM-dd");
        GlFundUserLevelLock lock = findById(user.getId());
        if (lock == null) {
            lock = new GlFundUserLevelLock();
            lock.setStatus(0);
            lock.setLastOperator(null);
            lock.setLastUpdate(null);
            lock.setUserName(username);
            lock.setLevelId(1);
            lock.setCreateDate(null);
            lock.setUserId(user.getId());
            lock.setBetTotal(BigDecimal.ZERO);
            lock.setRechargeTimes(0);
            lock.setRechargeTotal(BigDecimal.ZERO);
            lock.setRegisterDate(user.getRegisterDate());
            lock.setWithdrawTimes(0);
            lock.setWithdrawTotal(BigDecimal.ZERO);
            lock.setStatDate(null);
        }

        // todo 数据中心提供？
//        UserDailyReport report = glUserDataService.findUserTotalReport(user, startDay, endDay);
//        lock.setBetTotal(report.getBetTotal());
//        lock.setRechargeTimes((int) report.getRechargeCount());
//        lock.setRechargeTotal(report.getRechargeTotal());
//        lock.setWithdrawTimes((int) report.getWithdrawCount());
//        lock.setWithdrawTotal(report.getWithdrawTotal());
        return lock;
    }

    public List<String> findLockUser(Integer levelId) {
        return glFundUserLevelLockMapper.findLockUser(levelId);
    }

    @Transactional(rollbackFor = Exception.class)
    public FundUserLockResult doUserLock(FundUserLeveLockDto lockDto) {
        FundUserLockResult result = new FundUserLockResult();
        List<GlUserDO> users = lockDto.getUsers();
        if (ObjectUtils.isEmpty(users)) {
            return result;
        }

        lockDto.setUpdateTime(new Date());
        GlFundUserlevel level = lockDto.getLevel();
        Map<Integer, String> beforeLevelNameMap = new HashMap<>();
        GlFundUserLevelLock lock;
        GlFundUserlevel glFundUserlevel;
        int successNum = 0;
        for (GlUserDO user : users) {
            if (ObjectUtils.isEmpty(user)) {
                continue;
            }
            lock = findById(user.getId());
            if (!ObjectUtils.isEmpty(lock)) {
                glFundUserlevel = glFundUserlevelMapper.selectByPrimaryKey(lock.getLevelId());
                if (!ObjectUtils.isEmpty(glFundUserlevel)) {
                    // 层级id相同且已锁定，则不更新
                    if (lock.getLevelId().equals(level.getLevelId()) && 1 == lock.getStatus()) {
                        continue;
                    }
                    beforeLevelNameMap.put(user.getId(), glFundUserlevel.getName());
                }
            }
            saveUserLevelLock(lock, user, lockDto);
            successNum++;

            //同步上报
            userSynch(user, lockDto);
        }
        result.setSuccessNum(successNum);

        // 保存GlUserManage
        savesLevelUserManages(lockDto, beforeLevelNameMap);

        return result;
    }

    /**
     * 解锁会员
     *
     * @param usernames
     * @throws GlobalException
     */
    @Transactional(rollbackFor = Exception.class)
    public void unlock(List<String> usernames) throws GlobalException {
        List<GlUserDO> list = RPCResponseUtils.getData(glUserService.findByUsernames(usernames));
        for (GlUserDO user : list) {
            doUserUnLock(user.getId());
        }
    }

    /**
     * 保存
     *
     * @param lock
     * @param user
     * @param lockDto
     */
    private void saveUserLevelLock(GlFundUserLevelLock lock, GlUserDO user, FundUserLeveLockDto lockDto) {
        GlFundUserlevel level = lockDto.getLevel();
        String admin = lockDto.getAdmin();
        Date dateTime = lockDto.getUpdateTime();
        if (lock == null) {
            lock = new GlFundUserLevelLock();
            lock.setCreateDate(dateTime);
            lock.setLevelId(level.getLevelId());
            lock.setUserId(user.getId());
            lock.setUserName(user.getUsername());
            lock.setLastOperator(admin);
            lock.setLastUpdate(dateTime);
            lock.setStatus(1);
            lock.setStatDate(dateTime);
            lock.setWithdrawTotal(BigDecimal.ZERO);
            lock.setWithdrawTimes(0);
            lock.setRegisterDate(user.getRegisterDate());
            lock.setRechargeTotal(BigDecimal.ZERO);
            lock.setRechargeTimes(0);
            lock.setBetTotal(BigDecimal.ZERO);
            save(lock);
        } else {
            lock.setLevelId(level.getLevelId());
            lock.setUserName(user.getUsername());
            lock.setStatus(1);
            lock.setLastUpdate(dateTime);
            lock.setLastOperator(admin);
            updateByPrimaryKeySelective(lock);
        }

        userFundUtils.setCache(user.getId(), level.getLevelId(), level.getName(), level.getLevelType());
    }

    /**
     * 变更上报
     *
     * @param user
     * @param lockDto
     */
    private void userSynch(GlUserDO user, FundUserLeveLockDto lockDto) {
        //变更上报
        UserSynch userSynch = new UserSynch();
        userSynch.setId(user.getId());
        GlFundUserlevel level = lockDto.getLevel();
        userSynch.setLevel_id(level.getLevelId());
        userSynch.setLevel_name(level.getName());
        userSynch.setLevel_status(1);
        userSynch.setLast_update(DateUtils.format(lockDto.getUpdateTime(), "yyyy-MM-dd'T'HH:mm:ss'.000Z'"));
        try {
            reportService.userSynch(userSynch);
        } catch (Exception e) {
            log.error("reportService.userSynch error", e);
        }
    }

    /**
     * 保存操作审核记录
     *
     * @param lockDto
     * @param beforeLevelNameMap
     */
    private void savesLevelUserManages(FundUserLeveLockDto lockDto, Map<Integer, String> beforeLevelNameMap) {
        Date dateTime = lockDto.getUpdateTime();
        List<GlUserDO> users = lockDto.getUsers();
        GlFundUserlevel level = lockDto.getLevel();
        String levelName = level.getName();
        String admin = lockDto.getAdmin();
        List<GlUserManageDO> userManages = new ArrayList<>();
        GlUserManageDO userManage;
        for (GlUserDO user : users) {
            userManage = new GlUserManageDO();
            userManage.setUserId(user.getId());
            userManage.setUsername(user.getUsername());
            userManage.setUserType(user.getUserType());
            userManage.setOptData(levelName);
            String beforeData = beforeLevelNameMap.get(user.getId());
            userManage.setOptBeforeData(StringUtils.isBlank(beforeData) ? "" : beforeData);
            userManage.setOptAfterData(levelName);
            userManage.setCreator(admin);
            userManage.setOptType(UserConstant.UserOperateType.LEVEL_MANUAL_CHANGE.getOptType());
            userManage.setOptDesc(UserConstant.UserOperateType.LEVEL_MANUAL_CHANGE.getDesc());
            userManage.setCreateTime(dateTime);
            userManage.setFirstTime(dateTime);
            userManage.setFirstApprover(admin);
            userManage.setSecondTime(dateTime);
            userManage.setSecondApprover(admin);
            userManage.setStatus(3);
            userManage.setLockStatus(1);
            userManages.add(userManage);
        }
        userManageService.saveManages(userManages);
    }

    private void doUserUnLock(Integer userId) {
        GlFundUserLevelLock lock = findById(userId);
        if (lock == null) {
            return;
        }
        deleteById(userId);

        //变更上报
        UserSynch userSynch = new UserSynch();
        userSynch.setId(userId);
        userSynch.setLevel_id(1);
        userSynch.setLevel_name("兜底层");
        userSynch.setLevel_status(0);
        userSynch.setLast_update(DateUtils.format(new Date(), "yyyy-MM-dd'T'HH:mm:ss'.000Z'"));
        try {
            reportService.userSynch(userSynch);
        } catch (Exception e) {
            log.error("reportService.userSynch error", e);
        }
    }
}
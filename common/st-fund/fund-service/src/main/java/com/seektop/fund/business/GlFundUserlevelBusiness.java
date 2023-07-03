package com.seektop.fund.business;

import com.alibaba.fastjson.JSON;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.mybatis.utils.DtoUtils;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.FundConstant;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.recharge.GlPaymentMerchantAppBusiness;
import com.seektop.fund.configuration.CacheConfiguration;
import com.seektop.fund.controller.backend.result.FundUserLevelListResult;
import com.seektop.fund.controller.backend.result.FundUserLevelResult;
import com.seektop.fund.controller.backend.result.FundUserLevelVO;
import com.seektop.fund.dto.result.userLevel.UserLevelDO;
import com.seektop.fund.mapper.GlFundUserlevelMapper;
import com.seektop.fund.model.GlFundUserLevelLock;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.model.RechargeFailureLevelConfig;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.utils.Lists;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

@Slf4j
@Component
public class GlFundUserlevelBusiness extends AbstractBusiness<GlFundUserlevel> {

    @Resource
    private GlFundUserlevelMapper glFundUserlevelMapper;
    @Resource
    private GlFundUserLevelLockBusiness fundUserLevelLockBusiness;
    @Autowired
    private RedisService redisService;
    @Autowired
    private GlPaymentMerchantAppBusiness paymentMerchantAppBusiness;
    @Resource
    private RechargeFailureLevelConfigBusiness rechargeFailureLevelConfigBusiness;

    public String getLevelName(final Integer levelId) {
        GlFundUserlevel level = findById(levelId);
        if (ObjectUtils.isEmpty(level)) {
            return null;
        } else {
            return level.getName();
        }
    }

    public String getUserLevelName(Integer userId) {
        GlFundUserLevelLock lock = fundUserLevelLockBusiness.findById(userId);
        int levelId = 1;
        if (null != lock) {
            levelId = lock.getLevelId();
        }
        GlFundUserlevel level = findById(levelId);
        if (null == level) {
            level = findById(1);
        }
        return level.getName();
    }

    public GlFundUserlevel getUserLevel(Integer userId) {
        int levelId = getUserLevelId(userId);
        GlFundUserlevel result = findById(levelId);
        if (result == null) {
            result = findById(1);
        }
        return result;
    }

    public Integer getUserLevelId(Integer userId) {
        GlFundUserLevelLock lock = fundUserLevelLockBusiness.findById(userId);
        int levelId = 1;
        if (null != lock) {
            levelId = lock.getLevelId();
        }
        return levelId;
    }

    public List<GlFundUserlevel> findByLevelIds(List<Integer> levelIds){
        if(CollectionUtils.isEmpty(levelIds)){
            return Lists.newArrayList();
        }
        StringBuffer ids = new StringBuffer();
        levelIds.forEach(id -> ids.append(",").append(id));
        return findByIds(ids.toString().replaceFirst(",", ""));
    }

    public FundUserLevelResult findByUserIds(List<Integer> userIds){
        List<GlFundUserLevelLock> levelLocks = fundUserLevelLockBusiness.findByUserIds(userIds);
        List<Integer> levelIds = levelLocks.stream().map(GlFundUserLevelLock::getLevelId).distinct().collect(Collectors.toList());
        List<GlFundUserlevel> levels = findByLevelIds(levelIds);
        FundUserLevelResult result = new FundUserLevelResult();
        result.setLevelLocks(levelLocks);
        result.setLevels(levels);
        return result;
    }

    public List<UserLevelDO> getUserLevels(List<Integer> userIds){
        List<GlFundUserLevelLock> levelLocks = fundUserLevelLockBusiness.findByUserIds(userIds);
        List<Integer> levelIds = levelLocks.stream().map(GlFundUserLevelLock::getLevelId)
                .distinct().collect(Collectors.toList());
        List<GlFundUserlevel> userlevels = findByLevelIds(levelIds);
        return userIds.stream().map(userId -> {
            Integer levelId = levelLocks.stream()
                    .filter(lock -> lock.getUserId().equals(userId))
                    .map(GlFundUserLevelLock::getLevelId)
                    .findFirst()
                    .orElse(1);
            GlFundUserlevel userLevel = userlevels.stream().filter(l -> levelId.equals(l.getLevelId()))
                    .findFirst().orElse(findById(levelId));
            UserLevelDO userLevelDO = new UserLevelDO();
            userLevelDO.setUserId(userId);
            userLevelDO.setLevelId(userLevel.getLevelId());
            userLevelDO.setLevelName(userLevel.getName());
            userLevelDO.setLevelType(userLevel.getLevelType());
            return userLevelDO;
        }).collect(Collectors.toList());
    }

    public Optional<GlFundUserlevel> filter(FundUserLevelResult result, Integer userId){
        List<GlFundUserLevelLock> levelLocks = result.getLevelLocks();
        List<GlFundUserlevel> levels = result.getLevels();


        Optional<GlFundUserlevel> first = levelLocks.stream()
                .filter(l -> userId.equals(l.getUserId()))
                .map(l -> levels.stream().filter(level -> l.getLevelId().equals(level.getLevelId())).findFirst().get())
                .findFirst();
        GlFundUserlevel level;
        if(first.isPresent()) {
            level = first.get();
        }
        else if(ObjectUtils.isEmpty(result.getDefaultLevel())) {
            level = findById(1);
            result.setDefaultLevel(level);
        }
        else {
            level = result.getDefaultLevel();
        }
        return Optional.ofNullable(level);
    }

    @Cacheable(value = CacheConfiguration.FUND_USER_LEVEL, unless = "#result == null")
    public GlFundUserlevel findById(Integer id){
        return super.findById(id);
    }

    public List<FundUserLevelVO> findAllLevel() {
        RedisResult<FundUserLevelVO> redisResult = redisService.getListResult(RedisKeyHelper.USER_LEVEL_CACHE, FundUserLevelVO.class);
        if (ObjectUtils.isEmpty(redisResult)) {
            return null;
        }
        List<FundUserLevelVO> result = redisResult.getListResult();
        if (ObjectUtils.isEmpty(result)) {
            List<GlFundUserlevel> list = glFundUserlevelMapper.selectAll();
            result = DtoUtils.transformList(list, FundUserLevelVO.class);
        }
        return result;
    }

    public List<FundUserLevelListResult> findByLevelType(Integer levelType){
        List<FundUserLevelVO> data = findAllLevel();
        if (CollectionUtils.isEmpty(data)) {
            return Lists.newArrayList();
        }
        // 过滤搜索的层级类型
        if (levelType != -1) {
            data = data.stream().filter(item -> item.getLevelType().equals(levelType)).collect(Collectors.toList());
        }
        // 查询下层级的连续充值失败次数配置
        Map<Integer, RechargeFailureLevelConfig> configMap = rechargeFailureLevelConfigBusiness.getAllConfig();
        List<FundUserLevelListResult> list = data.stream().map(level -> {
            FundUserLevelListResult userLevelListResult = new FundUserLevelListResult();
            BeanUtils.copyProperties(level, userLevelListResult);
            userLevelListResult.setLockUsers(fundUserLevelLockBusiness.count(level.getLevelId(), 1));
            Integer merchantCount = paymentMerchantAppBusiness.getLevelMerchantCount(level.getLevelId());
            userLevelListResult.setMerchantCount(merchantCount);
            userLevelListResult.setWithdrawTag(FundConstant.WITHDRAW_TAG + level.getLevelId());
            // 设置新老用户的充值失败次数
            RechargeFailureLevelConfig rechargeFailureLevelConfig = configMap.get(level.getLevelId());
            if (rechargeFailureLevelConfig != null) {
                userLevelListResult.setNewUserRechargeFailureTimes(rechargeFailureLevelConfig.getNewUserTimes());
                userLevelListResult.setOldUserRechargeFailureTimes(rechargeFailureLevelConfig.getOldUserTimes());
                userLevelListResult.setTargetLevelId(rechargeFailureLevelConfig.getTargetLevelId());
                userLevelListResult.setTargetLevelName(rechargeFailureLevelConfig.getTargetLevelName());
                userLevelListResult.setVips(rechargeFailureLevelConfig.getVips());
            }
            return userLevelListResult;
        }).collect(Collectors.toList());
        return list;
    }

    @CacheEvict(value = CacheConfiguration.FUND_USER_LEVEL, allEntries = true)
    public void save(GlFundUserlevel fundUserlevel, GlAdminDO admin) throws GlobalException {
        List<FundUserLevelVO> list = findAllLevel();
        Collections.sort(list, Comparator.comparing(GlFundUserlevel::getSortId));

        if (list.stream().anyMatch(l -> l.getName().equals(fundUserlevel.getName()))) {
            throw new GlobalException("层级名称重复,请修改名称!");
        }

        for (int i = 0; i < list.size(); i++) {
            //排序值与现存排序值是否冲突
            int sort = (i + 1);
            GlFundUserlevel level = list.get(i);
            //冲突时，冲突下标自后的对象排序值全部+1
            if (sort >= fundUserlevel.getSortId()) {
                sort = sort + 1;
            }
            if(sort != level.getSortId()) {
                level.setSortId(sort);
                updateByPrimaryKey(level);
            }
        }

        Date now = new Date();
        fundUserlevel.setRegFromTime(now);
        fundUserlevel.setRegEndTime(now);
        fundUserlevel.setCreateTime(now);
        fundUserlevel.setCreator(admin.getUsername());
        fundUserlevel.setCreatorId(admin.getUserId());
        fundUserlevel.setLastOperator(admin.getUsername());
        fundUserlevel.setLastOperatorId(admin.getUserId());
        fundUserlevel.setLastUpdate(now);
        save(fundUserlevel);

        updateCache();
    }

    @CacheEvict(value = CacheConfiguration.FUND_USER_LEVEL, allEntries = true)
    public void update(GlFundUserlevel fundUserlevel, GlAdminDO admin) throws GlobalException {
        //1 找到源顺序
        List<GlFundUserlevel> list = DtoUtils.transformList(findAllLevel(), GlFundUserlevel.class);
        Collections.sort(list, Comparator.comparing(GlFundUserlevel::getSortId));

        //2 获取修改前的对象
        int oriIndex = 0;
        GlFundUserlevel oldlevel;
        for (int i = 0; i < list.size(); i++) {
            oldlevel = list.get(i);
            if (ObjectUtils.isEmpty(oldlevel)) {
                continue;
            }
            if (oldlevel.getLevelId() == fundUserlevel.getLevelId()) {
                oriIndex = i;
                break;
            }
            if (oldlevel.getName().equals(fundUserlevel.getName()) && !oldlevel.getLevelId().equals(fundUserlevel.getLevelId())) {
                throw new GlobalException("层级名称重复,请修改名称!");
            }
        }
        GlFundUserlevel temp1 = list.get(oriIndex);
        //更新前后排序有变化
        if (temp1.getSortId() != fundUserlevel.getSortId()) {
            //踢出原对象
            list.remove(oriIndex);
            list.add((fundUserlevel.getSortId() - 1) <= list.size() ? fundUserlevel.getSortId() - 1 : list.size(), fundUserlevel);

            for (int i = 0; i < list.size(); i++) {
                list.get(i).setSortId(i + 1);
                updateByPrimaryKeySelective(list.get(i));
            }
        }

        //更新其他信息
        Date now = new Date();
        fundUserlevel.setLastOperator(admin.getUsername());
        fundUserlevel.setLastOperatorId(admin.getUserId());
        fundUserlevel.setLastUpdate(now);
        fundUserlevel.setPayment(null);
        updateByPrimaryKeySelective(fundUserlevel);

        updateCache();
    }

    /**
     * 更新缓存
     */
    public void updateCache(){
        List<GlFundUserlevel> allLevelList = findAll();
        Collections.sort(allLevelList, Comparator.comparing(GlFundUserlevel::getSortId));
        redisService.delete(RedisKeyHelper.USER_LEVEL_CACHE);
        redisService.set(RedisKeyHelper.USER_LEVEL_CACHE, allLevelList, -1);
    }

    @CacheEvict(value = CacheConfiguration.FUND_USER_LEVEL, allEntries = true)
    public void delete(Integer levelId){
        deleteById(levelId);
        fundUserLevelLockBusiness.deleteLevelLocks(levelId);
        //更新排序值
        List<GlFundUserlevel> list = findAll();
        Collections.sort(list, Comparator.comparing(GlFundUserlevel::getSortId));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).setSortId(i + 1);
            updateByPrimaryKeySelective(list.get(i));
        }
    }
}
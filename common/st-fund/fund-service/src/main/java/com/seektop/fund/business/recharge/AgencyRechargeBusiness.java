package com.seektop.fund.business.recharge;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.mybatis.business.AbstractBusiness;
import com.seektop.common.redis.RedisService;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.mapper.AgencyRechargeMapper;
import com.seektop.fund.model.AgencyRecharge;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.fund.vo.AgencyRechargeQueryDto;
import com.seektop.fund.vo.AgencyRechargeVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tk.mybatis.mapper.entity.Condition;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.util.Date;
import java.util.List;

@Slf4j
@Component
public class AgencyRechargeBusiness extends AbstractBusiness<AgencyRecharge> {
    @Autowired
    private AgencyRechargeMapper agencyRechargeMapper;

    @Resource
    private GlFundUserlevelBusiness glFundUserlevelService;

    @Resource
    private RedisService redisService;

    public Integer createCode(GlUserDO glUser, Integer code, Integer appType) {
        AgencyRecharge agencyRecharge = new AgencyRecharge();
        agencyRecharge.setCode(code);
        agencyRecharge.setUserId(glUser.getId());
        agencyRecharge.setAppType(appType);
        agencyRecharge.setUserName(glUser.getUsername());
        agencyRecharge.setUserType(glUser.getUserType());
        GlFundUserlevel glFundUserlevel = glFundUserlevelService.getUserLevel(glUser.getId());
        agencyRecharge.setUserLevelName(glFundUserlevel.getName());
        agencyRecharge.setUserLevel(glFundUserlevel.getLevelId());
        agencyRecharge.setCreateDate(new Date());
        agencyRechargeMapper.insert(agencyRecharge);
        agencyRecharge.setId(agencyRecharge.getId());
        return agencyRecharge.getId();
    }

    public PageInfo<AgencyRecharge> query(AgencyRechargeQueryDto queryDto) {
        PageHelper.startPage(queryDto.getPage(), queryDto.getSize());
        List<AgencyRecharge> list = agencyRechargeMapper.listAgencyRecharge(queryDto);
        return new PageInfo(list);
    }

    public List<AgencyRechargeVO> findCodes(AgencyRechargeQueryDto queryDto) {
        return agencyRechargeMapper.findCodes(queryDto);
    }

    @Transactional(rollbackFor = {GlobalException.class})
    public void doAgencyRechargeSubmit(String orderId, GlUserDO user, Integer agencyId, GlAdminDO glAdmin) throws GlobalException {
        AgencyRecharge agencyRecharge = new AgencyRecharge();
        agencyRecharge.setId(agencyId);
        agencyRecharge.setOrderId(orderId);
        agencyRecharge.setAdminId(glAdmin.getUserId());
        agencyRecharge.setAdminName(glAdmin.getUsername());
        agencyRechargeMapper.updateByPrimaryKeySelective(agencyRecharge);
        redisService.delete(RedisKeyHelper.AGENT_RECHARGE + user.getUsername());
        redisService.delete(RedisKeyHelper.AGENT_RECHARGE + user.getUsername() + "-" + agencyId);
    }

    public void deleteHistoryData(Date date) {
        Condition condition = new Condition(AgencyRecharge.class);
        Example.Criteria criteria = condition.createCriteria();
        criteria.andLessThan("createDate", date);
        int count = agencyRechargeMapper.deleteByCondition(condition);
        log.info("清理代客充值码库数据  date ：{}  count： {}", date, count);
    }
}

package com.seektop.fund.business.withdraw;

import com.github.pagehelper.PageHelper;
import com.github.pagehelper.PageInfo;
import com.seektop.common.redis.RedisResult;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.data.ReportPageResult;
import com.seektop.data.service.UserService;
import com.seektop.dto.UserDetailDO;
import com.seektop.fund.business.withdraw.config.dto.GlWithdrawUserCheckConfig;
import com.seektop.fund.mapper.GlWithdrawMapper;
import com.seektop.fund.mapper.GlWithdrawUserCheckMapper;
import com.seektop.fund.model.GlWithdrawUserCheck;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import tk.mybatis.mapper.entity.Example;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.*;

@Component
@Slf4j
public class GlWithdrawUserCheckBusiness {
    public static String WithdrawUserCheckSettingKey = "WITHDRAW_USER_CHECK_SETTING_KEY";

    @Resource
    private GlWithdrawUserCheckMapper mapper;
    @Resource
    private RedisService redisService;
    @Resource
    private GlWithdrawMapper glWithdrawMapper;
    @DubboReference(retries = 2, timeout = 3000)
    private UserService glUserService;

    public String save(List<GlWithdrawUserCheckConfig> configs) {
        if (configs==null) return "参数错误！";
        if (configs.size()>5)return "最多5条设置！";
        List<Integer> listss = new ArrayList<>();
        Map<Integer,GlWithdrawUserCheckConfig> settings = new HashMap<>();
        settings().forEach(config -> settings.put(config.getId(),config));
        for (GlWithdrawUserCheckConfig config:configs){
            if (config.equals(settings.get(config.getId()))) continue;
            settings.put(config.getId(),config);
            config.setUpDate(false);
            listss.addAll(glWithdrawMapper.getFinishExceptionUserId(new Date(config.getMinTime()), new Date(config.getMaxTime())
                    , BigDecimal.valueOf(config.getMin()), BigDecimal.valueOf(config.getMax()), config.getNum()));
        }
        Map<Integer, GlWithdrawUserCheck> cache = new HashMap<>();
        Date now = new Date();
        listss.forEach(uid -> {
            GlWithdrawUserCheck value = new GlWithdrawUserCheck();
            value.setStatus(0);
            value.setUid(uid);
            UserDetailDO user = Optional.ofNullable(glUserService.getUserDetail(uid).getData())
                    .orElse(glUserService.getProxyUserDetail(uid).getData());
            if (user!=null) {
                value.setUserName(user.getUsername());
                value.setUserType(user.getUserType());
                value.setLevelName(user.getLevelName());
                value.setCreateDate(now);
                value.setLastWithDrawTime(glWithdrawMapper.maxCreateDateWithdrawByUser(value.getUid()));
                cache.put(uid,value);
            }
        });
        redisService.set(WithdrawUserCheckSettingKey,configs);
        if (cache.size()>0){
            Example exa = new Example(GlWithdrawUserCheck.class);
            exa.createCriteria().andIn("uid",cache.keySet()).andEqualTo("status",0);
            mapper.deleteByExample(exa);
            mapper.insertList(new ArrayList<>(cache.values()));
        }
        return "成功";
    }

    public List<GlWithdrawUserCheckConfig> settings() {
        RedisResult<GlWithdrawUserCheckConfig> configs = redisService.getListResult(WithdrawUserCheckSettingKey, GlWithdrawUserCheckConfig.class);
        if (configs!=null){
            List<GlWithdrawUserCheckConfig> listResult = configs.getListResult();
            if (listResult!=null) {
                return listResult;
            }
        }
        return Collections.emptyList();
    }

    public void check(int id ,int status ,String tag ,String remark,String approver) {
        GlWithdrawUserCheck t = new GlWithdrawUserCheck();
        t.setId(id);
        t.setTag(tag);
        t.setStatus(status);
        t.setRiskApprover(approver);
        t.setCheckTime(new Date());
        t.setRemark(remark);
        mapper.updateByPrimaryKeySelective(t);
    }


    public ReportPageResult<GlWithdrawUserCheck> list(Long stime, Long etime, String userName, String approver, List<Integer> status, Integer page, Integer size) {
        ReportPageResult<GlWithdrawUserCheck> result = new ReportPageResult<>();
        PageHelper.startPage(Optional.ofNullable(page).orElse(1), Optional.ofNullable(size).orElse(20));
        Example build = new Example(GlWithdrawUserCheck.class);
        Example.Criteria criteria = build.createCriteria();
        if (StringUtils.isNotBlank(userName))criteria.andEqualTo("userName",userName);
        if (StringUtils.isNotBlank(approver))criteria.andEqualTo("riskApprover",approver);
        if (!ObjectUtils.isEmpty(status))criteria.andIn("status",status);
        if (stime!=null)criteria.andGreaterThanOrEqualTo("lastWithDrawTime",new Date(stime));
        if (etime!=null)criteria.andLessThanOrEqualTo("lastWithDrawTime",new Date(etime));
        build.orderBy("lastWithDrawTime").desc();
        List<GlWithdrawUserCheck> glWithdrawUserChecks = mapper.selectByExample(build);
        PageInfo<GlWithdrawUserCheck> pageInfo = new PageInfo<>(glWithdrawUserChecks);
        result.setPageSize(size);
        result.setTotal(pageInfo.getTotal());
        result.setPageNum(page);
        result.setList(pageInfo.getList());
        return result;
    }
}

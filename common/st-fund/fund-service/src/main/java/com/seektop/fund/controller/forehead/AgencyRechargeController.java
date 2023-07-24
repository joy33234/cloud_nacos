package com.seektop.fund.controller.forehead;


import com.seektop.common.mvc.ParamBaseDO;
import com.seektop.common.redis.RedisService;
import com.seektop.common.rest.Result;
import com.seektop.constant.RedisKeyHelper;
import com.seektop.dto.GlUserDO;
import com.seektop.enumerate.ResultCode;
import com.seektop.fund.business.GlFundUserlevelBusiness;
import com.seektop.fund.business.recharge.AgencyRechargeBusiness;
import com.seektop.fund.model.GlFundUserlevel;
import com.seektop.user.service.GlUserService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

@RestController
@RequestMapping("/forehead/fund/agency/recharge")
@Slf4j
public class AgencyRechargeController extends GlFundForeheadBaseController{

    @Reference(retries = 2, timeout = 3000)
    private GlUserService glUserService;
    @Resource
    private RedisService redisService;
    @Resource
    private AgencyRechargeBusiness agencyRechargeBusiness;
    @Resource
    private GlFundUserlevelBusiness glFundUserlevelBusiness;

    @RequestMapping(value = "/getCode", produces = "application/json;charset=utf-8")
    public Result getCode(@ModelAttribute(value = "userInfo", binding = false) GlUserDO userDO, ParamBaseDO paramBaseDO){
        if(userDO == null){
            return Result.genFailResult(ResultCode.TOEKNUNVALIBLE.getCode(), ResultCode.TOEKNUNVALIBLE.getMessage());
        }
        //开关是否打开
        Object value = redisService.get(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG);
        if(value == null){
            return Result.genSuccessResult(null);
        }
        //层级开关是否打开
        if(value != null){
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(userDO.getId());
            boolean levenOpen = redisService.exists(RedisKeyHelper.AGENCY_USER_LEVEL + userlevel.getLevelId());
            if(!levenOpen){
                return Result.genSuccessResult(null);
            }
        }
        //从redis获取
        Integer agencyId = redisService.get(RedisKeyHelper.AGENT_RECHARGE + userDO.getUsername(), Integer.class);
        if(agencyId != null){
            return Result.genSuccessResult(redisService.get(RedisKeyHelper.AGENT_RECHARGE + userDO.getUsername() + "-" + agencyId));
        }
        //生成code
        Integer code = RandomUtils.nextInt(1000, 9999);
        agencyId = agencyRechargeBusiness.createCode(userDO, code, paramBaseDO.getHeaderAppType());
        redisService.set(RedisKeyHelper.AGENT_RECHARGE + userDO.getUsername() + "-" + agencyId, code, 30*60);
        redisService.set(RedisKeyHelper.AGENT_RECHARGE + userDO.getUsername(), agencyId, 30*60);
        return Result.genSuccessResult(code+"");
    }


    @PostMapping(value = "/getSwitch", produces = "application/json;charset=utf-8")
    public Result getSwitch(@ModelAttribute(value = "userInfo", binding = false) GlUserDO glUser){
        if(glUser == null){
            //用戶未登录，不开放
            return Result.genSuccessResult(false);
        }
        Object value = redisService.get(RedisKeyHelper.AGENT_RECHARGE_SWITCH_CONFIG);
        if(value != null){
            GlFundUserlevel userlevel = glFundUserlevelBusiness.getUserLevel(glUser.getId());
            boolean levenOpen = redisService.exists(RedisKeyHelper.AGENCY_USER_LEVEL + userlevel.getLevelId());
            if(levenOpen){
                return Result.genSuccessResult(true);
            }
        }
        return Result.genSuccessResult(false);
    }
}

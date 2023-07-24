package com.seektop.fund.configuration;

import com.alibaba.fastjson.JSONArray;
import com.seektop.common.encrypt.annotation.EnableEncrypt;
import com.seektop.common.encrypt.service.JobEncryptPermissionService;
import com.seektop.common.redis.RedisService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.rest.rpc.RPCResponse;
import com.seektop.constant.redis.KeyConstant;
import com.seektop.dto.GlAdminDO;
import com.seektop.dto.proxy.ProxyAdminDO;
import com.seektop.dto.proxy.ProxyJobDO;
import com.seektop.exception.GlobalException;
import com.seektop.system.dto.GlSystemDeptJobDO;
import com.seektop.system.service.GlSystemDeptJobService;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.List;

@Component
@DubboComponentScan
@EnableEncrypt
public class EncryptConfiguration {

    @Reference(retries = 3,timeout = 5000)
    private GlSystemDeptJobService glSystemDeptJobService;
    @Resource
    private RedisService redisService;

    @Bean
    public JobEncryptPermissionService jobEncryptPermissionService() {
        return userId -> {
            if(userId > 10000){
                // 表示是SEO代理后台
                ProxyAdminDO userDO = RedisTools.valueOperations().get(KeyConstant.PROXY_BACKEND.PROXY_BACKEND_ADMIN_CACHE + userId, ProxyAdminDO.class);
                ProxyJobDO jobDO = RedisTools.valueOperations().get(KeyConstant.PROXY_BACKEND.PROXY_BACKEND_JOB_CACHE + userDO.getJobId(), ProxyJobDO.class);
                List<Integer> encrypts = JSONArray.parseArray(jobDO.getEncryptList(), Integer.class);
                return encrypts;
            }
            GlAdminDO adminDO = redisService.get(KeyConstant.TOKEN.ADMIN_USER + userId, GlAdminDO.class);
            if(ObjectUtils.isEmpty(adminDO)){
                throw new GlobalException("admin查询异常");
            }
            RPCResponse<GlSystemDeptJobDO> response = glSystemDeptJobService.findByJobId(adminDO.getJobId());
            GlSystemDeptJobDO data = response.getData();
            String encryptList;
            if(ObjectUtils.isEmpty(data) || StringUtils.isEmpty(data.getEncryptList())){
                encryptList = "[]";
            }else{
                encryptList = data.getEncryptList();
            }
            List<Integer> encrypts = JSONArray.parseArray(encryptList, Integer.class);
            return encrypts;
        };
    }

}
package com.seektop.common.encrypt;

import com.seektop.common.encrypt.service.JobEncryptPermissionService;
import com.seektop.common.utils.UserIdUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
public class EncryptComponent {

    @Resource
    private JobEncryptPermissionService jobEncryptPermissionService;
    /**
     * 根据岗位ID得到数据权限
     */
    public List<Integer> getJobEncrypt(){
        if(ObjectUtils.isEmpty(UserIdUtils.getUserId())){
            log.error("脱敏：当前没有获取到userId,默认没有权限查看完整数据");
        }
        return getJobEncryptByToken(UserIdUtils.getUserId());
    }

    public List<Integer> getJobEncryptByToken(Integer userId) {
        List<Integer> jobEncrypts;
        try {
            jobEncrypts = jobEncryptPermissionService.findByUserId(userId);
        } catch (Exception e) {
            log.error("查询权限异常", e);
            jobEncrypts = new ArrayList<>();
        }
        return jobEncrypts;
    }
}

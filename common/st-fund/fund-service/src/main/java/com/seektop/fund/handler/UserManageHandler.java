package com.seektop.fund.handler;

import com.seektop.dto.GlAdminDO;
import com.seektop.fund.controller.backend.dto.withdraw.WithdrawExceptionApproveDto;
import com.seektop.fund.model.GlWithdraw;
import com.seektop.user.dto.UpdateLockUpDto;
import com.seektop.user.service.UserManageService;
import lombok.extern.slf4j.Slf4j;
import org.apache.dubbo.config.annotation.DubboReference;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserManageHandler {

    @DubboReference(timeout = 3000)
    private UserManageService userManageService;

    public void updateLockUp(GlWithdraw withdraw, WithdrawExceptionApproveDto approveDto,
                             GlAdminDO admin) {
        UpdateLockUpDto lockUpDto = new UpdateLockUpDto();
        lockUpDto.setUserId(withdraw.getUserId());
        lockUpDto.setUserType(withdraw.getUserType());
        lockUpDto.setOptType(1 == approveDto.getOperation() ? 4 : 5);
        lockUpDto.setRemark(approveDto.getRemark());
        lockUpDto.setAdmin(admin);
        try {
            userManageService.updateLockUp(lockUpDto);
        }
        catch (Exception e) {
            log.error("申请锁定或间接锁定异常", e);
        }
    }
}

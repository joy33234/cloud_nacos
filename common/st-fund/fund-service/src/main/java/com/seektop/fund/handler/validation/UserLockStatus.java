package com.seektop.fund.handler.validation;

import com.seektop.constant.user.UserConstant;
import com.seektop.dto.GlUserDO;
import com.seektop.exception.GlobalException;
import lombok.AllArgsConstructor;

@AllArgsConstructor
public class UserLockStatus implements DataValidation {

    private Integer operation;
    private GlUserDO user;

    @Override
    public void valid() throws GlobalException {
        if (operation > 0) {
            // 完全锁定状态,不能再次完全锁定
            if (UserConstant.Status.LOCKED == user.getStatus()) {
                throw new GlobalException("用户已经是锁定状态");
            }
            if (2 == operation && UserConstant.Status.HALF_LOCKED == user.getStatus()) {
                throw new GlobalException("用户已经是间接锁定状态");
            }
        }
    }
}

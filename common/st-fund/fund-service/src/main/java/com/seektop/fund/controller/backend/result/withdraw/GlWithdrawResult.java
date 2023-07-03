package com.seektop.fund.controller.backend.result.withdraw;

import com.seektop.fund.controller.backend.dto.withdraw.GlWithdrawDO;
import com.seektop.fund.model.GlWithdrawReceiveInfo;
import lombok.Data;

import java.io.Serializable;

@Data
public class GlWithdrawResult implements Serializable {

    private GlWithdrawDO withdrawDO;

    private GlWithdrawReceiveInfo withdrawReceiveInfo;
}

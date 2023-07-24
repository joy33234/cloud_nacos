package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
public class RejectWithdrawRequestDO implements Serializable {

    /**
     * 平台提现开关：0-开启提现、1-关闭提现
     */
    private String type;

    /**
     * 公告标题
     */
    private String title;

    /**
     * 公告内容
     */
    private String content;

}

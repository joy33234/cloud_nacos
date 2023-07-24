package com.seektop.fund.dto.param.account;

import com.seektop.dto.GlUserDO;
import lombok.Data;

import java.io.Serializable;

@Data
public class FundProxyAccountDto implements Serializable {

    private static final long serialVersionUID = 8935147604776064055L;

    private GlUserDO user;

    private String creator;

    private Integer proxyType; // 代理类型:0 外部代理 ,1  内部代理; 默认为外部代理
}

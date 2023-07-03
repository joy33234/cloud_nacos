package com.seektop.fund.controller.backend.dto.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawUsdtResult implements Serializable {

    private static final long serialVersionUID = -7025670264345362560L;

    private int usdtId;

    private String nickName;

    private String protocol;

    private String address;

    private int selected;

}

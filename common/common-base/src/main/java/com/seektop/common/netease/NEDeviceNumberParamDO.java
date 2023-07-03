package com.seektop.common.netease;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@Builder
public class NEDeviceNumberParamDO implements Serializable {

    private String secretId;

    private String secretKey;

    private String businessId;

    private String api;

    private String token;

}
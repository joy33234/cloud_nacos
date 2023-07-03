package com.seektop.common.mvc;

import lombok.Data;

import java.io.Serializable;

@Data
public class ProxyBackendParamBaseDO implements Serializable {

    private Long headerUid;

    private String requestIp;

    private String requestUrl;

    private String headerHost;

    private String headerDeviceId;

    private String headerToken;
}

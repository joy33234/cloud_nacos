package com.seektop.common.aws.model;

import lombok.Data;

@Data
public class AwsConfig {
    /**
     *  地区编码
     */
    private Integer areaCode;

    /**
     *
     */
    private String accessId;

    /**
     *
     */
    private String secretKey;

    /**
     *
     */
    private String regions;

    /**
     *
     */
    private String bucketName;

    /**
     *
     */
    private String domain;

    private String protocol;

}

package com.seektop.common.aws.domain;

import com.amazonaws.regions.Regions;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class BucketDomain {

    /**
     * 桶名称
     */
    private String bucketName;

    /**
     *
     */
    private Regions regions;
    /**
     * create
     */
    /**
     * delete
     */
    /**
     * list
     */
}

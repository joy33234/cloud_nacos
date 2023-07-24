package com.seektop.common.aws.model;


import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class SlicePartParam {
    private Integer partSize;
    private Integer partId;
    private String uploadId;
    private String partMd5;
}

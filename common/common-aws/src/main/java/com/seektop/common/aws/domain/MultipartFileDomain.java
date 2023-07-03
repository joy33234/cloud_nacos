package com.seektop.common.aws.domain;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.services.s3.model.ObjectMetadata;
import com.amazonaws.services.s3.model.PartETag;
import com.seektop.common.aws.model.AwsConfig;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MultipartFileDomain {
    private AwsConfig awsConfig;

    /**
     * Etags
     */
    List<PartETag> partETags = new ArrayList<PartETag>();

    List<Integer> uploadIds = new ArrayList<>();

    /**
     *
     */
    private String uploadId;

    /**
     * 存储路径
     */
    private String path;

    /**
     *
     */
    private File file;

    /**
     *
     */
    private InputStream inputStream;

    /**
     *
     */
    private Consumer<ProgressEvent> progressListener;

    /**
     * 是否全局可读
     */
    private Boolean publicRead;

    /**
     *
     */
    private ObjectMetadata objectMetadata;

    /**
     * 是否异步
     */
    private boolean async;
}

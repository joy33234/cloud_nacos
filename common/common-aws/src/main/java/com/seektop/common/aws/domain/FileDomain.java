package com.seektop.common.aws.domain;

import com.amazonaws.event.ProgressEvent;
import com.amazonaws.event.ProgressEventType;
import com.amazonaws.event.ProgressListener;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.model.*;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import com.google.common.collect.Lists;
import com.seektop.common.aws.AwsTools;
import com.seektop.common.aws.model.AwsConfig;
import com.seektop.common.aws.model.SlicePartParam;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.io.File;
import java.io.InputStream;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * 接受文件
 * 计算总进度
 * 释放资源
 */
@Setter
@Getter
@Builder
public class FileDomain {

    private AwsConfig awsConfig;


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

    private AmazonS3 client;

    private String uploadId;

    /**
     * 是否异步
     */
    private boolean async;

    public FileDomain upload(File file) {
        PutObjectRequest request = new PutObjectRequest(
                Optional.ofNullable(awsConfig.getBucketName()).orElse(""),
                Optional.ofNullable(path).orElse(""),
                file
        ).withMetadata(objectMetadata)
                .withCannedAcl(CannedAccessControlList.PublicRead);
        transfer(request);
        return this;
    }

    public FileDomain upload(InputStream inputStream) {
        PutObjectRequest request = new PutObjectRequest(
                Optional.ofNullable(awsConfig.getBucketName()).orElse(""),
                Optional.ofNullable(path).orElse(""),
                inputStream,
                objectMetadata
        );
        if (publicRead) {
            request.withCannedAcl(CannedAccessControlList.PublicRead);
        }
        transfer(request);
        return this;
    }

    private void transfer(PutObjectRequest request) {
        TransferManager transferManager = AwsTools.getTransferManager(awsConfig);
        Upload upload = transferManager
                .upload(request);
        upload.addProgressListener((ProgressListener) progressEvent -> {
            // 执行监听
            if (null != progressListener) {
                progressListener.accept(progressEvent);
            }
            if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(progressEvent.getEventType())) {
                // 表示传输完成
                // 关闭client 和 transferManager
                transferManager.shutdownNow(true);
                // 默认更新redis
            }
        });
        try {
            if (!async) {
                UploadResult uploadResult = upload.waitForUploadResult();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public String sliceUploadInit(ObjectMetadata objectMetadata) {
        //指定容器与对象名，也可以在request中同时设置metadata，acl等对象的属性
        client = AwsTools.getAmazonS3(awsConfig);
        InitiateMultipartUploadRequest initUploadRequest = new InitiateMultipartUploadRequest(
                Optional.ofNullable(awsConfig.getBucketName()).orElse("")
                , path);
        if (publicRead) {
            initUploadRequest.withCannedACL(CannedAccessControlList.PublicRead);
        }
        initUploadRequest.setObjectMetadata(objectMetadata);
        InitiateMultipartUploadResult initResult = client.initiateMultipartUpload(initUploadRequest);
        //用于区分分片上传的唯一标识，后续的操作中会使用该id
        String uploadId = initResult.getUploadId();
        return uploadId;
    }

    public PartETag uploadPart(SlicePartParam slicePartParam, InputStream inputStream) {
        client = AwsTools.getAmazonS3(awsConfig);
        UploadPartRequest uploadPartRequest = new UploadPartRequest();
        uploadPartRequest.setBucketName(Optional.ofNullable(awsConfig.getBucketName()).orElse(""));
        uploadPartRequest.setKey(path);
        uploadPartRequest.setUploadId(uploadId);
        uploadPartRequest.setObjectMetadata(objectMetadata);
        //设置输入流
        uploadPartRequest.setInputStream(inputStream);
        //设置分片大小，最小为4MB
        uploadPartRequest.setPartSize(slicePartParam.getPartSize());
        //设置分片号，范围是1~10000
        uploadPartRequest.setPartNumber(slicePartParam.getPartId());
        uploadPartRequest.withGeneralProgressListener(progressEvent -> {
            // 执行监听
            if (null != progressListener) {
                progressListener.accept(progressEvent);
            }
            if (ProgressEventType.TRANSFER_COMPLETED_EVENT.equals(progressEvent.getEventType())) {
                // 表示传输完成
            }
        });
        UploadPartResult uploadPartResult = client.uploadPart(uploadPartRequest);
        return uploadPartResult.getPartETag();
    }

    public CompleteMultipartUploadResult merge(List<PartETag> partETags) {
        //将partEtag按照分片号升序排序
        Collections.sort(partETags, Comparator.comparingInt(PartETag::getPartNumber));
        CompleteMultipartUploadRequest completeMultipartUploadRequest =
                new CompleteMultipartUploadRequest(
                        awsConfig.getBucketName()
                        , path
                        , uploadId
                        , partETags);
        //成功完成分片上传后，服务端会返回该对象的信息
        CompleteMultipartUploadResult result = client.completeMultipartUpload(completeMultipartUploadRequest);
        return result;
    }

    public void abort() {
        AbortMultipartUploadRequest request = new AbortMultipartUploadRequest(awsConfig.getBucketName(), path, uploadId);
        AwsTools.getAmazonS3(awsConfig).abortMultipartUpload(request);
    }

    public void getUploadedParts() {
        PartListing partListing;
        ListPartsRequest request = new ListPartsRequest(awsConfig.getBucketName(), path, uploadId);
        List<PartSummary> partSummaries = Lists.newArrayList();
        do {
            partListing = client.listParts(request);
            partSummaries.addAll(partListing.getParts());
            request.setPartNumberMarker(partListing.getNextPartNumberMarker());
        } while (partListing.isTruncated());
    }

    public void getUploadedPartsCount() {
        PartListing partListing;
        ListPartsRequest request = new ListPartsRequest(awsConfig.getBucketName(), path, uploadId);
        partListing = client.listParts(request);
    }


    public void delete() {
        AwsTools.getAmazonS3(awsConfig).deleteObject(this.awsConfig.getBucketName(), this.path);
    }
}

package com.seektop.common.aws.bucket;

import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.seektop.common.aws.domain.AuthDomain;
import com.seektop.common.aws.domain.FileDomain;

import java.util.List;

public class AwsFileUtils {

    private AuthDomain authDomain;

    private static AmazonS3 amazonS3;

    private TransferManager transferManager;

    /**
     * upload
     * upload with progress
     */
    public void upload(FileDomain fileDomain){
        fileDomain.upload(fileDomain.getFile());
    }

    /**
     *  file delete
     */
    public void delete(FileDomain fileDomain){
        amazonS3.deleteObject("","");
    }
    /**
     * file list
     */
    public List list(){
        return amazonS3.listObjectsV2("", "").getObjectSummaries();
    }

}

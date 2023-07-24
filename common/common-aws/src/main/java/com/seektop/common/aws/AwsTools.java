package com.seektop.common.aws;

import com.amazonaws.regions.Regions;
import com.amazonaws.services.chime.AmazonChime;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.TransferManagerBuilder;
import com.seektop.common.aws.domain.AuthDomain;
import com.seektop.common.aws.model.AwsConfig;


public class AwsTools {



    public static AmazonS3 getAmazonS3(AwsConfig awsConfig) {
        Regions regions = Regions.fromName(awsConfig.getRegions());
        return AuthDomain.builder()
                .protocol(awsConfig.getProtocol())
                .accessId(awsConfig.getAccessId())
                .secretKey(awsConfig.getSecretKey())
                .regions(regions)
                .build()
                .auth();
    }

    public static AmazonChime getAmazonChime(AwsConfig awsConfig) {
        Regions regions = Regions.fromName(awsConfig.getRegions());
        return AuthDomain.builder()
                .accessId(awsConfig.getAccessId())
                .secretKey(awsConfig.getSecretKey())
                .regions(regions)
                .build()
                .authChime();
    }

    /**
     * 需要手动关闭
     * @param
     * @return
     */
    public static TransferManager getTransferManager(AwsConfig awsConfig) {
       return TransferManagerBuilder.standard()
                .withS3Client(
                     getAmazonS3(awsConfig)
                ).build();
    }
}

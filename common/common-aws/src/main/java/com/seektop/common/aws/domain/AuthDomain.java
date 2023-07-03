package com.seektop.common.aws.domain;

import com.amazonaws.auth.AWSStaticCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.client.builder.AwsClientBuilder;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.chime.AmazonChime;
import com.amazonaws.services.chime.AmazonChimeClientBuilder;
import com.amazonaws.services.s3.AmazonS3;
import com.amazonaws.services.s3.AmazonS3ClientBuilder;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Builder
public class AuthDomain {
    /**
     * 地区
     */
    private Regions regions;
    /**
     * 密钥id
     */
    private String accessId;
    /**
     * 密钥key
     */
    private String secretKey;

    private String protocol;

    /**
     * 授权
     * @return
     *
     * 4baa126a-3139-4ff7-8a63-b19765deb0e0asdasdad
     */
    public AmazonS3 auth(){
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessId, secretKey);
        AwsClientBuilder.EndpointConfiguration configuration =
                new AwsClientBuilder.EndpointConfiguration(protocol+"://s3."+regions.getName()+".amazonaws.com",regions.getName()) ;
        return AmazonS3ClientBuilder
                .standard()
//                .withRegion(regions)
                .withEndpointConfiguration(configuration)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
    }
    public AmazonChime authChime(){
        BasicAWSCredentials awsCredentials = new BasicAWSCredentials(accessId, secretKey);
        return AmazonChimeClientBuilder
                .standard()
                .withRegion(regions)
                .withCredentials(new AWSStaticCredentialsProvider(awsCredentials))
                .build();
    }


}

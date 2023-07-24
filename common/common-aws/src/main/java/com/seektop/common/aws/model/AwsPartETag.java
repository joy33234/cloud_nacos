package com.seektop.common.aws.model;

import com.amazonaws.services.s3.model.PartETag;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class AwsPartETag extends PartETag {

    private int partNumber;

    private String eTag;

    public AwsPartETag(){
        super(0,"");
    }

    public AwsPartETag(int partNumber, String eTag) {
        super(partNumber, eTag);
    }

}

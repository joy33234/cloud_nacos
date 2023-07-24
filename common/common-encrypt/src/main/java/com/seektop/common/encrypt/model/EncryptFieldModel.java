package com.seektop.common.encrypt.model;



import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import lombok.Builder;
import lombok.Data;
import lombok.experimental.Tolerate;

import java.util.List;


@Data
@Builder
public class EncryptFieldModel {

    @Tolerate
    public EncryptFieldModel(){}
    /**
     * 脱敏字段
     */
    private String fieldName;

    /**
     * 脱敏类型
     */
    private List<EncryptTypeEnum> typeEnums;
}

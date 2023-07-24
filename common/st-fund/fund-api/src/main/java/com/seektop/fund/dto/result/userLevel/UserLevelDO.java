package com.seektop.fund.dto.result.userLevel;

import lombok.Data;

import java.io.Serializable;

@Data
public class UserLevelDO implements Serializable {
    private static final long serialVersionUID = 8944689214023368263L;

    private Integer userId;

    private Integer levelId;

    private String levelName;

    private Integer levelType;

}

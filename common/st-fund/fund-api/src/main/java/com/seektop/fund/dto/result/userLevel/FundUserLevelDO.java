package com.seektop.fund.dto.result.userLevel;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FundUserLevelDO implements Serializable {

	private Integer levelId;

    private String levelName;

    private Integer levelType;

}
package com.seektop.fund.controller.backend.param.recharge.agency;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class LevelSettingDto {
    private Integer levelId;
    private Integer value;
    private String levelName;

    public LevelSettingDto(Integer levelId, Integer value, String levelName) {
        this.levelId = levelId;
        this.value = value;
        this.levelName = levelName;
    }
}

package com.ruoyi.okx.domain;

import com.ruoyi.common.core.web.domain.CommonEntity;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class OkxStrategy extends CommonEntity {

    private Integer id;

    private Integer accountId;

    private String settingIds;

    private String strategyName;


    public OkxStrategy(Integer accountId) {
        this.accountId = accountId;
    }
}

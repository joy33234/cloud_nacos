package com.seektop.fund.controller.backend.dto;

import com.seektop.dto.GlUserDO;
import com.seektop.fund.model.GlFundUserlevel;
import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class FundUserLeveLockDto implements Serializable {

    @NotNull(message = "层级id不能为空")
    private Integer levelId;
    @NotNull(message = "用户名不能为空")
    private List<String> username;

    private List<GlUserDO> users;
    private GlFundUserlevel level;
    private String admin;
    private Date updateTime;
}

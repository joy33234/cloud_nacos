package com.seektop.fund.controller.forehead.param.userCard;

import com.seektop.common.mvc.ParamBaseDO;
import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class ApplyBindCardDto extends ParamBaseDO {
    private static final long serialVersionUID = 7080548587129524580L;

    @NotBlank(message = "银行卡不能为空")
    private String cardNo;
    /**
     * 持卡人姓名
     */
    private String name;
    /**
     * 照片地址
     */
    @NotBlank(message = "请上传照片")
    private String imagePath;
}

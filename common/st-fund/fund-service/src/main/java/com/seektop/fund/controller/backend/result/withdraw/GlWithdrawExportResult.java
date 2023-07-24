package com.seektop.fund.controller.backend.result.withdraw;

import com.seektop.fund.vo.GlWithdrawDetailDto;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@ToString
public class GlWithdrawExportResult extends GlWithdrawDetailDto {

    private static final long serialVersionUID = -2736281199665872279L;
    /**
     * 姓名
     */
    private String reallyName;
    /**
     * 电话
     */
    private String telephone;
    /**
     * 处理过 -1 ;
     */
    private String dealWithStatus;

    /**
     * 三方自动出款中：是否显示“退回”、“成功”按钮
     */
    private Boolean onShow;

    /**
     *  用户类型：0会员，1代理
     */
    private Integer userType;

    /**
     * 用户层级转换
     */
    private String userLevelName;


}

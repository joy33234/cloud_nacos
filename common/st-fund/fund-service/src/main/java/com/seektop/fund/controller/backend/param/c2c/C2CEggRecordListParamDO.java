package com.seektop.fund.controller.backend.param.c2c;

import com.seektop.common.mvc.ManageParamBaseDO;
import lombok.Getter;
import lombok.Setter;

import java.util.Date;

@Getter
@Setter
public class C2CEggRecordListParamDO extends ManageParamBaseDO {

    /**
     * 开始时间
     */
    private Date startDate;

    /**
     * 结束时间
     */
    private Date endDate;

    /**
     * C2C彩蛋类型
     *
     * @see com.seektop.enumerate.fund.C2CEggTypeEnum
     */
    private Short type;

    /**
     * C2C彩蛋状态
     *
     * @see com.seektop.enumerate.fund.C2CEggStatusEnum
     */
    private Short status;

    /**
     * 创建人
     */
    private String creator;

    /**
     * 修改人
     */
    private String updater;

    private Integer page = 1;

    private Integer size = 20;

}
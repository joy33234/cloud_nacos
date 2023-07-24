package com.seektop.fund.vo;

import com.seektop.enumerate.Language;
import lombok.Data;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.Date;
import java.util.List;

@Data
public class RechargeApproveQueryDto extends ManageParamBase implements Serializable {

    private static final long serialVersionUID = -2224750752902403961L;

    /**
     * 分页参数
     */
    private Integer page = 1;
    private Integer size = 10;

    private Integer startIndex = 1;
    private Integer endIndex = 1;

    /**
     * 查询时间段
     */
    private Date startTime;
    private Date endTime;
    //订单号
    private String orderId;
    //账户名
    private String userName;
    //姓名
    private String reallyName;
    /**
     * 用户类型
     */
    private Integer userType = -1;

    private List<Integer> userIdList;

    /**
     * 0：普通充值，1：大额充值
     */
    private Integer limitType = -1;
    /**
     * 补单申请人
     */
    private String applicant;
    /**
     * 补单审核人
     */
    private String auditor;

    /**
     * 审核状态 -1全部、 0 已审核、1 待审核
     */
    private Integer approveStatus = -1;

    private Integer userId;

    /**
     * 币种
     */
    private String coinCode = "-1";
}

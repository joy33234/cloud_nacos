package com.seektop.fund.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;

/**
 * 新财务-提现记录查询Dto
 */
@Data
public class GlWithdrawQueryDto extends ManageParamBase implements Serializable {


    private static final long serialVersionUID = -7998058591768301862L;

    /**
     * 分页参数
     */
    private Integer page = 1;
    private Integer size = 10;

    /**
     * 查询时间段
     */
    private Date startTime;
    private Date endTime;

    /**
     * 时间类型：1提现时间，2出款时间，3风控审核时间，4分单时间
     */
    private Integer dateType = 1;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户类型
     */
    private Integer userType = -1;
    /**
     * 订单号
     */
    private String orderId;


    /**
     * 订单状态 - 多选
     */
    private List<Integer> withdrawStatus;


    /**
     * 出款方式：-1全部、0:人工出款、1:三方自动出款、2:三方手动出款
     */
    private Integer withdrawType;

    /**
     * 出款商户id
     */
    private String merchantId;
    /**
     * 出款商户
     */
    private String merchant;


    /**
     * 客户端类型(支持多选)：0PC，1H5，2安卓，3IOS，4PAD
     */
    private List<Integer> clientTypeList;

    /**
     * 手机号
     */
    private String telephone;

    /**
     * 用户id
     */
    private List<Integer> userIdList;


    /**
     * 排序字段：-1.提现时间(创建时间) 0.提现金额 1.手续费 2.出款时间
     */
    private Integer sortType;

    private String reallyName;

    /**
     * 排序
     */
    private String sortStr;
    // 排序拼接
    private String orderByClause;

    private Integer userId;

    /**
     * 仅为导出接口使用
     */
    private Integer exportId;

    /**
     * 根据用户名查询 0=非必填、1=必填
     */
    private Integer needName = 0;

    /**
     * 提现金额最小值
     */
    private BigDecimal minAmount;

    /**
     * 提现金额最大值
     */
    private BigDecimal maxAmount;


    /**
     * 提现申请类型  (1-普通提现、2-快速提现、3-代理提现 4-极速转卡）
     */
    private String aisleType;

    /**
     * 币种
     */
    private String coinCode;
}

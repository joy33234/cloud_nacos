package com.seektop.fund.vo;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Set;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WithdrawExceptionQueryDto extends ManageParamBase implements Serializable {

    private static final long serialVersionUID = 5953873685382619269L;

    /**
     * 时间类型：1提现时间，2出款时间，3风控审核时间
     */
    private Integer dateType = -1;

    private Date startTime; // 开始时间
    private Date endTime; // 截止时间

    private BigDecimal minAmount; // 最小提现金额
    private BigDecimal maxAmount; // 最大提现金额

    private String username; // 账户名
    private Integer userType = 0; // 用户类型：-1全部，0玩家，1代理
    private Set<Integer> userTypes; // 用户类型：-1全部，0新会员，1代理，2老会员
    private Integer clientType = -1; // 客户端类型：-1全部，0PC，1H5，2安卓，3IOS，4PAD
    private Integer status = -1; // 风险状态：-1全部，-4 审核搁置, -3风险待审核，-2风险审核拒绝，0风险审核通过

    /**
     * 风险类型：-1全部，0正常提现，1首提金额过大，2单笔金额过大，3当日金额过大，4频繁提现，5利润异常，6 7日累积金额过大，7提现IP冲突，8提现设备冲突
     */
    private List<Integer> riskType;

    private Integer searchType = -1; // 搜索类型：1提现单号，2账号名，3处理人,4姓名，5手机号，6出款商户号
    private String keywords; // 搜索关键词
    private Integer aisleType; // 提现申请类型（1-普通提现、2-快速提现、3-代理提现）


    private List<Integer> userLevel; // 用户层级
    private String nameBatch; // 用户名批量查询

    private Integer page = 1; // 页码
    private Integer size = 10; // 单页记录条数

    private List<Integer> userIds; // 用户userIds

    private Integer systemId; // 子系统id

    /**
     * 根据提现时间排序  descend:倒序  ascend:顺序
     */
    private String sortStr = "ascend";

    /**
     * 当前时间
     */
    private Date queryTime = new Date();

    /**
     * 币种
     */
    private String coinCode = "-1";
}

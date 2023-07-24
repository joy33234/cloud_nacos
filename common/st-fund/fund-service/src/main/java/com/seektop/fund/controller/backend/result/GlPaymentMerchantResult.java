package com.seektop.fund.controller.backend.result;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * @author darren
 * @create 2019-03-30
 */
@Setter
@Getter
@NoArgsConstructor
@ToString
public class GlPaymentMerchantResult implements Serializable {

    private static final long serialVersionUID = 975953608325396824L;
    /**
     * 三方商户应用ID
     */
    private Integer appId;

    /**
     * 三方商户ID
     */
    private Integer merchantId;

    /**
     * 三方商户名称
     */
    private String merchantName;

    /**
     * 三方商户号
     */
    private String merchantCode;

    /**
     * 通道名称
     */
    private String aisleName;

    /**
     * 三方商户渠道ID
     */
    private Integer channelId;

    /**
     * 三方商户渠道名称
     */
    private String channelName;

    /**
     * 充值手续费
     */
    private BigDecimal fee;

    /**
     * 手续费最高金额
     */
    private BigDecimal feeLimit;

    /**
     * 最低金额
     */
    private BigDecimal minAmount;

    /**
     * 最高金额
     */
    private BigDecimal maxAmount;

    /**
     * 支持的银行列表
     */
    private List<GlPaymentBankResult> bankList;

    /**
     * 是否是内部支付
     */
    private Boolean innerPay;

    /**
     * 推荐状态（0-已推荐、1-未推荐）
     */
    private Integer recommendStatus;

    /**
     * 置顶状态（0-已置顶、1-未置顶）
     */
    private Integer topStatus;

    /**
     * 置顶时间
     */
    private Date topDate;

    /**
     * 快捷支付卡号
     */
    private List<String> cardList;

    /**
     * 快捷支付是否需要卡号
     */
    private boolean needCardNo;

    /**
     * 转账姓名
     */
    private List<String> nameList;

    /**
     * 银行卡转账-是否需要姓名
     */
    private boolean needName;

    /**
     * 成功率（万分之）
     */
    private Integer successRate;

    /**
     * 剩余收款额度
     */
    private Long leftAmount;

    /**
     * 快捷金额列表
     */
    private List<Integer> quickAmount;

    /**
     * 推荐金额列表
     */
    private List<Integer> recommendAmount;


    /**
     * 虚拟货币新增字段-区块协议
     */
    private Map<String, String> protocolMap;

    /**
     * 虚拟货币新增字段-兑换汇率
     */
    private BigDecimal rate;

    /**
     * 支付币种：RMB、USDT
     */
    private String currency = "RMB";

    /**
     * 通道最大处理量数量 0:不限制
     */
    private Integer maxCount;

    /**
     * 当前商户应用达到最大处理量
     */
    private Boolean isFull;

    /**
     * 单位时间（轮询）处理量
     */
    private Integer cycleCount;

    /**
     * 轮询优化级
     */
    private Integer cyclePriority;

    /**
     * 停止收款限额
     */
    private BigDecimal limitAmount = new BigDecimal(99999);

    /**
     * 单位轮询内实际进单量
     */
    private Integer actualOrder = 0;

    /**
     * 付款人姓名类型：-1全部 1普通汉族姓名 2少数民族姓名 3英文名
     */
    private List<String> nameType;

    /**
     * VIP等级
     */
    private List<String> vipLevel;

    /**
     * 币种
     */
    private String coin;


}

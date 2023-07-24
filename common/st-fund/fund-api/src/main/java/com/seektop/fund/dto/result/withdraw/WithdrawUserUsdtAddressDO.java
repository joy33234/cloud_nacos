package com.seektop.fund.dto.result.withdraw;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
public class WithdrawUserUsdtAddressDO implements Serializable {


    private static final long serialVersionUID = -6782945866409595587L;
    /**
     * 用户ID
     */
    private Integer id;

    /**
     * 用户ID
     */
    private Integer userId;


    /**
     * 用户账号
     */
    private String userName;

    /**
     * 用户虚拟地址别名
     */
    private String nickName;

    /**
     * USDT协议(OMNI、ERC20、TRC20等)
     */
    private String protocol;

    /**
     * 收款地址
     */
    private String address;

    /**
     * 有效状态(0-有效、1-删除) default = 0
     */
    private Integer status;

    /**
     * 创建时间
     */
    private Date createDate;

    /**
     * 最后修改时间
     */
    private Date updateDate;


}
package com.seektop.fund.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import javax.persistence.Column;
import javax.persistence.Id;
import javax.persistence.Table;
import java.io.Serializable;
import java.util.Date;

@Setter
@Getter
@NoArgsConstructor
@ToString
@Table(name = "gl_withdraw_user_usdt_address")
public class GlWithdrawUserUsdtAddress implements Serializable {

    @Id
    private Integer id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;


    /**
     * 用户账号
     */
    @Column(name = "user_name")
    private String userName;

    /**
     * 币种编码
     */
    @Column(name = "coin")
    private String coin;

    /**
     * 用户虚拟地址别名
     */
    @Column(name = "nick_name")
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
     * 有效状态(0-有效、1-删除 2删除中) default = 0
     */
    private Integer status;

    /**
     * 是否默认选中
     * 0不是
     * 1是
     */
    @Column(name = "selected")
    private Integer selected;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 最后修改时间
     */
    @Column(name = "update_date")
    private Date updateDate;

}
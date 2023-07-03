package com.seektop.fund.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import javax.persistence.*;
import java.io.Serializable;
import java.util.Date;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "gl_user_bankcard_name")
public class UserBankcardName implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 用户ID
     */
    @Column(name = "user_id")
    private Integer userId;

    /**
     * 币种
     */
    @Column(name = "coin")
    private String coin;

    /**
     * 真实姓名
     */
    @Column(name = "name")
    private String name;

    /**
     * 绑定时间
     */
    @Column(name = "bind_date")
    private Date bindDate;

}
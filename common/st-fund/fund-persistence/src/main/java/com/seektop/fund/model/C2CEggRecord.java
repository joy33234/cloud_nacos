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
@Table(name = "gl_c2c_egg_record")
public class C2CEggRecord implements Serializable {

    @Id
    @Column(name = "id")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    /**
     * 类型
     *
     * @see com.seektop.enumerate.fund.C2CEggTypeEnum
     */
    @Column(name = "type")
    private Short type;

    /**
     * 启动时间
     */
    @Column(name = "start_date")
    private Date startDate;

    /**
     * 结束时间
     */
    @Column(name = "end_date")
    private Date endDate;

    /**
     * 时长（单位：分钟）
     */
    @Column(name = "duration")
    private Integer duration;

    /**
     * 状态
     *
     * @see com.seektop.enumerate.fund.C2CEggStatusEnum
     */
    @Column(name = "status")
    private Short status;

    /**
     * 创建时间
     */
    @Column(name = "create_date")
    private Date createDate;

    /**
     * 创建人
     */
    @Column(name = "creator")
    private String creator;

    /**
     * 更新时间
     */
    @Column(name = "update_date")
    private Date updateDate;

    /**
     * 更新人
     */
    @Column(name = "updater")
    private String updater;

    /**
     * 活动配置内容
     */
    @Column(name = "config")
    private String config;

}
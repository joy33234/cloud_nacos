package com.seektop.common.elasticsearch.dto;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

@Getter
@EqualsAndHashCode
@NoArgsConstructor
@AllArgsConstructor
public class BaseQueryDto implements Serializable {

    /**
     * 默认的开始时间
     */
    private Long stime;

    /**
     * 默认的结束时间,当前的时间
     */
    private Long etime;

    /**
     * 注册时间
     */
    private Long regStartTime;

    /**
     * 注册时间
     */
    private Long regEndTime;
    /**
     * 导出csv
     */
    private Boolean export = false;
    /**
     * 币别
     */
    private String coinCode;

    /**
     * 默认项目为
     */
    @Deprecated
    private String project = "betball";

    /**
     * 0:真实账号
     * 1:虚拟账号
     *
     * 说明：-1时查询全部
     */
    private int isFake = -1;

    public BaseQueryDto(Long stime, Long etime, String project, Integer isFake) {
        setStime(stime);
        setEtime(etime);
        setProject(project);
        setIsFake(isFake);
    }


    public Integer getIntegerIsFake() {
        return isFake > -1 ? isFake : null;
    }

    public void setStime(Long stime) {
        if (stime != null && stime.toString().length() == 10) {
            stime *= 1000;
        }
        this.stime = stime;
    }

    public void setEtime(Long etime) {
        if (etime != null && etime.toString().length() == 10) {
            etime = etime * 1000 + 999;
        }
        this.etime = etime;
    }
    public void setRegStartTime(Long stime) {
        if (stime != null && stime.toString().length() == 10) {
            stime *= 1000;
        }
        this.regStartTime = stime;
    }

    public void setRegEndTime(Long etime) {
        if (etime != null && etime.toString().length() == 10) {
            etime = etime * 1000 + 999;
        }
        this.regEndTime = etime;
    }

    public void setCoinCode(String coinCode) {
        this.coinCode = coinCode;
    }

    public void setProject(String project) {
        this.project = project;
    }

    public void setIsFake(int isFake) {
        this.isFake = isFake > 1 ? -1 : isFake;
    }
    public void setExport(boolean export) {
        this.export = export;
    }

    public BaseQueryDto putStartTimeIfNull(Long startTime) {
        if (this.stime == null) {
            this.stime = startTime;
        }
        return this;
    }

    public BaseQueryDto putEndTimeIfNull(Long endTime) {
        if (this.etime == null) {
            this.etime = endTime;
        }
        return this;
    }

    public Long getStime() {
        return stime;
    }

    public Long getStime(long defaultVal) {
        return Optional.ofNullable(stime).orElse(defaultVal);
    }

    public LocalDateTime LocaDateStime() {
        return LocalDateTime.ofInstant(Instant.ofEpochMilli(getStime(0)), ZoneId.systemDefault());
    }

    public LocalDateTime LocaDateEtime() {
        return etime == null ? LocalDateTime.now()
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(getEtime()), ZoneId.systemDefault());
    }

    public Long getEtime() {
        return etime;
    }

    public Long getEtime(long defaultVal) {
        return Optional.ofNullable(etime).orElse(defaultVal);
    }

    public Long getRegStartTime(long defaultVal) {
        return Optional.ofNullable(regStartTime).orElse(defaultVal);
    }

    public Long getRegEndTime(long defaultVal) {
        return Optional.ofNullable(regEndTime).orElse(defaultVal);
    }
}
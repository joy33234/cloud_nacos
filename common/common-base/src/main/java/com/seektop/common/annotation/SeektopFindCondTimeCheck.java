package com.seektop.common.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * @Desc 被注解标注的api拥有时间区间辅助验证的功能(默认不超过100天)，
 * @param #{var}+startTime+#{var} ,  #{var}+endTime+#{var}.不区分大小写，
 * 单个api不能支持含有多个时间区间参数（目前仅支持一对）
 * */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface SeektopFindCondTimeCheck {

}
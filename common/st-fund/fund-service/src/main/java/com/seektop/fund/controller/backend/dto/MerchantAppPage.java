package com.seektop.fund.controller.backend.dto;

import com.github.pagehelper.PageInfo;
import lombok.Data;
import lombok.ToString;
import org.apache.poi.ss.formula.functions.T;

import java.util.List;


@Data
@ToString
public class MerchantAppPage<P> extends PageInfo {

    /**
     * 轮询时间-设置总量
     */
    private Integer totalOrder = 0;

    /**
     * 轮询时间-进单总量
     */
    private Integer actualOrder = 0;


    /**
     * 包装Page对象
     *
     * @param list
     */
    public MerchantAppPage(List<T> list) {
        super(list);
    }
}

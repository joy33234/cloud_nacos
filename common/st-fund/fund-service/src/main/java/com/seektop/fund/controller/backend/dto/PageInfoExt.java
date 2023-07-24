package com.seektop.fund.controller.backend.dto;

import com.github.pagehelper.PageInfo;
import lombok.Data;
import lombok.ToString;
import org.apache.poi.ss.formula.functions.T;

import java.util.List;
import java.util.Map;


@Data
@ToString
public class PageInfoExt<T> extends PageInfo {

    /**
     * 业务扩充属性
     */
    private Map<String,Object> extData;


    public PageInfoExt(List<T> list) {
        super(list, 8);
    }
    public PageInfoExt() {
        super();
    }
}

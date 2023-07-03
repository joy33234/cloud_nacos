package com.seektop.fund.controller.backend.dto;

import com.seektop.common.function.NormalSupplier;
import lombok.Data;

import java.io.Serializable;

@Data
public class ExportFileDto implements Serializable {

    private Integer userId;  // admin的userId
    private String fileName; // 导出的文件名

    private String headers;  // StringBuffer类型数据时的头标题
    /**
     * 数据提供者
     * 提供StringBuffer或List类型数据
     */
    private NormalSupplier<Object> supplier; // 数据提供者
}

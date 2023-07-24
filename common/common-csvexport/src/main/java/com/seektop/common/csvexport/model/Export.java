package com.seektop.common.csvexport.model;


import com.seektop.common.csvexport.enums.DatasourceTypeEnum;
import com.seektop.common.csvexport.function.SourceFunction;
import com.seektop.common.csvexport.helper.ExportHelper;
import com.seektop.common.csvexport.utils.CsvExportUtils;
import com.seektop.common.utils.UserIdUtils;
import com.seektop.exception.GlobalException;
import lombok.Builder;
import lombok.Data;
import lombok.ToString;
import lombok.experimental.Tolerate;
import org.springframework.util.ObjectUtils;

import java.security.InvalidParameterException;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;


@Builder
@Data
@ToString
public class Export {
    private String header;
    private Integer pageSize;
    private Integer total;
    private String title;
    private String fileType;
    private String lockKey;
    private Integer userId;
    private List<FieldMap> fieldMaps;
    private FieldMapBuilder fieldMapBuilder;
    private Integer interval;
    private DatasourceTypeEnum datasourceTypeEnum;
    //设置指定脱敏字段，不设置将走默认的脱敏行为[只展示姓名]
    private List<Integer> encrypts;
    //是否根据权限脱敏 //默认false
    private Boolean encrypt = false;


    @Tolerate
    public Export(){}
    /**
     * @param sourceFunction
     */
    public <T> void doExport(SourceFunction.PageInfoQuery<T> sourceFunction) throws GlobalException {
        try {
            validate(this);
            validateNotNullAnd(this.getFieldMaps(), "fieldsMaps",()-> ObjectUtils.isEmpty(fieldMapBuilder));
            ExportHelper
                    .csvExportComponent()
                    .export(this, sourceFunction);
        } finally {
            CsvExportUtils.release();
        }
    }

    public <T> void doExport(SourceFunction.ListQuery<T> sourceFunction) throws GlobalException {
        try {
            validate(this);
            validateNotNullAnd(this.getFieldMaps(), "fieldsMaps",()-> ObjectUtils.isEmpty(fieldMapBuilder));
            ExportHelper
                    .csvExportComponent()
                    .export(this, sourceFunction);
        } finally {
            CsvExportUtils.release();
        }
    }
    /**
     * @param sourceFunction
     * @param parse
     * @param <T>
     */
    public <T> void doExport(SourceFunction.PageInfoQuery<T> sourceFunction, Function<T, StringBuffer> parse) throws GlobalException {
        try {
            validate(this);
            ExportHelper
                    .csvExportComponent()
                    .export(this, sourceFunction, parse);
        } finally {
            CsvExportUtils.release();
        }
    }
    public <T> void doExport(SourceFunction.ListQuery<T> sourceFunction, Function<T, StringBuffer> parse) throws GlobalException {
        try {
            validate(this);
            validateNotNull(this.getHeader(),"header");
            ExportHelper
                    .csvExportComponent()
                    .export(this, sourceFunction, parse);
        } finally {
            CsvExportUtils.release();
        }
    }
    /**
     * validate
     */
    public void validate(Export export) throws GlobalException {
        //校验标题
        validateNotNull(export.getTitle(), "标题[title]");
        validateNotNullAnd(export.getHeader(), "表头[header]",()-> ObjectUtils.isEmpty(fieldMaps) && ObjectUtils.isEmpty(fieldMapBuilder));
        validateNotNullAnd(export.getUserId(), "userId[userId]",
                () -> {
                    // service 层使用的时候，如果没有使用filter
                    export.setUserId(UserIdUtils.getUserId());
                    return ObjectUtils.isEmpty(UserIdUtils.getUserId());
                }
        );
        //校验key
        if (ObjectUtils.isEmpty(export.getFileType()) && ObjectUtils.isEmpty(export.getLockKey())) {
            throw new InvalidParameterException(String.format("%s:不能为空", "locKey和fileType"));
        }
    }

    private void validateNotNull(Object value, String title) {
        if (ObjectUtils.isEmpty(value)) {
            throw new InvalidParameterException(String.format("%s:不能为空", title));
        }
    }

    private void validateNotNullAnd(Object value, String title, Supplier<Boolean> and) {
        if (ObjectUtils.isEmpty(value) && and.get()) {
            throw new InvalidParameterException(String.format("%s:不能为空", title));
        }
    }

    private void validateNotNullOr(Object value, String title, Supplier<Boolean> or) {
        if (ObjectUtils.isEmpty(value) || or.get()) {
            throw new InvalidParameterException(String.format("%s:不能为空", title));
        }
    }
}

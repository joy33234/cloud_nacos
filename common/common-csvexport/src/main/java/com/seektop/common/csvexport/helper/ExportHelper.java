package com.seektop.common.csvexport.helper;


import com.seektop.common.csvexport.CsvExportComponent;
import com.seektop.common.csvexport.model.Export;
import com.seektop.common.csvexport.utils.CsvExportUtils;
import org.springframework.beans.factory.annotation.Autowired;

public class ExportHelper {

    private static CsvExportComponent csvExportComponent;


    private static ExportHelper exportHelper;

    @Autowired
    public  void setCsvExportComponent(CsvExportComponent csvExportComponent) {
        ExportHelper.csvExportComponent = csvExportComponent;
    }

    @Autowired
    public  void setExportHelper(ExportHelper exportHelper) {
        ExportHelper.exportHelper = exportHelper;
    }

    /**
     * 去除ThreadLocal传参
     * @param pageSize
     * @param total
     * @return
     */
    public static  Export.ExportBuilder startPageExport(Integer pageSize, Integer total) {
        Export.ExportBuilder builder = Export.builder();
        CsvExportUtils.setTotal(total);
        CsvExportUtils.setPageSize(pageSize);
        builder.total(total)
                .pageSize(pageSize);
        return builder;
    }
    public static ExportHelper exportHelper(){
        return exportHelper;
    }
    public static CsvExportComponent csvExportComponent(){
        return csvExportComponent;
    }
}

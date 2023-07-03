package com.seektop.common.csvexport.utils;



import com.seektop.common.csvexport.model.Export;
import com.seektop.exception.GlobalException;

import java.util.function.Supplier;

public class CsvExportUtils {
    private static ThreadLocal<Export> LOCAL_EXPORT = ThreadLocal.withInitial(Export::new);

    public static Export setPageSize(Integer size){
        Export export = LOCAL_EXPORT.get();
        export.setPageSize(size);
        return export;
    }
    public static Integer getPageSize(){
        return LOCAL_EXPORT.get().getPageSize();
    }
    public static Export setTotal(Integer total){
        Export export = LOCAL_EXPORT.get();
        export.setTotal(total);
        return export;
    }
    public static Integer getTotal(){
        return LOCAL_EXPORT.get().getPageSize();
    }
    public static void setExport(Export export){
        LOCAL_EXPORT.set(export);
    }
    public static Export getExport(){
        return LOCAL_EXPORT.get();
    }
    public static void release(){
        LOCAL_EXPORT.remove();
    }

    /**
     * 统一异常处理
     * @param supplier
     * @throws GlobalException
     */
    public static void executor(Supplier supplier) throws GlobalException {
        try {
            CsvExportUtils.release();
            supplier.get();
        }catch (Exception e){
            throw new GlobalException(e);
        }finally {
            CsvExportUtils.release();
        }
    }
}

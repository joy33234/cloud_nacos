package com.seektop.common.csvexport.service;

import com.seektop.common.csvexport.dto.GlExportDto;
import com.seektop.common.csvexport.model.Export;
import com.seektop.exception.GlobalException;

public interface CsvExportService {

    /**
     * 开始导出
     * @param userId
     * @param fileName
     * @return
     */
     Integer startExport(Integer userId, String fileName);

    /**
     * 导出成功
     * @param id
     */
     void success(Integer id, String mongoFileId, String path);

    /**
     * 导出失败
     * @param id
     */
    void fail(Integer id);

    /**
     * lock
     */
    void lock(String lock);

    void releaseLock(String lock);

    /**
     *
     * @param export
     * @return lockKey
     * @throws GlobalException
     */
    String checkAndGetLock(Export export) throws GlobalException;
    /**
     * 持久化
     */
    GlExportDto persistent(byte[] data) throws GlobalException;

    /**
     * defaultPermission
     * 默认全部脱敏只展示姓名
     */
    default void doExport(Export export){
    }
    default void release(Export export){

    }

}

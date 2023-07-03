package com.seektop.fund.handler;

import com.seektop.common.encrypt.EncryptHelper;
import com.seektop.common.function.NormalSupplier;
import com.seektop.common.mongo.file.MongoFileService;
import com.seektop.fund.controller.backend.dto.ExportFileDto;
import com.seektop.system.dto.param.GlExportCompleteDO;
import com.seektop.system.service.GlExportService;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.apache.dubbo.config.annotation.Reference;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.util.List;

@Slf4j
@Component
public class ExportFileHandler {

    @Reference(retries = 2, timeout = 3000)
    private GlExportService exportService;
    @Resource
    private MongoFileService fileService;

    /**
     * 导出文件
     * @param exportFileDto
     */
    public void exportFile(ExportFileDto exportFileDto) {
        Integer adminUserId = exportFileDto.getUserId();
        String exportName = exportFileDto.getFileName();
        // 开始下载
        Integer exportId = exportService.startExport(adminUserId, exportName);
        GlExportCompleteDO completeDO = new GlExportCompleteDO();
        completeDO.setIsBackend(true); // 后端文件
        completeDO.setCompress(false);
        completeDO.setExportId(exportId);

        String headers = exportFileDto.getHeaders();
        NormalSupplier<Object> supplier = exportFileDto.getSupplier();
        StringBuffer sbData = null;
        List<?> records = null;
        try {
            Object data = EncryptHelper.startEncrypt(supplier, adminUserId);
            if(data instanceof StringBuffer) {
                sbData = (StringBuffer) data;
            }
            else if(data instanceof List) {
                records = (List<?>) data;
            }
            // 上数据保存到MongoDB并返回文件ID
            String fileId = null;
            if(StringUtils.isNotBlank(headers) && !ObjectUtils.isEmpty(sbData)) {
                fileId = fileService.save(sbData, headers, false, exportName);
            }
            else if(!CollectionUtils.isEmpty(records)) {
                fileId = fileService.save(records, false, exportName);
            }
            if(StringUtils.isNotBlank(fileId)) {
                // 更新导出结果
                completeDO.setMongoFileId(fileId);
                completeDO.setStatus(1);
            }
            else {
                completeDO.setStatus(2);
                log.info("headers/supplier参数为空或supplier参数为空");
            }
            exportService.completeExport(completeDO);
        }
        catch (Exception e) {
            log.error("export data error", e);
            completeDO.setStatus(2);
            exportService.completeExport(completeDO);
        }
    }
}

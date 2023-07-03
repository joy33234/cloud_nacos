package com.seektop.fund.configuration;

import com.seektop.common.csvexport.annotation.EnAbleExportUtils;
import com.seektop.common.csvexport.dto.GlExportDto;
import com.seektop.common.csvexport.model.Export;
import com.seektop.common.csvexport.service.CsvExportService;
import com.seektop.common.mongo.file.MongoFileDO;
import com.seektop.common.mongo.file.MongoFileService;
import com.seektop.common.redis.RedisTools;
import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.constant.ProjectConstant;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import com.seektop.system.dto.param.GlExportCompleteDO;
import com.seektop.system.service.GlExportService;
import org.apache.dubbo.config.annotation.Reference;
import org.apache.dubbo.config.spring.context.annotation.DubboComponentScan;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;
import org.springframework.util.StringUtils;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@DubboComponentScan
@EnAbleExportUtils
public class CsvExportConfiguration {

    @Reference(timeout =5000, retries = 3)
    private GlExportService exportService;

    @Resource
    private MongoFileService mongoFileService;

    @Bean
    public CsvExportService csvExportService() {
        return new CsvExportService() {

            @Override
            public Integer startExport(Integer userId, String fileName) {
                return exportService.startExport(userId, fileName);
            }

            @Override
            public void success(Integer id, String mongoFileId, String path) {
                GlExportCompleteDO completeDO = new GlExportCompleteDO();
                completeDO.setMongoFileId(mongoFileId);
                completeDO.setStatus(1);
                completeDO.setIsBackend(true);
                completeDO.setCompress(false);
                completeDO.setExportId(id);
                exportService.completeExport(completeDO);
            }

            @Override
            public void fail(Integer id) {
                GlExportCompleteDO completeDO = new GlExportCompleteDO();
                completeDO.setMongoFileId("");
                // 失败
                completeDO.setStatus(2);
                completeDO.setIsBackend(true);
                completeDO.setCompress(false);
                completeDO.setExportId(id);
                exportService.completeExport(completeDO);
            }

            @Override
            public void lock(String lock) {
                RedisTools.valueOperations().set(lock,"1",5, TimeUnit.MINUTES);
            }

            @Override
            public void releaseLock(String lock) {
                RedisTools.template().delete(lock);
            }

            @Override
            public String checkAndGetLock(Export export) throws GlobalException {
                // 可以进行导出的判断
                // 兼容之前的写法，设置了fileType 默认就是 fileType_userId 否则直接取 lockKey
                String lockKey = !ObjectUtils.isEmpty(export.getFileType())?export.getFileType() + "_" + export.getUserId():export.getLockKey();
                String value = RedisTools.stringOperations().get(lockKey);
                if(!StringUtils.isEmpty(value)){
                    throw new GlobalException(ResultCode.FAIL.getCode(), "五分钟只能导出一次", "五分钟只能导出一次", null);
                }
                return lockKey;
            }

            @Override
            public GlExportDto persistent(byte[] data) throws GlobalException {
                MongoFileDO csvFile = new MongoFileDO(data, UUID.randomUUID().toString() + ".csv", "application/csv");
                String returnFile = mongoFileService.save(csvFile);
                String fileId = String.valueOf(returnFile);
                String detailPath = "/api/gl/file/files/" + fileId + ".csv";
                GlExportDto exportDto = new GlExportDto();
                exportDto.setFileId(fileId);
                exportDto.setFilePath(detailPath);
                return exportDto;
            }

            /**
             *如果只做导出，可以不重写该方法
             * @param export
             */
            @Override
            public void doExport(Export export) {
                // 导出去掉姓名脱敏
                if(!Optional.ofNullable(export.getEncrypt()).orElse(false)){
                    //表示不需要根据权限脱敏
                    if(CollectionUtils.isEmpty(export.getEncrypts())){
                        // 没有设置脱敏字段 默认全部脱敏只展示姓名
                        JobEncryptPermissionUtils.setJobEncryptPermissions(Arrays.asList(ProjectConstant.EncryptPermission.NAME));
                    }else{
                        // 根据设置的字段脱敏
                        JobEncryptPermissionUtils.setJobEncryptPermissions(export.getEncrypts());
                    }
                }
            }
            @Override
            public void release(Export export) {
                JobEncryptPermissionUtils.release();
            }
        };
    }

}
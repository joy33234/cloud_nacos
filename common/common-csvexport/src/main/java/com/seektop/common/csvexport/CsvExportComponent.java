package com.seektop.common.csvexport;


import com.github.pagehelper.PageInfo;
import com.seektop.common.csvexport.dto.GlExportDto;
import com.seektop.common.csvexport.enums.DatasourceTypeEnum;
import com.seektop.common.csvexport.function.SourceFunction;
import com.seektop.common.csvexport.model.Export;
import com.seektop.common.csvexport.model.FieldMap;
import com.seektop.common.csvexport.service.CsvExportService;
import com.seektop.common.csvexport.utils.CsvExportUtils;
import com.seektop.common.utils.UserIdUtils;
import com.seektop.enumerate.ResultCode;
import com.seektop.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.task.TaskExecutor;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
public class CsvExportComponent {

    @Resource
    private CsvExportService csvExportService;
    @Resource
    private TaskExecutor taskExecutor;

    public void export(Export export, SourceFunction sourceFunction) throws GlobalException {
        if(ObjectUtils.isEmpty(export.getFieldMapBuilder())) {
            getHeader(export);
            doExport(export, sourceFunction, item -> sourceFunction.commonParse(export.getFieldMaps(), item));
        }else {
            getHeader(export);
            doExport(export, sourceFunction, item -> sourceFunction.functionParse(export.getFieldMapBuilder().getFieldMaps(), item));
        }
    }

    private void getHeader(Export export) throws GlobalException {
        List<FieldMap> fieldMaps = export.getFieldMaps();
        if(CollectionUtils.isEmpty(fieldMaps)){
            // 没有设置builder 或者 builder的fieldMap为空
            if(ObjectUtils.isEmpty(export.getFieldMapBuilder()) && CollectionUtils.isEmpty(export.getFieldMapBuilder().getFieldMaps())) {
                throw new GlobalException("fieldMaps can't empty");
            }else{
                fieldMaps = export.getFieldMapBuilder().getFieldMaps();
            }
        }
        List<String> titleList = fieldMaps.stream().map(v -> v.getFieldTitle()).collect(Collectors.toList());
        String title = StringUtils.join(titleList,",");
        export.setHeader(title);
    }

    public <T> void export(Export export,SourceFunction sourceFunction,Function<T, StringBuffer> parser) throws GlobalException {
        doExport(export,sourceFunction,parser);
    }

    /**
     * 导出分页查询数据
     *
     * @param title          表格名称
     * @param header         表头数据 "aa,bb,cc"
     * @param fileType       锁名称 BLACK_STATISTIC_DOWNLOAD_LOCK
     * @param userId          用户token
     * @param sourceFunction PageInfoQuery 分页查询数据的Function （page,size）->findXXxByPage(page,size,....)
     * @param parser         解析数据格式的function 需要 item->{} 需要返回StringBuffer对象
     * @param <T>
     * @throws GlobalException
     */
    @Deprecated
    public <T> void export(String title, String header, String fileType, Integer userId,
                           SourceFunction.PageInfoQuery<T> sourceFunction, Function<T, StringBuffer> parser
    ) throws GlobalException {
        // 预查询一次，如果数据为空直接返回
        //开始执行导出操作
        Export.builder()
                .title(title)
                .header(header)
                .fileType(fileType)
                .pageSize(DatasourceTypeEnum.MYSQL.getDefaultPageSize())
                .total(DatasourceTypeEnum.MYSQL.getDefaultTotal())
                .userId(userId)
                .build()
                .doExport(sourceFunction, parser);
    }

    /**
     * 导出分页查询数据
     *
     * @param title          表格名称
     * @param header         表头数据 "aa,bb,cc"
     * @param fileType       锁名称 BLACK_STATISTIC_DOWNLOAD_LOCK
     * @param userId          用户token
     * @param sourceFunction ListQuery 分页查询数据的Function （）->findXXxList(...)
     * @param parser         解析数据格式的function 需要 item->{} 需要返回StringBuffer对象
     * @param <T>
     * @throws GlobalException
     */
    @Deprecated
    public <T> void export(String title, String header, String fileType, Integer userId,
                           SourceFunction.ListQuery<T> sourceFunction, Function<T, StringBuffer> parser
    ) throws GlobalException {
        Export.builder()
                .title(title)
                .header(header)
                .fileType(fileType)
                .pageSize(DatasourceTypeEnum.MYSQL.getDefaultPageSize())
                .total(DatasourceTypeEnum.MYSQL.getDefaultTotal())
                .userId(userId)
                .build()
                .doExport(sourceFunction,parser);
    }

    private <T> void doExport(Export export,
                              SourceFunction<T> sourceFunction, Function<T, StringBuffer> parser
    ) throws GlobalException {
        int page = 1;
        int size = 1;
        if (sourceFunction instanceof SourceFunction.PageInfoQuery) {
            PageInfo<T> pageInfo = sourceFunction.apply(page, size);
            if (ObjectUtils.isEmpty(pageInfo)|| pageInfo.getTotal() == 0) {
                throw new GlobalException(ResultCode.FAIL.getCode(), "数据为空", "数据为空", null);
            }
        }
        String lockKey = csvExportService.checkAndGetLock(export);
        Integer id = csvExportService.startExport(export.getUserId(), export.getTitle());
        // 异步操作
        taskExecutor.execute(() -> {
            try {
                CsvExportUtils.setExport(export);
                // 导出需要进行脱敏权限校验
                UserIdUtils.setUserId(export.getUserId());
                // 自定义操作
                csvExportService.doExport(export);
                // 获取需要写入的数据 字节数组
                log.info("导出校验通过，开始执行导出 {}",export);
                byte[] byteArrays = getBytes(export.getHeader(), lockKey, sourceFunction, parser);
                // 存储文件到mongoDb中
                GlExportDto exportDto = saveFile(byteArrays);
                if (null != exportDto) {
                    csvExportService.success(id,  exportDto.getFileId(), exportDto.getFilePath());
                } else {
                    csvExportService.fail(id);
                    csvExportService.releaseLock(lockKey);
                }
            } catch (Exception e) {
                try {
                    csvExportService.fail(id);
                } catch (Exception ex) {
                    ex.printStackTrace();
                    csvExportService.releaseLock(lockKey);
                }
                log.error("export data error", e);
            }finally {
                UserIdUtils.release();
                CsvExportUtils.release();
                csvExportService.release(export);
            }
        });
    }

    private <T> byte[] getBytes(String header, String lockKey, SourceFunction<T> source, Function<T, StringBuffer> parser) throws GlobalException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            outputStream.write(new byte[]{(byte) 0xEF, (byte) 0xBB, (byte) 0xBF});
            // 写表头
            outputStream.write(header.getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.flush();
            //读内容 写入到输出流
            //先做导出限定，防止数据没有读完的时候，再次点击下载
            csvExportService.lock(lockKey);
            source.readAndWrite(outputStream, parser);
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (IOException e) {
            log.error("IOException:{}", e.getMessage());
            csvExportService.releaseLock(lockKey);
            throw new GlobalException(e);
        }
    }

    private GlExportDto saveFile(byte[] data) throws GlobalException {
        // todo 对文件进行压缩
        return csvExportService.persistent(data);
    }
}

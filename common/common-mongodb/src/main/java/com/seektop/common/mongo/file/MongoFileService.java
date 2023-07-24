package com.seektop.common.mongo.file;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSDownloadStream;
import com.mongodb.client.gridfs.model.GridFSFile;
import com.seektop.common.mongo.MongoFileConstant;
import com.seektop.common.mongo.utils.MongoFileIdUtils;
import com.seektop.common.utils.ExportUtils;
import com.seektop.constant.ContentType;
import com.seektop.constant.ProjectConstant;
import com.seektop.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.gridfs.GridFsResource;
import org.springframework.data.mongodb.gridfs.GridFsTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.util.ObjectUtils;

import javax.annotation.Resource;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Slf4j
@Service
public class MongoFileService {

    @Autowired
    private GridFsTemplate gridFsTemplate;
    @Resource
    private GridFSBucket gridFSBucket;

    @Resource
    private MongoTemplate mongoTemplate;


    /**
     * 获取文件路径
     *
     * @param fileId
     * @param isBackend
     * @return
     */
    public String getFilePath(final String fileId, final boolean isBackend) {
        if (isBackend) {
            return "/api/manage/system/file/get/" + fileId;
        } else {
            return "/api/forehead/system/file/get/" + fileId;
        }
    }

    /**
     * 获取下载文件路径
     *
     * @param fileId
     * @param isBackend
     * @return
     */
    public String getDownloadFilePath(final String fileId, final boolean isBackend) {
        if (isBackend) {
            return "/api/manage/system/file/download/" + fileId;
        } else {
            return "/api/forehead/system/file/download/" + fileId;
        }
    }

    /**
     * 用于mysql导出
     *
     * @param exportData
     * @param exportName
     * @return
     * @throws GlobalException
     */
    public String saveMapData(List<Map<String, Object>> exportData, String exportName) throws GlobalException {
        if (CollectionUtils.isEmpty(exportData)) {
            return null;
        }
        byte[] data = ExportUtils.getInstance().getMapExportData(exportData);
        if (data != null) {
            String fileName = exportName + ".csv";
            String contentType = ContentType.CSV;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                ObjectId objectId = store(inputStream, fileName, contentType);
                if (ObjectUtils.isEmpty(objectId)) {
                    return null;
                }
                return objectId.toString();
            } catch (Exception e) {
                log.info("MongoFileService.save() error", e);
                throw new GlobalException("保存文件到MongoDB出现错误", e);
            }
        } else {
            throw new GlobalException("导出生成的数据为空");
        }
    }

    /**
     * 导出数据到MongoDB【报表数据】
     *
     * @param exportData
     * @param isCompress
     * @param exportName
     * @param <T>
     * @return
     * @throws GlobalException
     */
    public <T> String save(List<T> exportData, boolean isCompress, String exportName) throws GlobalException {
        if (CollectionUtils.isEmpty(exportData)) {
            return null;
        }
        byte[] data;
        if (isCompress) {
            data = ExportUtils.getInstance().getExportCompressData(exportData);
        } else {
            data = ExportUtils.getInstance().getExportData(exportData);
        }
        if (data != null) {
            String fileName = exportName + (isCompress ? ".zip" : ".csv");
            String contentType = isCompress ? ContentType.ZIP : ContentType.CSV;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                ObjectId objectId = store(inputStream, fileName, contentType);
                if (ObjectUtils.isEmpty(objectId)) {
                    return null;
                }
                return objectId.toString();
            } catch (Exception e) {
                log.info("MongoFileService.save() error", e);
                throw new GlobalException("保存文件到MongoDB出现错误", e);
            }
        } else {
            throw new GlobalException("导出生成的数据为空");
        }
    }

    /**
     * 导出数据到MongoDB【报表数据】
     *
     * @param <T>
     * @param exportData
     * @param title
     * @param isCompress
     * @param exportName
     * @return
     * @throws GlobalException
     */
    public <T> String save(StringBuffer exportData, String title, boolean isCompress, String exportName) throws GlobalException {
        if (ObjectUtils.isEmpty(exportData)) {
            return null;
        }
        byte[] data;
        if (isCompress) {
            data = ExportUtils.getInstance().getExportCompressData(exportData, title);
        } else {
            data = ExportUtils.getInstance().getExportData(exportData, title);
        }
        if (data != null) {
            String fileName = exportName + (isCompress ? ".zip" : ".csv");
            String contentType = isCompress ? ContentType.ZIP : ContentType.CSV;
            try (ByteArrayInputStream inputStream = new ByteArrayInputStream(data)) {
                ObjectId objectId = store(inputStream, fileName, contentType);
                if (ObjectUtils.isEmpty(objectId)) {
                    return null;
                }
                return objectId.toString();
            } catch (Exception e) {
                log.info("MongoFileService.save() error", e);
                throw new GlobalException("保存文件到MongoDB出现错误", e);
            }
        } else {
            throw new GlobalException("导出生成的数据为空");
        }
    }

    /**
     * 删除文件
     *
     * @param id
     */
    public void delete(String id) {
        if (StringUtils.isBlank(id))
            return;
        id = MongoFileIdUtils.getReallyId(id);
        gridFsTemplate.delete(Query.query(Criteria.where("_id").is(id)));
    }

    /**
     * 获取文件
     *
     * @param id
     * @return
     */
    @Deprecated
    public GridFsResource get(String id) {
        id = MongoFileIdUtils.getReallyId(id);
        GridFSFile gridFSFile = gridFsTemplate.findOne(Query.query(Criteria.where("_id").is(id)));
        GridFSDownloadStream downloadStream = gridFSBucket.openDownloadStream(gridFSFile.getObjectId());
        GridFsResource gridFsResource = new GridFsResource(gridFSFile, downloadStream);
        return gridFsResource;
    }

    /**
     * 保存文件
     * 前后台上传数据
     *
     * @param mongoFileDO
     * @return
     * @throws GlobalException
     */
    public String save(MongoFileDO mongoFileDO) throws GlobalException {
        if (ObjectUtils.isEmpty(mongoFileDO)) {
            return null;
        }
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(mongoFileDO.getData())) {
            ObjectId objectId = null;
            if (null == mongoFileDO.getProperties()) {
                objectId = store(inputStream, mongoFileDO.getFileName(), mongoFileDO.getContentType());
            } else {
                Document document = mongoFileDO.getProperties().toDocument();
                objectId = gridFsTemplate.store(inputStream, mongoFileDO.getFileName(), mongoFileDO.getContentType(), (Object) document);
            }
            if (ObjectUtils.isEmpty(objectId)) {
                log.info("当前的文件信息保存异常:{}",objectId);
                return null;
            }
            return objectId.toString();
        } catch (Exception e) {
            log.info("MongoFileService.save() error", e);
            throw new GlobalException("保存文件到MongoDB出现错误", e);
        }
    }

    /**
     * 【plist文件导出，IOS模板，代理相关的数据】
     *
     * @param zipFile
     * @return
     * @throws GlobalException
     */
    public String save(File zipFile) throws GlobalException {
        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(zipFile.getContent())) {
            ObjectId objectId = store(inputStream, zipFile.getName(), zipFile.getContentType());
            if (ObjectUtils.isEmpty(objectId)) {
                return null;
            }
            return objectId.toString();
        } catch (Exception e) {
            log.info("MongoFileService.save() error", e);
            throw new GlobalException("保存文件到MongoDB出现错误", e);
        }
    }

    public ObjectId store(InputStream content, @Nullable String filename, @Nullable String contentType) {
        Document document = MongoFileProperties
                .builder()
                .appType(ProjectConstant.APPType.SYSTEM)
                .backend(true)
                .build().toDocument();
        return gridFsTemplate.store(content, filename, contentType, (Object) document);
    }

    public long mark(Set<ObjectId> ids) {
        final Update update = new Update();
        final Query query = new Query();
        update.set("metadata.effective", true);
        update.set("metadata.effectiveTime", new Date());
        Criteria criteria = Criteria.where("_id").in(ids);
        query.addCriteria(criteria);
        long matchedCount = mongoTemplate.updateMulti(query, update, MongoFileConstant.FILE_COLLECTION).getModifiedCount();
        log.debug("本次查询更新记录数:{}", matchedCount);
        return matchedCount;
    }

}
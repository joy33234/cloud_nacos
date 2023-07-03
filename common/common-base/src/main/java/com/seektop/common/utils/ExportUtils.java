package com.seektop.common.utils;

import com.seektop.common.annotation.SeektopExport;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.compress.archivers.zip.Zip64Mode;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.apache.commons.io.FileUtils;
import org.springframework.util.ObjectUtils;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Slf4j
public class ExportUtils {

    /**
     * 获取注单导出文件的数据（压缩后的数据）
     *
     * @param exportData
     * @param <T>
     * @return
     */
    public <T> byte[] getExportCompressData(List<T> exportData) {
        byte[] orgCsvData = getExportData(exportData);
        if (orgCsvData == null) {
            return null;
        }
        java.io.File csvFile = null;
        java.io.File zipFile = null;
        try {
            // 创建临时文件存储CSV
            String csvFilePath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString() + ".csv";
            log.debug("csv临时文件{}", csvFilePath);
            csvFile = new java.io.File(csvFilePath);
            if (csvFile.exists() == false) {
                csvFile.createNewFile();
            }
            FileUtils.writeByteArrayToFile(csvFile, orgCsvData);
            // 文件压缩
            String zipFilePath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString() + ".zip";
            log.debug("zip临时文件{}", zipFilePath);
            zipFile = new java.io.File(zipFilePath);
            if (zipFile.exists() == false) {
                zipFile.createNewFile();
            }
            ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(zipFile);
            zipArchiveOutputStream.setUseZip64(Zip64Mode.AsNeeded);
            ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(csvFile, csvFile.getName());
            zipArchiveOutputStream.putArchiveEntry(zipArchiveEntry);
            zipArchiveOutputStream.write(orgCsvData, 0, orgCsvData.length);
            zipArchiveOutputStream.closeArchiveEntry();
            zipArchiveOutputStream.flush();
            zipArchiveOutputStream.close();
            return FileUtils.readFileToByteArray(zipFile);
        } catch (Exception ex) {
            log.error("compressData error", ex);
            return null;
        } finally {
            if (csvFile != null) {
                if (csvFile.exists()) {
                    csvFile.delete();
                }
            }
            if (zipFile != null) {
                if (zipFile.exists()) {
                    zipFile.delete();
                }
            }
        }
    }

    /**
     * 获取注单导出文件的数据（压缩后的数据）
     * @param <T>
     * @param exportData
     * @param title
     * @return
     */
    public <T> byte[] getExportCompressData(StringBuffer exportData,String title) {
        byte[] orgCsvData = getExportData(exportData,title);
        if (orgCsvData == null) {
            return null;
        }
        java.io.File csvFile = null;
        java.io.File zipFile = null;
        try {
            // 创建临时文件存储CSV
            String csvFilePath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString() + ".csv";
            log.debug("csv临时文件{}", csvFilePath);
            csvFile = new java.io.File(csvFilePath);
            if (csvFile.exists() == false) {
                csvFile.createNewFile();
            }
            FileUtils.writeByteArrayToFile(csvFile, orgCsvData);
            // 文件压缩
            String zipFilePath = System.getProperty("java.io.tmpdir") + "/" + UUID.randomUUID().toString() + ".zip";
            log.debug("zip临时文件{}", zipFilePath);
            zipFile = new java.io.File(zipFilePath);
            if (zipFile.exists() == false) {
                zipFile.createNewFile();
            }
            ZipArchiveOutputStream zipArchiveOutputStream = new ZipArchiveOutputStream(zipFile);
            zipArchiveOutputStream.setUseZip64(Zip64Mode.AsNeeded);
            ZipArchiveEntry zipArchiveEntry = new ZipArchiveEntry(csvFile, csvFile.getName());
            zipArchiveOutputStream.putArchiveEntry(zipArchiveEntry);
            zipArchiveOutputStream.write(orgCsvData, 0, orgCsvData.length);
            zipArchiveOutputStream.closeArchiveEntry();
            zipArchiveOutputStream.flush();
            zipArchiveOutputStream.close();
            return FileUtils.readFileToByteArray(zipFile);
        } catch (Exception ex) {
            log.error("compressData error", ex);
            return null;
        } finally {
            if (csvFile != null) {
                if (csvFile.exists()) {
                    csvFile.delete();
                }
            }
            if (zipFile != null) {
                if (zipFile.exists()) {
                    zipFile.delete();
                }
            }
        }
    }

    private String getHeader(Map<String, Object> header) {
        StringBuffer titleBuffer = new StringBuffer();
        for (String key : header.keySet()) {
            titleBuffer.append(key).append(",");
        }
        return titleBuffer.toString();
    }

    public byte[] getMapExportData(List<Map<String, Object>> exportData) {
        if (exportData == null || exportData.isEmpty()) {
            return null;
        }
        String title = getHeader(exportData.get(0));
        log.debug("获取的头部内容{}", title);
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // BOM标识
            outputStream.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
            outputStream.write(title.getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
            StringBuffer contentBuffer = null;
            for (Map<String, Object> exportDatum : exportData) {
                contentBuffer = new StringBuffer();
                for (String key : exportDatum.keySet()) {
                    Object obj = exportDatum.get(key);
                    if (obj != null) {
                        contentBuffer.append("\"").append(obj).append("\"").append(",");
                    } else {
                        contentBuffer.append(",");
                    }
                }
                // 换行
                outputStream.write(contentBuffer.toString().getBytes("UTF-8"));
                outputStream.write("\r\n".getBytes("UTF-8"));
            }
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("getExportData error", ex);
            return null;
        }
    }

    /**
     * 获取导出文件的数据
     *
     * @param exportData
     * @param <T>
     * @return
     */
    public <T> byte[] getExportData(List<T> exportData) {
        if (exportData == null || exportData.isEmpty()) {
            return null;
        }
        Class<?> classz;
        T exportElement;
        Field[] fields;
        String title;
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // BOM标识
            outputStream.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
            // 表头
            exportElement = exportData.get(0);
            title = getTitle(exportElement.getClass());
            log.debug("获取的头部内容{}", title);
            outputStream.write(title.getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
            StringBuffer contentBuffer;
            for (int i = 0, len = exportData.size(); i < len; i++) {
                contentBuffer = new StringBuffer();
                exportElement = exportData.get(i);
                classz = exportElement.getClass();
                fields = classz.getDeclaredFields();
                for (Field field : fields) {
                    field.setAccessible(true);
                    // 检查该字段是否有@SeektopExport注解
                    SeektopExport seektopExport = field.getAnnotation(SeektopExport.class);
                    if (ObjectUtils.isEmpty(seektopExport)) {
                        continue;
                    }
                    Object obj = field.get(exportElement);
                    if (obj != null) {
                        contentBuffer.append("\"").append(obj).append("\"").append(",");
                    } else {
                        contentBuffer.append(",");
                    }
                }
                outputStream.write(contentBuffer.toString().getBytes("UTF-8"));
                outputStream.write("\r\n".getBytes("UTF-8"));
            }
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("getExportData error", ex);
            return null;
        }
    }
    
    
    /**
     * 获取导出文件的数据
     * @param <T>
     * @param exportData
     * @param title
     * @return
     */
    public <T> byte[] getExportData(StringBuffer exportData,String title) {
        if (ObjectUtils.isEmpty(exportData)) {
            return null;
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            // BOM标识
            outputStream.write(new byte[]{(byte)0xEF, (byte)0xBB, (byte)0xBF});
       
            log.debug("获取的头部内容{}", title);
            outputStream.write(title.getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.write(exportData.toString().getBytes("UTF-8"));
            outputStream.write("\r\n".getBytes("UTF-8"));
            outputStream.flush();
            return outputStream.toByteArray();
        } catch (Exception ex) {
            ex.printStackTrace();
            log.error("getExportData error", ex);
            return null;
        }
    }

    /**
     * 获取要导出数据的表头
     *
     * @param classz
     * @param <T>
     * @return
     */
    public <T> String getTitle(Class<T> classz) {
        StringBuffer titleBuffer = new StringBuffer();
        try {
            Field[] fields = classz.getDeclaredFields();
            for (Field field : fields) {
                field.setAccessible(true);
                SeektopExport seektopExport = field.getAnnotation(SeektopExport.class);
                if (ObjectUtils.isEmpty(seektopExport)) {
                    continue;
                }
                titleBuffer.append(seektopExport.name()).append(",");
            }
        } catch (Exception ex) {
            log.error("get export header title error", ex);
        }
        return titleBuffer.toString();
    }

    private static final ExportUtils instance = new ExportUtils();

    private ExportUtils() {

    }

    public static ExportUtils getInstance() {
        return instance;
    }

}
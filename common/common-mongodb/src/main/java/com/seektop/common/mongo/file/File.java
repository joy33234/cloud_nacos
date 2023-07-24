package com.seektop.common.mongo.file;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Date;

/**
 * Created by ken on 2018/3/27.
 */

@Document
public class File {
    @Id  // 主键
    private String id;
    private String name; // 文件名称
    private String contentType; // 文件类型
    private long size;
    private Date uploadDate;
    private String md5;
    private byte[] content; // 文件内容
    private String path; // 文件路径
    private java.io.File localFile;

    protected File() {
    }

    public java.io.File getLocalFile() {
		return localFile;
	}

	public void setLocalFile(java.io.File localFile) {
		this.localFile = localFile;
	}

	public File(String name, String contentType, long size, byte[] content) {
        this.name = name;
        this.contentType = contentType;
        this.size = size;
        this.uploadDate = new Date();
        this.content = content;
    }
    
    public File(String name, String contentType, long size, Date uploadDate, String md5, java.io.File localFile,byte[] content) {
		this.name = name;
		this.contentType = contentType;
		this.size = size;
		this.uploadDate = uploadDate;
		this.md5 = md5;
		this.localFile = localFile;
		this.content = content;
	}
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public long getSize() {
        return size;
    }

    public void setSize(long size) {
        this.size = size;
    }

    public Date getUploadDate() {
        return uploadDate;
    }

    public void setUploadDate(Date uploadDate) {
        this.uploadDate = uploadDate;
    }

    public String getMd5() {
        return md5;
    }

    public void setMd5(String md5) {
        this.md5 = md5;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        File fileInfo = (File) object;
        return java.util.Objects.equals(size, fileInfo.size)
                && java.util.Objects.equals(name, fileInfo.name)
                && java.util.Objects.equals(contentType, fileInfo.contentType)
                && java.util.Objects.equals(uploadDate, fileInfo.uploadDate)
                && java.util.Objects.equals(md5, fileInfo.md5)
                && java.util.Objects.equals(id, fileInfo.id);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(name, contentType, size, uploadDate, md5, id);
    }

    @Override
    public String toString() {
        return "File{"
                + "name='" + name + '\''
                + ", contentType='" + contentType + '\''
                + ", size=" + size
                + ", uploadDate=" + uploadDate
                + ", Md5='" + md5 + '\''
                + ", id='" + id + '\''
                + '}';
    }
}

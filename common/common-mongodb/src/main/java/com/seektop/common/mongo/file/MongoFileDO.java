package com.seektop.common.mongo.file;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
public class MongoFileDO implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 7658532277752610853L;

	/**
     * 文件内容
     */
    private byte[] data;

    /**
     * 文件名称
     */
    private String fileName;

    /**
     * 文件类型
     */
    private String contentType;

    private MongoFileProperties properties;


    public MongoFileDO(String fileName, String contentType, byte[] data){
        this.contentType = contentType;
        this.fileName = fileName;
        this.data = data;
    }

    public MongoFileDO(byte[] data, String fileName, String contentType){
        this.contentType = contentType;
        this.fileName = fileName;
        this.data = data;
    }

}
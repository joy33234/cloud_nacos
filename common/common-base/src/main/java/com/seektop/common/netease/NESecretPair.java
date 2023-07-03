package com.seektop.common.netease;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class NESecretPair implements Serializable {

    /**
	 * 
	 */
	private static final long serialVersionUID = 6443579642429119510L;

	/**
     * 密钥对id
     */
    public String secretId;

    /**
     * 密钥对key
     */
    public String secretKey;

}
package com.seektop.fund.payment.xinhuipay.secure.utils;

import com.seektop.fund.payment.xinhuipay.secure.SignManager;
import com.seektop.fund.payment.xinhuipay.secure.SignManagerImpl;

import java.security.PrivateKey;

/**
 * @Description <p> 功能描述：对数据的签名、验签操作
 * </p>
 * @author ty
 * @since 2016年7月6日
 * @version 1.0.0
 * @ModifyBy 
 *
 */
public class RSASignUtil {
    
    /**
     * 
     * @Description <p> rsa签名
     * </p>
     * @param plainData
     * @return
     * @throws Exception
     */
    public static String signByRsa(String plainData,PrivateKey privateKey) throws Exception{
    	
    	SignManagerImpl signManager = new SignManagerImpl();
		String signature = signManager.sign(plainData, SignManager.RSA_ALGORITHM_NAME, privateKey);
		
		return signature;
	}

}

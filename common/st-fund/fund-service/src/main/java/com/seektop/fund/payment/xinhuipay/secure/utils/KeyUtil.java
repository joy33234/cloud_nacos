package com.seektop.fund.payment.xinhuipay.secure.utils;

import com.seektop.fund.payment.xinhuipay.secure.KeyReader;
import com.seektop.fund.payment.xinhuipay.secure.SignManager;

import java.security.PrivateKey;

/**
 * @Description <p> 这个是key的工具类，用来管理我们的key
 * </p>
 * @author ty
 * @since 2016年5月31日
 * @version 1.0.0
 * @ModifyBy 
 *
 */
public class KeyUtil {
    
    /**

     * @Description <p> 获取签名rsa密钥
     * </p>
     * @param
     * @param
     * @return
     * @throws Exception
     */
    public static PrivateKey getSignRsaPrivateKey(String privateKeyStr) throws Exception {
    	KeyReader keyReader = new KeyReader();
    	PrivateKey key = keyReader.readPrivateKey(privateKeyStr, true, SignManager.RSA_ALGORITHM_NAME);
    	
    	return key;
    }

        
}

package com.seektop.fund.payment.xinhuipay.secure;

import java.security.PrivateKey;
import java.security.PublicKey;

public interface SignManager {
	 String MD5_ALGORITHM_NAME = "MD5";

	 String RSA_ALGORITHM_NAME = "RSA";

	 String DSA_ALGORITHM_NAME = "DSA";

	 String RSA_SIGN_ALGORITHMS = "SHA1WithRSA";

	 String DSA_SIGN_ALGORITHMS = "DSA";

	 String DEFAULT_CHARSET = "UTF-8";

	/**
	 * 生成BASE64编码后的签名串，支持MD5/RSA/DSA算法
	 * 
	 * @param content
	 * @param signType
	 * @param key
	 * @return
	 */
	public String sign(String content, String signType, String key);

	/**
	 * 校验签名，支持MD5/RSA/DSA算法
	 * 
	 * @param signature
	 * @param content
	 * @param signType
	 * @param key
	 * @return
	 */
	public boolean check(String signature, String content, String signType,
                         String key);

	/**
	 * 签名，使用非对称密钥，不支持自制MD5
	 *
	 * @param content
	 * @param signType
	 * @param priKey
	 * @return
	 */
	public String sign(String content, String signType, PrivateKey priKey);

	/**
	 * 签名校验，使用非对称密钥，不支持自制MD5
	 *
	 * @param signature
	 * @param content
	 * @param signType
	 * @param pubKey
	 * @return
	 */
	public boolean check(String signature, String content, String signType,
                         PublicKey pubKey);
}

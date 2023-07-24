package com.seektop.fund.payment.fenbeifu;

import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import sun.security.util.DerInputStream;
import sun.security.util.DerValue;

import javax.crypto.Cipher;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.RSAPrivateCrtKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.*;

public class RSAPemCoder {
    public static final String KEY_ALGORITHM = "RSA";
    public static final String SIGNATURE_ALGORITHM = "SHA1withRSA";

    /**
     * 用私钥对信息生成数字签名
     *
     * @param privateKey 私钥
     * @return
     * @throws Exception
     */
    public static String sign(TreeMap<String, String> treeMap, PrivateKey privateKey) throws Exception {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append("&" + key.trim() + "=" + value.trim());
        }

        String strMap = sb.toString().substring(1);
        byte[] data = strMap.getBytes(Charset.forName("utf-8"));
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initSign(privateKey);
        signature.update(data);
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    /**
     * 校验数字签名
     *
     * @param publicKey 公钥
     * @param sign      数字签名
     * @return 校验成功返回true 失败返回false
     * @throws Exception
     */
    public static boolean verify(TreeMap<String, String> treeMap, PublicKey publicKey, String sign) throws Exception {
        StringBuffer sb = new StringBuffer();
        for (Map.Entry<String, String> entry : treeMap.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            sb.append("&" + key.trim() + "=" + value.trim());
        }

        String strMap = sb.toString().substring(1);
        byte[] data = strMap.getBytes(Charset.forName("utf-8"));
        Signature signature = Signature.getInstance(SIGNATURE_ALGORITHM);
        signature.initVerify(publicKey);
        signature.update(data);
        return signature.verify(decryptBASE64(sign));
    }

    /**
     * 用公钥加密
     *
     * @param data 明文
     *             //     * @param PublicKey 公钥
     * @return
     * @throws Exception
     */
    public static String encryptByPublicKey(byte[] data, PublicKey publicKey) throws Exception {
        KeyFactory keyFactory = KeyFactory.getInstance(KEY_ALGORITHM);
        Cipher cipher = Cipher.getInstance(keyFactory.getAlgorithm());
        cipher.init(Cipher.ENCRYPT_MODE, publicKey);
        return Base64.getEncoder().encodeToString(cipher.doFinal(data));
    }


    public static byte[] decryptBASE64(String key) throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] buffer = decoder.decode(key);
        return buffer;
    }

    public static PublicKey getPublicKey(String publicKey) throws Exception {
        Base64.Decoder decoder = Base64.getDecoder();
        byte[] b = decoder.decode(publicKey);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        X509EncodedKeySpec keySpec = new X509EncodedKeySpec(b);
        PublicKey pubKey = kf.generatePublic(keySpec);
        return pubKey;
    }

    public static PrivateKey getPrivateKey(String privateKeyStr) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(privateKeyStr);

        DerInputStream derReader = new DerInputStream(bytes);
        DerValue[] seq = derReader.getSequence(0);
        // skip version seq[0];
        BigInteger modulus = seq[1].getBigInteger();
        BigInteger publicExp = seq[2].getBigInteger();
        BigInteger privateExp = seq[3].getBigInteger();
        BigInteger prime1 = seq[4].getBigInteger();
        BigInteger prime2 = seq[5].getBigInteger();
        BigInteger exp1 = seq[6].getBigInteger();
        BigInteger exp2 = seq[7].getBigInteger();
        BigInteger crtCoef = seq[8].getBigInteger();

        RSAPrivateCrtKeySpec keySpec =
                new RSAPrivateCrtKeySpec(modulus, publicExp, privateExp, prime1, prime2, exp1, exp2, crtCoef);
        KeyFactory keyFactory = KeyFactory.getInstance("RSA");
        PrivateKey privateKey = keyFactory.generatePrivate(keySpec);
        return privateKey;
    }


    // HTTP POST请求
    public static String sendPost(String url, TreeMap<String, String> postData) throws Exception {
        CloseableHttpClient client = HttpClients.createDefault();
        HttpPost httpPost = new HttpPost(url);

        List<BasicNameValuePair> params = new ArrayList<>();
        for (Map.Entry<String, String> entry : postData.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            params.add(new BasicNameValuePair(key, value));
        }
        httpPost.setEntity(new UrlEncodedFormEntity(params, "utf-8"));
        CloseableHttpResponse response = client.execute(httpPost);
        String content = EntityUtils.toString(response.getEntity());
        client.close();
        return content;
    }


}
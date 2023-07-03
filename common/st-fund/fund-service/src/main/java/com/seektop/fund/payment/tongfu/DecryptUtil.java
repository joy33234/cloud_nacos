package com.seektop.fund.payment.tongfu;

import org.bouncycastle.jce.provider.BouncyCastleProvider;

import javax.crypto.Cipher;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.Base64;

public class DecryptUtil {

    private static BouncyCastleProvider provider = new BouncyCastleProvider();

    static {
        Security.addProvider(provider);
    }

    public static String aes(String content, String key) {
        String encryptValue = "";
        try {
            byte[] plaintext = content.getBytes(StandardCharsets.UTF_8);

            Cipher cipher = Cipher.getInstance("AES/ECB/PKCS7Padding", "BC");
            SecretKeySpec keySpec = new SecretKeySpec(key.getBytes(StandardCharsets.UTF_8), "AES");

            cipher.init(Cipher.DECRYPT_MODE, keySpec);

            byte[] bys = cipher.doFinal(Base64.getDecoder().decode(plaintext));

            encryptValue = new String(bys, StandardCharsets.UTF_8);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return encryptValue;
    }

}

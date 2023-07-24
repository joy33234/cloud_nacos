package com.seektop.common.encrypt.enums.builder;


import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.common.utils.StringEncryptor;
import com.seektop.constant.ProjectConstant;
import lombok.Getter;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.function.Function;

@Getter
public class Encryptor {
    private Integer encryptType;
    private Function<String,String> encrypt;
    private Function<String,String> orElse;

    private Encryptor(EncryptBuilder encryptBuilder){
        this.encryptType = encryptBuilder.encryptType;
        this.encrypt = encryptBuilder.encrypt;
        this.orElse = encryptBuilder.orElse;
    }

    public static EncryptBuilder builder(){
        return new EncryptBuilder();
    }

    public static EncryptBuilder builder(EncryptTypeEnum encryptTypeEnum){
        return new EncryptBuilder()
                .encryptType(encryptTypeEnum.getJobEncryptId())
                .encrypt(encryptTypeEnum.getParse());
    }

    /**
     * 逗号分隔，字段非正则脱敏
     * @param encryptType
     * @param parse
     * @return
     */
    public static EncryptBuilder commaSplitEncrypt(Integer encryptType,Function parse){
        return new EncryptBuilder()
                .encryptType(encryptType)
                .encrypt(
                        source-> StringEncryptor.commaSplitEncrypt(source,parse)
                );
    }
    public static EncryptBuilder builderMobile(){
        return new EncryptBuilder()
                .encryptType(ProjectConstant.EncryptPermission.MOBILE)
                .encrypt(StringEncryptor::encryptMobile);
    }
    public static EncryptBuilder builderEmail(){
        return new EncryptBuilder()
                .encryptType(ProjectConstant.EncryptPermission.EMAIL)
                .encrypt(StringEncryptor::encryptEmail);
    }
    public static EncryptBuilder builderName(){
        return new EncryptBuilder()
                .encryptType(ProjectConstant.EncryptPermission.NAME)
                .encrypt(StringEncryptor::encryptUsername);
    }
    public static EncryptBuilder builderAddress(){
        return new EncryptBuilder()
                .encryptType(ProjectConstant.EncryptPermission.ADDRESS)
                .encrypt(StringEncryptor::encryptAddress);
    }
    public static EncryptBuilder builderBankCard(){
        return new EncryptBuilder()
                .encryptType(ProjectConstant.EncryptPermission.BANKCARD)
                .encrypt(StringEncryptor::encryptBankCard);
    }
    public static class EncryptBuilder{
        private Integer encryptType;
        private Function<String,String> encrypt;
        private Function<String,String> orElse;

        public EncryptBuilder encryptType(Integer encryptType) {
            this.encryptType = encryptType;
            return this;
        }

        public EncryptBuilder encrypt(Function<String,String> encrypt) {
            this.encrypt = encrypt;
            return this;
        }

        public EncryptBuilder orElse(Function<String,String> orElse) {
            this.orElse = orElse;
            return this;
        }
        public Encryptor build(){
            return new Encryptor(this);
        }
        public  String doEncrypt(String source){
            Encryptor encrypt = new Encryptor(this);
            if(StringUtils.isEmpty(source))return source;
            //查询得到脱敏权限
            List<Integer> jonEncrypts = JobEncryptPermissionUtils.getJobEncryptPermissions();//防止一次请求频繁查询数据库
            try {
                if (!jonEncrypts.contains(encrypt.encryptType)) {
                    if (encrypt.encrypt != null) {
                        return encrypt.encrypt.apply(source);
                    }
                } else {
                    if (encrypt.orElse != null) {
                        return encrypt.orElse.apply(source);
                    }
                }
            }catch (Exception e){
                return source;
            }
            return source;
        }
    }

}

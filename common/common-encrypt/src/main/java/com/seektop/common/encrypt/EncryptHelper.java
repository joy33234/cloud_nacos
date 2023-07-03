package com.seektop.common.encrypt;


import com.google.common.collect.Lists;
import com.seektop.common.encrypt.enums.EncryptTypeEnum;
import com.seektop.common.encrypt.enums.builder.Encryptor;
import com.seektop.common.encrypt.model.EncryptFieldModel;
import com.seektop.common.function.NormalFunction;
import com.seektop.common.function.NormalSupplier;
import com.seektop.common.utils.JobEncryptPermissionUtils;
import com.seektop.common.utils.UserIdUtils;
import com.seektop.constant.ProjectConstant;
import com.seektop.exception.GlobalException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

@Slf4j
@Import(EncryptComponent.class)
public class EncryptHelper {

    private static EncryptComponent encryptComponent;


    @Autowired
    public void setEncryptComponent(EncryptComponent encryptComponent){
        EncryptHelper.encryptComponent = encryptComponent;
        JobEncryptPermissionUtils.setGetJobEncrypt(()->encryptComponent.getJobEncrypt());
    }

    /**
     * 根据岗位ID得到数据权限
     */
    public static List<Integer> getJobEncrypt(){
        return encryptComponent.getJobEncrypt();
    }

    /**
     * 根据岗位和脱敏字段获取脱敏器
     */
    public static Map<String, List<Function<String,String>>> getEncryptorsByJob(Integer userId, List<EncryptFieldModel> encryptFieldModels){
        List<Integer> jobEncrypts = encryptComponent.getJobEncryptByToken(userId);
        //每一个属性对应的脱敏器
        HashMap<String,List<Function<String,String>>> fieldParseMap= new HashMap<>();
        encryptFieldModels.forEach(encryptFieldModel -> {
            //初始化脱敏器
            //获取需要脱敏的字段
            for (EncryptFieldModel value : encryptFieldModels) {
                ArrayList<Function<String,String>> parses = Lists.newArrayList();
                fieldParseMap.put(value.getFieldName(),parses);
                for (EncryptTypeEnum encryptTypeEnum : value.getTypeEnums()) {
                    if(!jobEncrypts.contains(encryptTypeEnum.getJobEncryptId())){
                        parses.add(encryptTypeEnum.getParse());
                    }
                }
            }
        });
        return fieldParseMap;
    }
   public static List<Function<String,String>> getParses(List<EncryptTypeEnum> encryptTypeEnums){
       List<Integer> jobEncrypts = encryptComponent.getJobEncrypt();
       List<Function<String,String>> parses = Lists.newArrayList();
       for (EncryptTypeEnum encryptTypeEnum : encryptTypeEnums) {
           if(!jobEncrypts.contains(encryptTypeEnum.getJobEncryptId())){
               parses.add(encryptTypeEnum.getParse());
           }
       }
       return parses;
   }
    public static String doEncrypt(String source, Encryptor... encryptors){
        List<Integer> jobEncrypt = JobEncryptPermissionUtils.getJobEncryptPermissions();
        for (Encryptor encryptor : encryptors) {
            try {
                if(!jobEncrypt.contains(encryptor.getEncryptType())){
                    if(encryptor.getEncrypt() !=null) {
                        source = encryptor.getEncrypt().apply(source);
                    }
                }else{
                    if(encryptor.getOrElse()!=null) {
                        source = encryptor.getOrElse().apply(source);
                    }
                }
            }catch (Exception e){
                log.error("脱敏失败--->源数据：{}  异常：{}",source,e);
              return source;
            }
        }
        return source;
    }

    /**
     * 只检查是否执行
     * @param encryptType
     * @see
     * @param
     */
    public static void check(Integer encryptType, NormalFunction normalFunction){
        List<Integer> jobEncryptPermissions = JobEncryptPermissionUtils.getJobEncryptPermissions();
        if(!jobEncryptPermissions.contains(encryptType)) {
            normalFunction.execute();
        }
    }
    /**
     * !!! 只能在异步线程使用
     */
    public  static <T> T startEncrypt(NormalSupplier<T> supplier, Integer userId) throws GlobalException {
        try {
            UserIdUtils.setUserId(userId);
            JobEncryptPermissionUtils.release();
            return supplier.execute();
        }finally {
            JobEncryptPermissionUtils.release();
            UserIdUtils.release();
        }
    }


    /**
     * checkEMail
     */
    public static void checkEMail(NormalFunction normalFunction){
        check(ProjectConstant.EncryptPermission.EMAIL,normalFunction);
    }
    /**
     * checkMobile
     */
    public static void checkMobile(NormalFunction normalFunction){
        check(ProjectConstant.EncryptPermission.MOBILE,normalFunction);
    }
    /**
     * checkAddress
     */
    public static void checkAddress (NormalFunction normalFunction){
        check(ProjectConstant.EncryptPermission.ADDRESS,normalFunction);
    }
    /**
     * checkName
     */
    public static void checkName (NormalFunction normalFunction){
        check(ProjectConstant.EncryptPermission.NAME,normalFunction);
    }
    /**
     * checkBankcard
     */
    public static void checkBankcard (NormalFunction normalFunction){
        check(ProjectConstant.EncryptPermission.BANKCARD,normalFunction);
    }
}

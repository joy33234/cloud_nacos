package com.seektop.common.rest;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.seektop.base.LocalKey;
import com.seektop.enumerate.ResultCode;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.commons.lang3.StringUtils;

@Data
@NoArgsConstructor
public class Result {

    /**
     * 响应的消息内容
     */
    private String message;

    /**
     * 响应的状态码
     */
    private Integer code;

    /**
     * 响应的数据内容
     */
    private Object data;

    private LocalKey keyConfig;
    private String[] languagePassVal;
    public Result(Builder builder) {
        this.message = builder.message;
        this.code = builder.code;
        this.data = builder.data;
        this.keyConfig = builder.localConfigKey;
        this.languagePassVal = builder.languagePassVal;
    }

    public static Builder newBuilder() {
        return new Builder();
    }

    public static class Builder {

        private String message;
        private Integer code;
        private Object data;
        private LocalKey localConfigKey;
        private String[] languagePassVal;

        public Builder() {

        }

        public Builder success() {
            this.code = ResultCode.SUCCESS.getCode();
            this.message = ResultCode.SUCCESS.getMessage();
            return this;
        }

        public Builder success(final Integer code) {
            this.code = code;
            return this;
        }

        public Builder fail() {
            this.code = ResultCode.FAIL_DEFAULT.getCode();
            return this;
        }

        public Builder fail(final Integer code) {
            this.code = code;
            return this;
        }

        public Builder fail(ResultCode code) {
            this.code = code.getCode();
            this.message = code.getMessage();
            return this;
        }

        public Builder paramError() {
            this.code = ResultCode.PARAM_ERROR.getCode();
            this.message = ResultCode.PARAM_ERROR.getMessage();
            return this;
        }

        public Builder serverError() {
            this.code = ResultCode.SERVER_ERROR.getCode();
            this.message = ResultCode.SERVER_ERROR.getMessage();
            return this;
        }

        @Deprecated
        public Builder setMessage(String message) {
            this.message = message;
            return this;
        }

        public Builder setCode(Integer code) {
            this.code = code;
            return this;
        }

        public Builder setLocalConfigKey(LocalKey localConfigKey,String ... passeVal) {
            this.localConfigKey = localConfigKey;
            this.languagePassVal = passeVal;
            return this;
        }

        public <T> Builder addData(T t) {
            this.data = t;
            return this;
        }

        public Result build() {
            return new Result(this);
        }

    }

    @Deprecated
    public static Result genFailResult(String message){
        return Result.newBuilder().fail().setMessage(message).build();
    }

    public static Result genFailResult(LocalKey localConfigKey){
        return Result.newBuilder().fail().setLocalConfigKey(localConfigKey).build();
    }

    @Deprecated
    public static Result genFailResult(Integer code, String message){
        return Result.newBuilder().fail().setCode(code).setMessage(message).build();
    }


    public static Result genFailResult(Integer code, LocalKey localConfigKey){
        return Result.newBuilder().fail().setCode(code).setLocalConfigKey(localConfigKey).build();
    }

    public static Result genSuccessResult(Object data){
        return Result.newBuilder().success().addData(data).build();
    }
    public static Result genSuccessResultWithMessage(String message){
        return Result.newBuilder().success().setMessage(message).build();
    }
    public static Result genSuccessResultWithMessage(LocalKey localConfigKey){
        return Result.newBuilder().success().setLocalConfigKey(localConfigKey).build();
    }

    public static Result genSuccessResult(){
        return Result.newBuilder().success().build();
    }

    public static Result genResultByString(String pattern){
        //因Result setCode()方法冲突,无法自动转,需手动转
        if (StringUtils.isNotEmpty(pattern)) {
            Result result = new Result();
            try{
                JSONObject creatorJson = JSON.parseObject(pattern);
                result.setCode(creatorJson.getInteger("code"));
                result.setMessage(creatorJson.getString("message"));
                JSONArray data = creatorJson.getJSONArray("data");
                result.setData(data.toArray(new String[]{}));
                return result;
            }catch (Exception e){
                return null;
            }
        }
        return null;
    }

    @Deprecated
    public static Result genFailResult(int code, String message, Object data) {
        return Result.newBuilder().fail().setCode(code).setMessage(message).addData(data).build();
    }

    public static Result genFailResult(int code, LocalKey localConfigKey, Object data) {
        return Result.newBuilder().fail().setCode(code).setLocalConfigKey(localConfigKey).addData(data).build();
    }

}
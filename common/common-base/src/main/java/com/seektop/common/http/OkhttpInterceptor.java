package com.seektop.common.http;

import com.alibaba.fastjson.JSON;
import com.seektop.dto.StatusInfoDto;
import com.seektop.enumerate.MyHttpStatusEnum;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.net.URLDecoder;

@Slf4j
public class OkhttpInterceptor implements Interceptor {

    @Override
    public Response intercept(Chain chain) throws IOException {

            long startTime = System.currentTimeMillis();
            Request request = chain.request();
            //url去参数处理（安全考虑）
            String url = request.url().toString();
            StatusInfoDto statusInfoDto = StatusInfoDto.builder().event(1).type("okhttp").url(url).method(request.method()).build();
            statusInfoDto.setTimeOutConfig(chain.connectTimeoutMillis());
            Response response = null;
            try {
                response = chain.proceed(chain.request());
                //正常日志记录
                statusInfoDto.setStatus(response.code());
                statusInfoDto.setMsg(response.message());
                statusInfoDto.setErrorInfo("");

                //请求头中的业务参数
                statusInfoDto.setAction(request.header("glAction"));
                String glChannelName = request.header("glChannelName");
                glChannelName = StringUtils.isBlank(glChannelName) ? glChannelName : URLDecoder.decode(glChannelName, "UTF-8");
                statusInfoDto.setChannelName(glChannelName);
                statusInfoDto.setUserName( request.header("glUserName"));
                statusInfoDto.setTradeId(request.header("glTradeId"));
                String channelId =  request.header("glChannelId");
                statusInfoDto.setTerminal(request.header("glTerminal"));
                String userIdStr  = request.header("glUserId");
                Integer userId =  StringUtils.isNotBlank(userIdStr)?Integer.parseInt(userIdStr):null;
                statusInfoDto.setChannelId(channelId);
                statusInfoDto.setUserId(userId);
            } catch (IOException e) {
                statusInfoDto.setStatus(MyHttpStatusEnum.HTTP_IO_ERROR.getStatus());
                statusInfoDto.setMsg(MyHttpStatusEnum.HTTP_IO_ERROR.getMsg());
                //socket连接成功后，发生请求阶段时抛出的各类网络异常
                if (e instanceof SocketTimeoutException) {
                    statusInfoDto.setStatus(MyHttpStatusEnum.HTTP_CONNECT_TIME_OUT.getStatus());
                    statusInfoDto.setMsg(MyHttpStatusEnum.HTTP_CONNECT_TIME_OUT.getMsg());
                } else if (e instanceof ConnectException) {
                    statusInfoDto.setStatus(MyHttpStatusEnum.HTTP_CONNECT_ERROR.getStatus());
                    statusInfoDto.setMsg(MyHttpStatusEnum.HTTP_CONNECT_TIME_OUT.getMsg());
                }
                statusInfoDto.setErrorInfo("异常类型：" + e.getClass() + "  异常简介：" + e.getMessage());
            } catch (Exception e) {
                statusInfoDto.setStatus(MyHttpStatusEnum.HTTP_ERROR.getStatus());
                statusInfoDto.setMsg(MyHttpStatusEnum.HTTP_ERROR.getMsg());
                statusInfoDto.setErrorInfo("异常类型：" + e.getClass() + "  异常简介：" + e.getMessage());
            }
            statusInfoDto.setSpendTime(System.currentTimeMillis() - startTime);
            log.info(JSON.toJSONString(statusInfoDto));
            return response;
    }

}
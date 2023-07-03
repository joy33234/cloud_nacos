package com.seektop.common.function;


import okhttp3.Response;

@FunctionalInterface
public interface OKHttpRequestPostHandler {
    Object post(Response response);
}

package com.seektop.common.redis.function;

public interface TransactionalFunction<T> {

    T doTransaction() throws Exception;

}
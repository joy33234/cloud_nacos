package com.seektop.common.function;

@FunctionalInterface
public interface CommonFunction<T,R> {
    R execute(T t);
}

package com.seektop.common.function;

import com.seektop.exception.GlobalException;

@FunctionalInterface
public interface NormalConsumer<T> {
    void execute(T param) throws GlobalException;
}

package com.seektop.common.function;

import com.seektop.exception.GlobalException;

@FunctionalInterface
public interface NormalSupplier<T> {
    T execute() throws GlobalException;
}

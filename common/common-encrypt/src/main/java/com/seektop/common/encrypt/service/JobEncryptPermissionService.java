package com.seektop.common.encrypt.service;

import com.seektop.exception.GlobalException;

import java.util.List;

public interface JobEncryptPermissionService {
    List<Integer> findByUserId(Integer userId) throws GlobalException;
}

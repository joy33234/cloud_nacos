package com.seektop.common.executor.dto;

import lombok.*;

@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionDTO {
    private String id;
    private Object data;
    private String errorData;
}

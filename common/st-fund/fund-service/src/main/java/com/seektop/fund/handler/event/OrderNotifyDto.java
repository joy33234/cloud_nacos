package com.seektop.fund.handler.event;

import lombok.Data;

import java.io.Serializable;

@Data
public class OrderNotifyDto implements Serializable {
    private static final long serialVersionUID = -6823497613084195085L;

    private String url;
}

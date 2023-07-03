package com.seektop.fund.vo;

import com.seektop.fund.model.BindCardRecord;
import lombok.Data;

import java.util.Date;

@Data
public class BindCardRecordForm extends BindCardRecord {
    private static final long serialVersionUID = 6346255923776709122L;

    private Date startTime;
    private Date endTime;

    private Integer page = 1;
    private Integer size = 10;
}

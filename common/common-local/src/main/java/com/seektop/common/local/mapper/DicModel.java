package com.seektop.common.local.mapper;

import com.seektop.common.local.base.DataSourceEntity;
import lombok.Data;

import javax.persistence.*;

@Data
@Table(name = "gl_language_dic")
public class DicModel implements DataSourceEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key")
    private String configKey;

    @Column(name = "config_value")
    private String configValue;

    private String language;

    private String module;
}

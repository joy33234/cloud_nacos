package com.seektop.common.csvexport.configuration;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@ConfigurationProperties(
        prefix = "com.seektop.export"
)
@EnableConfigurationProperties(ExportConfiguration.class)
public class ExportConfiguration {
    private ES es;
    private MYSQL mysql;
    private String name;
    public static class ES{
        private Long size;
        private Long page;

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Long getPage() {
            return page;
        }

        public void setPage(Long page) {
            this.page = page;
        }
    }
    public static class MYSQL{
        private Long size;
        private Long page;

        public Long getSize() {
            return size;
        }

        public void setSize(Long size) {
            this.size = size;
        }

        public Long getPage() {
            return page;
        }

        public void setPage(Long page) {
            this.page = page;
        }
    }

    public ES getEs() {
        return es;
    }

    public void setEs(ES es) {
        this.es = es;
    }

    public MYSQL getMysql() {
        return mysql;
    }

    public void setMysql(MYSQL mysql) {
        this.mysql = mysql;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}

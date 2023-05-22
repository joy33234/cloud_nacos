//package com.ruoyi.job.config;
//
//
//import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
//import com.baomidou.mybatisplus.autoconfigure.SpringBootVFS;
//import com.baomidou.mybatisplus.core.MybatisConfiguration;
//import com.baomidou.mybatisplus.core.MybatisXMLLanguageDriver;
//import com.baomidou.mybatisplus.extension.spring.MybatisSqlSessionFactoryBean;
//import org.apache.commons.lang.StringUtils;
//import org.apache.ibatis.mapping.DatabaseIdProvider;
//import org.apache.ibatis.plugin.Interceptor;
//import org.springframework.beans.factory.annotation.Autowired;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.core.io.DefaultResourceLoader;
//import org.springframework.core.io.Resource;
//import org.springframework.core.io.ResourceLoader;
//import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
//import org.springframework.core.io.support.ResourcePatternResolver;
//
//import javax.sql.DataSource;
//import java.io.IOException;
//
//@Configuration
//public class MybatisPlusConfig {
//    @Autowired
//    private DataSource dataSource;
//
//    @Autowired
//    private MybatisPlusProperties properties;
//
//    @Autowired
//    private ResourceLoader resourceLoader = new DefaultResourceLoader();
//
//    @Autowired(required = false)
//    private Interceptor[] interceptors;
//
//    @Autowired(required = false)
//    private DatabaseIdProvider databaseIdProvider;
//
//    @Bean
//    public MybatisSqlSessionFactoryBean mybatisSqlSessionFactoryBean() throws IOException {
//        MybatisSqlSessionFactoryBean mybatisPlus = new MybatisSqlSessionFactoryBean();
//        mybatisPlus.setDataSource(dataSource);
//        mybatisPlus.setVfs(SpringBootVFS.class);
//        String configLocation = this.properties.getConfigLocation();
//        if(StringUtils.isNotEmpty(configLocation)){
//            mybatisPlus.setConfigLocation(this.resourceLoader.getResource(configLocation));
//        }
//        mybatisPlus.setConfiguration(properties.getConfiguration());
//        mybatisPlus.setPlugins(interceptors);
//        MybatisConfiguration mc = new MybatisConfiguration();
//        mc.setDefaultScriptingLanguage(MybatisXMLLanguageDriver.class);
//        mc.setMapUnderscoreToCamelCase(true);// 数据库和java都是驼峰，就不需要
//        mybatisPlus.setConfiguration(mc);
//        if (this.databaseIdProvider != null) {
//            mybatisPlus.setDatabaseIdProvider(this.databaseIdProvider);
//        }
//        mybatisPlus.setTypeAliasesPackage(this.properties.getTypeAliasesPackage());
//        mybatisPlus.setTypeHandlersPackage(this.properties.getTypeHandlersPackage());
//        mybatisPlus.setMapperLocations(this.properties.resolveMapperLocations());
//        // 设置mapper.xml文件的路径
//        ResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
//        Resource[] resource = resolver.getResources("classpath:mapper/**/*.xml");
//        mybatisPlus.setMapperLocations(resource);
//        return mybatisPlus;
//
//    }
//}
//
//

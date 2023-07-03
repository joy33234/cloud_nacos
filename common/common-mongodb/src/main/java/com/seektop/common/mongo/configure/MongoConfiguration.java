package com.seektop.common.mongo.configure;

import com.mongodb.client.gridfs.GridFSBucket;
import com.mongodb.client.gridfs.GridFSBuckets;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

@Configuration
public class MongoConfiguration {

    @Bean
    public GridFSBucket getGridFSBucket(MongoTemplate mongoTemplate) {
        GridFSBucket gridFSBucket = GridFSBuckets.create(mongoTemplate.getDb());
        return gridFSBucket;
    }

}
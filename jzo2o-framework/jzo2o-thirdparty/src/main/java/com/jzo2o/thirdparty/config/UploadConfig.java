package com.jzo2o.thirdparty.config;

import com.qiniu.storage.Configuration;
import com.qiniu.storage.UploadManager;
import com.qiniu.util.Auth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

@org.springframework.context.annotation.Configuration
public class UploadConfig {

    @Value("${ali.oss.accessKeyId}")
    private String accessKey;
    @Value("${ali.oss.accessKeySecret}")
    private String secretKey;

    @Bean
    public Auth getAuth(){
        return Auth.create(accessKey,secretKey);
    }

    @Bean
    public UploadManager getUploadManager(){
        return new UploadManager(new Configuration());
    }
}
package com.jzo2o.es.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "jzo2o.es")
@Data
public class EsProperties {
    /**
     * es host
     */
    private String host= "192.168.101.68";
    /**
     * es 端口
     */
    private Integer port=9200;
}

package com.scrapider.finance.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "influxdb")
public class InfluxDbProperties {

    private String url;
    private String token;
    private String org;
    private String bucket;
    private String stockMinuteMeasurement = "stock_minute";
    private String timezone = "Asia/Shanghai";
}

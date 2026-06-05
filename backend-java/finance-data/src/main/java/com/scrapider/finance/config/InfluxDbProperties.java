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
    private String stockMinuteBucket;
    private String indexMinuteBucket = "index_intraday";
    private String bondMinuteBucket = "bond_intraday";
    private String stockMinuteMeasurement = "stock_minute";
    private String indexMinuteMeasurement = "index_minute";
    private String bondMinuteMeasurement = "bond_minute";
    private String timezone = "Asia/Shanghai";
}

package com.scrapider.finance.config;

import com.influxdb.client.InfluxDBClient;
import com.influxdb.client.InfluxDBClientFactory;
import com.influxdb.client.DeleteApi;
import com.influxdb.client.QueryApi;
import com.influxdb.client.WriteApiBlocking;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
@EnableConfigurationProperties(InfluxDbProperties.class)
public class InfluxDbConfig {

    @Bean
    public InfluxDBClient influxDBClient(InfluxDbProperties properties) {
        return InfluxDBClientFactory.create(
                properties.getUrl(),
                properties.getToken().toCharArray(),
                properties.getOrg(),
                properties.getBucket());
    }

    @Bean
    public WriteApiBlocking writeApiBlocking(InfluxDBClient influxDBClient) {
        return influxDBClient.getWriteApiBlocking();
    }

    @Bean
    public QueryApi queryApi(InfluxDBClient influxDBClient) {
        return influxDBClient.getQueryApi();
    }

    @Bean
    public DeleteApi deleteApi(InfluxDBClient influxDBClient) {
        return influxDBClient.getDeleteApi();
    }

    @Bean
    public ApplicationRunner ensureMinuteBuckets(
            InfluxDBClient influxDBClient,
            InfluxDbProperties properties) {
        return args -> {
            for (String bucketName : minuteBuckets(properties)) {
                try {
                    if (influxDBClient.getBucketsApi().findBucketByName(bucketName) == null) {
                        influxDBClient.getBucketsApi().createBucket(bucketName, properties.getOrg());
                        log.info("Created InfluxDB minute bucket: {}", bucketName);
                    }
                } catch (Exception ex) {
                    log.warn("Failed to ensure InfluxDB minute bucket: {}", bucketName, ex);
                }
            }
        };
    }

    private static Set<String> minuteBuckets(InfluxDbProperties properties) {
        Set<String> buckets = new LinkedHashSet<>();
        addBucket(buckets, blankToDefault(properties.getStockMinuteBucket(), properties.getBucket()));
        addBucket(buckets, blankToDefault(properties.getIndexMinuteBucket(), "index_intraday"));
        addBucket(buckets, blankToDefault(properties.getBondMinuteBucket(), "bond_intraday"));
        return buckets;
    }

    private static void addBucket(Set<String> buckets, String bucketName) {
        if (!isBlank(bucketName)) {
            buckets.add(bucketName);
        }
    }

    private static String blankToDefault(String value, String defaultValue) {
        return isBlank(value) ? defaultValue : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}

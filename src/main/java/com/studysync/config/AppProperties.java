package com.studysync.config;

import org.springframework.boot.context.properties.ConfigurationProperties;


/**
 * Configuration properties for application-specific settings.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
    DataSourceProperties datasource,
    FeatureProperties features
) {
    
    public record DataSourceProperties(
        int maximumPoolSize,
        int minimumIdle,
        long connectionTimeout,
        long idleTimeout,
        long maxLifetime,
        long leakDetectionThreshold
    ) {
        public DataSourceProperties {
            if (maximumPoolSize <= 0) {
                throw new IllegalArgumentException("Maximum pool size must be positive");
            }
            if (minimumIdle < 0) {
                throw new IllegalArgumentException("Minimum idle cannot be negative");
            }
        }
    }
    
    public record FeatureProperties(
        boolean debugMode,
        boolean performanceMonitoring
    ) {}
}
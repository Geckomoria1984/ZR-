package com.example.groupdashboard;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dashboard.database")
public record DashboardDatabaseProperties(boolean enabled, boolean importOnStartup) {
}

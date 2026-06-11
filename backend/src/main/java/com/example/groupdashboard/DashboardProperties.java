package com.example.groupdashboard;

import java.nio.file.Path;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dashboard")
public record DashboardProperties(Path excelPath, Path photoDir) {
}

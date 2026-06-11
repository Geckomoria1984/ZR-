package com.example.groupdashboard;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "dashboard.cors")
public record CorsProperties(List<String> allowedOrigins) {
  public CorsProperties {
    if (allowedOrigins == null || allowedOrigins.isEmpty()) {
      allowedOrigins = List.of(
          "http://localhost:5173",
          "http://127.0.0.1:5173",
          "http://localhost:5174",
          "http://127.0.0.1:5174");
    }
  }
}

package com.example.groupdashboard;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class GroupDashboardApplication {
  public static void main(String[] args) {
    SpringApplication.run(GroupDashboardApplication.class, args);
  }
}

package com.example.groupdashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
  private final ExcelDashboardService dashboardService;

  public DashboardController(ExcelDashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  @GetMapping("/api/dashboard")
  public Map<String, Object> dashboard() {
    return dashboardService.dashboard();
  }
}

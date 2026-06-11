package com.example.groupdashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DashboardController {
  private final ExcelDashboardService dashboardService;
  private final AdminExcelImportService adminExcelImportService;

  public DashboardController(
      ExcelDashboardService dashboardService,
      AdminExcelImportService adminExcelImportService) {
    this.dashboardService = dashboardService;
    this.adminExcelImportService = adminExcelImportService;
  }

  @GetMapping("/api/dashboard")
  public Map<String, Object> dashboard() {
    return dashboardService.dashboard();
  }

  @GetMapping("/api/dashboard/hidden-investors")
  public Map<String, Object> hiddenInvestorsDashboard() {
    return adminExcelImportService.hiddenInvestorDashboard();
  }

  @GetMapping("/api/dashboard/hidden-investors/people")
  public Map<String, Object> hiddenInvestorPeople(@RequestParam Map<String, String> params) {
    return adminExcelImportService.hiddenInvestorPeople(params);
  }
}

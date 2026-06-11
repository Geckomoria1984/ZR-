package com.example.groupdashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/imports")
public class AdminExcelImportController {
  private final AdminExcelImportService importService;

  public AdminExcelImportController(AdminExcelImportService importService) {
    this.importService = importService;
  }

  @PostMapping("/related-people/import-excel")
  public Map<String, Object> importRelatedPeople(@RequestParam("file") MultipartFile file) {
    return importService.importExcel(AdminExcelImportService.ImportType.RELATED_PEOPLE, file);
  }

  @PostMapping("/hidden-investors/import-excel")
  public Map<String, Object> importHiddenInvestors(@RequestParam("file") MultipartFile file) {
    return importService.importExcel(AdminExcelImportService.ImportType.HIDDEN_INVESTORS, file);
  }

  @PostMapping("/added-people/import-excel")
  public Map<String, Object> importAddedPeople(@RequestParam("file") MultipartFile file) {
    return importService.importExcel(AdminExcelImportService.ImportType.ADDED_PEOPLE, file);
  }
}

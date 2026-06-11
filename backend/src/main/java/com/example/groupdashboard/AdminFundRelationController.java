package com.example.groupdashboard;

import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/fund-relations")
public class AdminFundRelationController {
  private final AdminPeopleService peopleService;
  private final DashboardFundRelationService fundRelationService;

  public AdminFundRelationController(
      AdminPeopleService peopleService,
      DashboardFundRelationService fundRelationService) {
    this.peopleService = peopleService;
    this.fundRelationService = fundRelationService;
  }

  @PostMapping("/import-excel")
  public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
    return fundRelationService.importExcel(file);
  }

  @GetMapping("/person/{id}")
  public Map<String, Object> personGraph(@PathVariable String id) {
    return fundRelationService.graphForPerson(peopleService.get(id));
  }

  @GetMapping("/identity")
  public Map<String, Object> identityGraph(
      @RequestParam(value = "idNumber", required = false) String idNumber,
      @RequestParam(value = "name", required = false) String name) {
    Map<String, Object> person = peopleService.findByIdentity(idNumber, name).orElseGet(() -> Map.of(
        "id", "fund-" + String.valueOf(idNumber == null || idNumber.isBlank() ? name : idNumber),
        "name", String.valueOf(name == null || name.isBlank() ? "资金关系人" : name),
        "idNumber", String.valueOf(idNumber == null ? "" : idNumber),
        "risk", "资金关系人",
        "group", "hidden",
        "gender", "未填写",
        "age", 0,
        "amount", "未填写",
        "occupation", "未填写",
        "hiddenInvestor", "无"));
    return fundRelationService.graphForIdentity(person);
  }
}

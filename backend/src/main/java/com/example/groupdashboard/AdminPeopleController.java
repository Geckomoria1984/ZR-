package com.example.groupdashboard;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/admin/people")
public class AdminPeopleController {
  private final AdminPeopleService peopleService;

  public AdminPeopleController(AdminPeopleService peopleService) {
    this.peopleService = peopleService;
  }

  @GetMapping
  public Map<String, Object> people(
      @RequestParam(defaultValue = "") String name,
      @RequestParam(defaultValue = "") String idNumber,
      @RequestParam(defaultValue = "") String gender,
      @RequestParam(defaultValue = "") String risk,
      @RequestParam(defaultValue = "") String amountBucket,
      @RequestParam(defaultValue = "") String locality,
      @RequestParam(defaultValue = "") String province,
      @RequestParam(defaultValue = "") String region,
      @RequestParam(required = false) Integer age,
      @RequestParam(defaultValue = "1") int page,
      @RequestParam(defaultValue = "20") int size) {
    return peopleService.list(name, idNumber, gender, risk, amountBucket, locality, province, region, age, page, size);
  }

  @GetMapping("/{id}")
  public Map<String, Object> person(@PathVariable String id) {
    return peopleService.get(id);
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  public Map<String, Object> create(@RequestBody Map<String, Object> payload) {
    return peopleService.create(payload);
  }

  @PostMapping("/import-excel")
  public Map<String, Object> importExcel(@RequestParam("file") MultipartFile file) {
    return peopleService.importExcel(file);
  }

  @PutMapping("/{id}")
  public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> payload) {
    return peopleService.update(id, payload);
  }

  @DeleteMapping("/{id}")
  @ResponseStatus(HttpStatus.NO_CONTENT)
  public void delete(@PathVariable String id) {
    peopleService.delete(id);
  }
}

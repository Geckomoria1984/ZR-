package com.example.groupdashboard;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = {
        "spring.datasource.url=jdbc:h2:mem:group-dashboard-test;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=0",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.datasource.driver-class-name=org.h2.Driver"
    })
class AdminPeopleControllerTest {
  @LocalServerPort
  int port;

  @Autowired
  TestRestTemplate rest;

  @Autowired
  JdbcTemplate jdbcTemplate;

  @Test
  void createsUpdatesAndDeletesPeople() {
    String baseUrl = "http://localhost:" + port + "/api/admin/people";
    Map<String, Object> createRequest = Map.of(
        "name", "测试人员",
        "idNumber", "230100199001010011",
        "gender", "男",
        "age", 36,
        "district", "南岗",
        "risk", "组织串联",
        "group", "organizers",
        "phone", "13900000000",
        "amount", "900万");

    ResponseEntity<Map> created = rest.postForEntity(baseUrl, createRequest, Map.class);

    assertThat(created.getStatusCode().is2xxSuccessful()).isTrue();
    String id = String.valueOf(created.getBody().get("id"));
    assertThat(id).isNotBlank();

    ResponseEntity<Map> updated = rest.exchange(
        baseUrl + "/" + id,
        HttpMethod.PUT,
        new org.springframework.http.HttpEntity<>(Map.of("name", "测试人员已编辑", "age", 37)),
        Map.class);

    assertThat(updated.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(updated.getBody().get("name")).isEqualTo("测试人员已编辑");
    assertThat(updated.getBody().get("age")).isEqualTo(37);

    ResponseEntity<Map> list = rest.getForEntity(baseUrl + "?name=测试人员已编辑", Map.class);

    assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(list.getBody().get("total")).isEqualTo(1);

    ResponseEntity<Void> deleted = rest.exchange(baseUrl + "/" + id, HttpMethod.DELETE, null, Void.class);

    assertThat(deleted.getStatusCode().is2xxSuccessful()).isTrue();
    ResponseEntity<Map> afterDelete = rest.getForEntity(baseUrl + "?name=测试人员已编辑", Map.class);
    assertThat(afterDelete.getBody().get("total")).isEqualTo(0);
  }

  @Test
  void listReturnsExcelHeadersAndRawFieldsForAdminTable() {
    String baseUrl = "http://localhost:" + port + "/api/admin/people";

    ResponseEntity<Map> list = rest.getForEntity(baseUrl + "?size=1", Map.class);

    assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
    List<Map<String, Object>> headers = (List<Map<String, Object>>) list.getBody().get("headers");
    assertThat(headers)
        .extracting(header -> header.get("label"))
        .contains(
            "序号",
            "死亡情况",
            "持有中融信托产品份额总数",
            "丈夫|妻子、XXX、身份证、电话、职业",
            "得分");

    List<Map<String, Object>> rows = (List<Map<String, Object>>) list.getBody().get("rows");
    Map<String, Object> row = rows.get(0);
    Map<String, Object> excelFields = (Map<String, Object>) rows.get(0).get("excelFields");
    assertThat(excelFields).containsKeys("excel_0", "excel_1", "excel_14", "excel_93");
    assertThat(excelFields.get("excel_2")).isEqualTo(row.get("name"));
    assertThat(excelFields.get("excel_3")).isEqualTo(row.get("idNumber"));
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_person", Integer.class)).isGreaterThan(0);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_excel_column", Integer.class)).isGreaterThan(90);
  }

  @Test
  void listCanFilterPeopleByRegionForDashboardChips() {
    String baseUrl = "http://localhost:" + port + "/api/admin/people";

    ResponseEntity<Map> dashboard = rest.getForEntity("http://localhost:" + port + "/api/dashboard", Map.class);
    assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
    List<List<List<Object>>> regionRows = (List<List<List<Object>>>) dashboard.getBody().get("regionRows");
    int dashboardCount = regionRows.get(0).stream()
        .filter(row -> "南岗".equals(row.get(0)))
        .map(row -> ((Number) row.get(1)).intValue())
        .findFirst()
        .orElse(0);

    ResponseEntity<Map> list = rest.getForEntity(baseUrl + "?region=南岗&size=1000", Map.class);

    assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat((Integer) list.getBody().get("total")).isEqualTo(dashboardCount);
    List<Map<String, Object>> rows = (List<Map<String, Object>>) list.getBody().get("rows");
    assertThat(rows).isNotEmpty();
    assertThat(rows).allSatisfy(row -> assertThat(String.valueOf(row.getOrDefault("district", ""))).isEqualTo("南岗"));
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  void uploadingExcelReplacesDatabaseRowsAndRefreshesAdminList() throws Exception {
    String baseUrl = "http://localhost:" + port + "/api/admin/people";
    rest.getForEntity(baseUrl + "?size=1", Map.class);

    ByteArrayResource excel = new ByteArrayResource(importWorkbook()) {
      @Override
      public String getFilename() {
        return "导入测试.xlsx";
      }
    };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", excel);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<Map> imported = rest.postForEntity(
        baseUrl + "/import-excel",
        new HttpEntity<>(body, headers),
        Map.class);

    assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(imported.getBody().get("imported")).isEqualTo(214);

    ResponseEntity<Map> list = rest.getForEntity(baseUrl + "?name=导入测试人员&size=10", Map.class);
    assertThat(list.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(list.getBody().get("total")).isEqualTo(210);
    List<Map<String, Object>> rows = (List<Map<String, Object>>) list.getBody().get("rows");
    assertThat(rows.get(0).get("idNumber")).isEqualTo("230100199001010001");
    assertThat(rows.get(0).get("criminalRecord")).isEqualTo("有前科记录");
    assertThat(rows.get(0).get("policeStation")).isEqualTo("南岗分局测试派出所");

    ResponseEntity<Map> generalPeople = rest.getForEntity(baseUrl + "?risk=一般参与&size=1", Map.class);
    assertThat(generalPeople.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(generalPeople.getBody().get("total")).isEqualTo(209);
    ResponseEntity<Map> watchPeople = rest.getForEntity(baseUrl + "?risk=密切关注&size=1", Map.class);
    assertThat(watchPeople.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(watchPeople.getBody().get("total")).isEqualTo(1);

    ResponseEntity<Map> amountBucket = rest.getForEntity(baseUrl + "?amountBucket=lt300&size=10", Map.class);
    assertThat(amountBucket.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(amountBucket.getBody().get("total")).isEqualTo(209);
    ResponseEntity<Map> threeMillionBucket = rest.getForEntity(baseUrl + "?amountBucket=300-500&size=10", Map.class);
    assertThat(threeMillionBucket.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(threeMillionBucket.getBody().get("total")).isEqualTo(1);
    ResponseEntity<Map> highAmountBucket = rest.getForEntity(baseUrl + "?amountBucket=gte10000&size=10", Map.class);
    assertThat(highAmountBucket.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(highAmountBucket.getBody().get("total")).isEqualTo(4);

    ResponseEntity<Map> provinceHighAmountBucket = rest.getForEntity(baseUrl + "?amountBucket=gte10000&province=本省&size=10", Map.class);
    assertThat(provinceHighAmountBucket.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(provinceHighAmountBucket.getBody().get("total")).isEqualTo(2);

    ResponseEntity<Map> dashboard = rest.getForEntity("http://localhost:" + port + "/api/dashboard", Map.class);
    assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
    List<Map<String, Object>> amountBuckets = (List<Map<String, Object>>) dashboard.getBody().get("amountBuckets");
    assertThat(amountBuckets)
        .filteredOn(bucket -> "gte10000".equals(bucket.get("key")))
        .singleElement()
        .extracting(bucket -> bucket.get("count"))
        .isEqualTo(2);
    List<List<List<Object>>> regionRows = (List<List<List<Object>>>) dashboard.getBody().get("regionRows");
    assertThat(regionRows.get(0)).contains(List.of("南岗", 211));
    assertThat(regionRows.get(1)).contains(List.of("齐齐哈尔", 1));
    assertThat(regionRows.get(1)).doesNotContain(List.of("山东", 1));
    assertThat(regionRows.get(0)).doesNotContain(List.of("道里", 1));

    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_person", Integer.class)).isEqualTo(214);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_excel_column", Integer.class)).isGreaterThan(10);
  }

  @Test
  @DirtiesContext(methodMode = DirtiesContext.MethodMode.AFTER_METHOD)
  void dashboardAlwaysIncludesAllOrganizerAndResponderPeopleInHomePayload() throws Exception {
    String peopleUrl = "http://localhost:" + port + "/api/admin/people";
    ByteArrayResource excel = new ByteArrayResource(primaryGroupOverflowWorkbook()) {
      @Override
      public String getFilename() {
        return "重点人员完整展示.xlsx";
      }
    };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", excel);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<Map> imported = rest.postForEntity(
        peopleUrl + "/import-excel",
        new HttpEntity<>(body, headers),
        Map.class);

    assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(imported.getBody().get("imported")).isEqualTo(217);

    ResponseEntity<Map> dashboard = rest.getForEntity("http://localhost:" + port + "/api/dashboard", Map.class);

    assertThat(dashboard.getStatusCode().is2xxSuccessful()).isTrue();
    List<Map<String, Object>> people = (List<Map<String, Object>>) dashboard.getBody().get("people");
    assertThat(people).hasSize(206);
    assertThat(people)
        .filteredOn(person -> "organizers".equals(person.get("group")))
        .extracting(person -> person.get("name"))
        .containsExactly("组织串联完整人员211", "组织串联完整人员212", "组织串联完整人员213");
    assertThat(people)
        .filteredOn(person -> "responders".equals(person.get("group")))
        .extracting(person -> person.get("name"))
        .containsExactly(
            "活跃响应完整人员214",
            "活跃响应完整人员215",
            "活跃响应完整人员216",
            "活跃响应完整人员217");
  }

  @Test
  void importingFundRelationExcelStoresRowsAndReturnsPersonGraph() throws Exception {
    String peopleUrl = "http://localhost:" + port + "/api/admin/people";
    ResponseEntity<Map> firstList = rest.getForEntity(peopleUrl + "?size=1", Map.class);
    List<Map<String, Object>> firstPeople = (List<Map<String, Object>>) firstList.getBody().get("rows");
    Map<String, Object> targetPerson = firstPeople.get(0);
    String targetName = String.valueOf(targetPerson.get("name"));
    String targetIdNumber = String.valueOf(targetPerson.get("idNumber"));
    ByteArrayResource excel = new ByteArrayResource(fundRelationWorkbook(targetName, targetIdNumber)) {
      @Override
      public String getFilename() {
        return "资金关系.xlsx";
      }
    };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", excel);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<Map> imported = rest.postForEntity(
        "http://localhost:" + port + "/api/admin/fund-relations/import-excel",
        new HttpEntity<>(body, headers),
        Map.class);

    assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(imported.getBody().get("imported")).isEqualTo(2);
    assertThat(imported.getBody().get("columns")).isEqualTo(5);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_fund_relation", Integer.class)).isEqualTo(2);

    String personId = String.valueOf(targetPerson.get("id"));
    ResponseEntity<Map> graph = rest.getForEntity(
        "http://localhost:" + port + "/api/admin/fund-relations/person/" + personId,
        Map.class);

    assertThat(graph.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(graph.getBody().get("total")).isEqualTo(1);
    assertThat((List<Map<String, Object>>) graph.getBody().get("headers"))
        .extracting(header -> header.get("label"))
        .contains("付款方", "收款方", "金额");
    List<Map<String, Object>> rows = (List<Map<String, Object>>) graph.getBody().get("rows");
    Map<String, Object> fields = (Map<String, Object>) rows.get(0).get("fields");
    assertThat(fields).containsValue(targetName);
    assertThat(fields).containsValue("500000");
    Map<String, Object> graphData = (Map<String, Object>) graph.getBody().get("graph");
    assertThat((List<Map<String, Object>>) graphData.get("nodes"))
        .extracting(node -> node.get("label"))
        .contains(targetName, "中融产品账户");
    assertThat((List<Map<String, Object>>) graphData.get("edges"))
        .extracting(edge -> edge.get("amount"))
        .contains("向上层投资金额：50万");
  }

  @Test
  void layeredFundRelationGraphOnlyUsesCurrentVisibleInvestorAndAdjacentLayers() throws Exception {
    String peopleUrl = "http://localhost:" + port + "/api/admin/people";
    ResponseEntity<Map> list = rest.getForEntity(peopleUrl + "?size=2", Map.class);
    List<Map<String, Object>> people = (List<Map<String, Object>>) list.getBody().get("rows");
    Map<String, Object> targetPerson = people.get(0);
    Map<String, Object> otherPerson = people.get(1);
    String targetName = String.valueOf(targetPerson.get("name"));
    String targetIdNumber = String.valueOf(targetPerson.get("idNumber"));
    String otherName = String.valueOf(otherPerson.get("name"));
    String otherIdNumber = String.valueOf(otherPerson.get("idNumber"));
    ByteArrayResource excel = new ByteArrayResource(layeredFundRelationWorkbook(targetName, targetIdNumber, otherName, otherIdNumber)) {
      @Override
      public String getFilename() {
        return "层级资金关系.xlsx";
      }
    };
    MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
    body.add("file", excel);
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.MULTIPART_FORM_DATA);

    ResponseEntity<Map> imported = rest.postForEntity(
        "http://localhost:" + port + "/api/admin/fund-relations/import-excel",
        new HttpEntity<>(body, headers),
        Map.class);

    assertThat(imported.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(imported.getBody().get("imported")).isEqualTo(4);

    String personId = String.valueOf(targetPerson.get("id"));
    ResponseEntity<Map> graph = rest.getForEntity(
        "http://localhost:" + port + "/api/admin/fund-relations/person/" + personId,
        Map.class);

    assertThat(graph.getStatusCode().is2xxSuccessful()).isTrue();
    assertThat(graph.getBody().get("total")).isEqualTo(3);
    Map<String, Object> graphData = (Map<String, Object>) graph.getBody().get("graph");
    List<Map<String, Object>> nodes = (List<Map<String, Object>>) graphData.get("nodes");
    assertThat(nodes)
        .filteredOn(node -> "显名投资人".equals(node.get("level")))
        .extracting(node -> node.get("fullLabel"))
        .containsExactly(targetName);
    assertThat(nodes)
        .extracting(node -> node.get("fullLabel"))
        .doesNotContain(otherName);

    Map<Object, Object> nodeLevels = nodes.stream()
        .collect(java.util.stream.Collectors.toMap(node -> node.get("id"), node -> node.get("level")));
    List<Map<String, Object>> edges = (List<Map<String, Object>>) graphData.get("edges");
    assertThat(edges)
        .extracting(edge -> edge.get("amount"))
        .contains("向上层投资金额：10万", "向上层投资金额：30万")
        .doesNotContain("向上层投资金额：20万");
    assertThat(edges).allSatisfy(edge ->
        assertThat(nodeLevels.get(edge.get("source"))).isNotEqualTo(nodeLevels.get(edge.get("target"))));
  }

  private byte[] importWorkbook() throws Exception {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      Row header = sheet.createRow(0);
      List<String> columns = List.of(
          "序号",
          "姓名",
          "身份证号",
          "性别",
          "年龄",
          "风险级别",
          "省内人员简易户籍",
          "属地",
          "联系电话",
          "前科累计情况",
          "ZR被处置打击人员",
          "属地派出所",
          "持有中融信托产品份额总数");
      for (int index = 0; index < columns.size(); index++) {
        header.createCell(index).setCellValue(columns.get(index));
      }
      for (int rowIndex = 1; rowIndex <= 210; rowIndex++) {
        Row row = sheet.createRow(rowIndex);
        row.createCell(0).setCellValue(String.valueOf(rowIndex));
        row.createCell(1).setCellValue("导入测试人员" + rowIndex);
        row.createCell(2).setCellValue("23010019900101" + String.format("%04d", rowIndex));
        row.createCell(3).setCellValue("男");
        row.createCell(4).setCellValue("42");
        row.createCell(5).setCellValue(rowIndex == 1 ? "三级" : rowIndex == 2 ? "四级" : "一般参与");
        row.createCell(6).setCellValue("南岗");
        row.createCell(7).setCellValue("南岗");
        row.createCell(8).setCellValue("13900000099");
        row.createCell(9).setCellValue("有前科记录");
        row.createCell(10).setCellValue("已处置");
        row.createCell(11).setCellValue("南岗分局测试派出所");
        row.createCell(12).setCellValue(rowIndex == 210 ? "300万" : "800000");
      }
      Row unclassified = sheet.createRow(211);
      unclassified.createCell(0).setCellValue("211");
      unclassified.createCell(1).setCellValue("未分级导入人员");
      unclassified.createCell(2).setCellValue("230100199001012111");
      unclassified.createCell(3).setCellValue("女");
      unclassified.createCell(4).setCellValue("40");
      unclassified.createCell(5).setCellValue("");
      unclassified.createCell(6).setCellValue("南岗");
      unclassified.createCell(7).setCellValue("南岗");
      unclassified.createCell(8).setCellValue("13900002111");
      unclassified.createCell(11).setCellValue("南岗分局测试派出所");
      unclassified.createCell(12).setCellValue("120000000");
      Row qiqihar = sheet.createRow(212);
      qiqihar.createCell(0).setCellValue("212");
      qiqihar.createCell(1).setCellValue("齐齐哈尔高额人员");
      qiqihar.createCell(2).setCellValue("230200199001012112");
      qiqihar.createCell(3).setCellValue("男");
      qiqihar.createCell(4).setCellValue("41");
      qiqihar.createCell(5).setCellValue("");
      qiqihar.createCell(6).setCellValue("齐齐哈尔");
      qiqihar.createCell(7).setCellValue("齐齐哈尔");
      qiqihar.createCell(8).setCellValue("13900002112");
      qiqihar.createCell(11).setCellValue("齐齐哈尔测试派出所");
      qiqihar.createCell(12).setCellValue("120000000");
      Row shandong = sheet.createRow(213);
      shandong.createCell(0).setCellValue("213");
      shandong.createCell(1).setCellValue("山东高额人员");
      shandong.createCell(2).setCellValue("370100199001012113");
      shandong.createCell(3).setCellValue("女");
      shandong.createCell(4).setCellValue("39");
      shandong.createCell(5).setCellValue("");
      shandong.createCell(6).setCellValue("山东");
      shandong.createCell(7).setCellValue("山东");
      shandong.createCell(8).setCellValue("13900002113");
      shandong.createCell(11).setCellValue("山东测试派出所");
      shandong.createCell(12).setCellValue("120000000");
      Row addressOnlyHarbin = sheet.createRow(214);
      addressOnlyHarbin.createCell(0).setCellValue("214");
      addressOnlyHarbin.createCell(1).setCellValue("地址哈尔滨人员");
      addressOnlyHarbin.createCell(2).setCellValue("230100199001012114");
      addressOnlyHarbin.createCell(3).setCellValue("女");
      addressOnlyHarbin.createCell(4).setCellValue("39");
      addressOnlyHarbin.createCell(5).setCellValue("");
      addressOnlyHarbin.createCell(6).setCellValue("黑龙江");
      addressOnlyHarbin.createCell(7).setCellValue("道里区测试街1号");
      addressOnlyHarbin.createCell(8).setCellValue("13900002114");
      addressOnlyHarbin.createCell(11).setCellValue("道里测试派出所");
      addressOnlyHarbin.createCell(12).setCellValue("120000000");
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] primaryGroupOverflowWorkbook() throws Exception {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      Row header = sheet.createRow(0);
      List<String> columns = List.of(
          "序号",
          "姓名",
          "身份证号",
          "性别",
          "年龄",
          "风险级别",
          "省内人员简易户籍",
          "属地",
          "持有中融信托产品份额总数");
      for (int index = 0; index < columns.size(); index++) {
        header.createCell(index).setCellValue(columns.get(index));
      }
      for (int rowIndex = 1; rowIndex <= 217; rowIndex++) {
        Row row = sheet.createRow(rowIndex);
        String risk = rowIndex <= 210 ? "一般参与" : rowIndex <= 213 ? "组织串联" : "活跃响应";
        String namePrefix = rowIndex <= 210 ? "一般参与预览人员" : rowIndex <= 213 ? "组织串联完整人员" : "活跃响应完整人员";
        row.createCell(0).setCellValue(String.valueOf(rowIndex));
        row.createCell(1).setCellValue(namePrefix + rowIndex);
        row.createCell(2).setCellValue("23010019900102" + String.format("%04d", rowIndex));
        row.createCell(3).setCellValue("男");
        row.createCell(4).setCellValue("42");
        row.createCell(5).setCellValue(risk);
        row.createCell(6).setCellValue("南岗");
        row.createCell(7).setCellValue("南岗");
        row.createCell(8).setCellValue("800000");
      }
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] fundRelationWorkbook(String targetName, String targetIdNumber) throws Exception {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      Row header = sheet.createRow(0);
      List<String> columns = List.of("付款方", "付款方身份证号", "收款方", "金额", "关系说明");
      for (int index = 0; index < columns.size(); index++) {
        header.createCell(index).setCellValue(columns.get(index));
      }
      Row first = sheet.createRow(1);
      first.createCell(0).setCellValue(targetName);
      first.createCell(1).setCellValue(targetIdNumber);
      first.createCell(2).setCellValue("中融产品账户");
      first.createCell(3).setCellValue("500000");
      first.createCell(4).setCellValue("认购资金");
      Row second = sheet.createRow(2);
      second.createCell(0).setCellValue("其他人员");
      second.createCell(1).setCellValue("110101198001010099");
      second.createCell(2).setCellValue("中融产品账户");
      second.createCell(3).setCellValue("300000");
      second.createCell(4).setCellValue("无关行");
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] layeredFundRelationWorkbook(
      String targetName,
      String targetIdNumber,
      String otherName,
      String otherIdNumber) throws Exception {
    try (Workbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      Row header = sheet.createRow(0);
      List<String> columns = List.of(
          "显名投资人姓名",
          "显名投资人证件号",
          "隐名姓名",
          "隐名证件号",
          "向显名投资金额",
          "层级",
          "上级身份证号");
      for (int index = 0; index < columns.size(); index++) {
        header.createCell(index).setCellValue(columns.get(index));
      }
      Row first = sheet.createRow(1);
      first.createCell(0).setCellValue(targetName);
      first.createCell(1).setCellValue(targetIdNumber);
      first.createCell(2).setCellValue("一层人员甲");
      first.createCell(3).setCellValue("230100198001010001");
      first.createCell(4).setCellValue("100000");
      first.createCell(5).setCellValue("1");
      first.createCell(6).setCellValue(targetIdNumber);
      Row second = sheet.createRow(2);
      second.createCell(0).setCellValue(targetName);
      second.createCell(1).setCellValue(targetIdNumber);
      second.createCell(2).setCellValue("同层人员乙");
      second.createCell(3).setCellValue("230100198001010002");
      second.createCell(4).setCellValue("200000");
      second.createCell(5).setCellValue("1");
      second.createCell(6).setCellValue("230100198001010001");
      Row third = sheet.createRow(3);
      third.createCell(0).setCellValue(targetName);
      third.createCell(1).setCellValue(targetIdNumber);
      third.createCell(2).setCellValue("二层人员丙");
      third.createCell(3).setCellValue("230100198001010003");
      third.createCell(4).setCellValue("300000");
      third.createCell(5).setCellValue("2");
      third.createCell(6).setCellValue("230100198001010001");
      Row fourth = sheet.createRow(4);
      fourth.createCell(0).setCellValue(otherName);
      fourth.createCell(1).setCellValue(otherIdNumber);
      fourth.createCell(2).setCellValue("其他显名一层");
      fourth.createCell(3).setCellValue("230100198001010004");
      fourth.createCell(4).setCellValue("400000");
      fourth.createCell(5).setCellValue("1");
      fourth.createCell(6).setCellValue(otherIdNumber);
      workbook.write(output);
      return output.toByteArray();
    }
  }
}

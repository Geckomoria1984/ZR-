package com.example.groupdashboard;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.IOUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdminExcelImportService {
  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private static final List<String> HARBIN_AREAS = List.of(
      "道里", "南岗", "道外", "平房", "松北", "香坊", "呼兰", "阿城",
      "双城", "依兰", "方正", "宾县", "巴彦", "木兰", "通河", "延寿",
      "尚志", "五常");
  private static final List<String> HEILONGJIANG_CITIES = List.of(
      "齐齐哈尔", "牡丹江", "佳木斯", "大庆", "鸡西", "双鸭山",
      "伊春", "七台河", "鹤岗", "黑河", "绥化", "大兴安岭");
  private static final List<String> CHINA_PROVINCES = List.of(
      "北京", "天津", "河北", "山西", "内蒙古", "辽宁", "吉林", "黑龙江",
      "上海", "江苏", "浙江", "安徽", "福建", "江西", "山东", "河南",
      "湖北", "湖南", "广东", "广西", "海南", "重庆", "四川", "贵州",
      "云南", "西藏", "陕西", "甘肃", "青海", "宁夏", "新疆", "台湾",
      "香港", "澳门");
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private volatile Map<String, Object> hiddenDashboardCache;
  private volatile List<Map<String, Object>> hiddenInvestorPeopleCache;
  private volatile List<Map<String, Object>> hiddenInvestorHeadersCache;

  public AdminExcelImportService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> importExcel(ImportType type, MultipartFile file) {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要导入的 " + type.label() + " Excel 文件");
    try {
      ParsedExcel parsed = parse(file.getInputStream(), type.fieldPrefix());
      ensureSchema(type);
      jdbcTemplate.update("DELETE FROM " + type.rowTable());
      jdbcTemplate.update("DELETE FROM " + type.columnTable());
      for (Map<String, Object> header : parsed.headers()) {
        jdbcTemplate.update(
            "INSERT INTO " + type.columnTable() + " (field_key, label, column_index, width) VALUES (?, ?, ?, ?)",
            header.get("key"),
            header.get("label"),
            header.get("index"),
            header.get("width"));
      }
      int storedRowIndex = 1;
      for (Map<String, Object> row : parsed.rows()) {
        jdbcTemplate.update(
            "INSERT INTO " + type.rowTable() + " (row_index, payload_json, search_text) VALUES (?, ?, ?)",
            storedRowIndex++,
            objectMapper.writeValueAsString(row),
            searchText(row));
      }
      if (type == ImportType.HIDDEN_INVESTORS) {
        clearHiddenInvestorCache();
      }
      return Map.of(
          "type", type.key(),
          "label", type.label(),
          "imported", parsed.rows().size(),
          "columns", parsed.headers().size(),
          "rowTable", type.rowTable(),
          "columnTable", type.columnTable());
    } catch (Exception exception) {
      throw new IllegalStateException("导入 " + type.label() + " Excel 失败", exception);
    }
  }

  public Map<String, Object> hiddenInvestorDashboard() {
    Map<String, Object> cached = hiddenDashboardCache;
    if (cached != null) return cached;

    List<Map<String, Object>> headers = hiddenInvestorHeadersSnapshot();
    List<Map<String, Object>> allPeople = hiddenInvestorPeopleSnapshot();
    List<Map<String, Object>> gradedPeople = allPeople.stream()
        .filter(person -> !String.valueOf(person.getOrDefault("group", "")).isBlank())
        .toList();
    List<Map<String, Object>> heilongjiangPeople = allPeople.stream()
        .filter(this::isHeilongjiangUnclassifiedHiddenInvestor)
        .toList();
    Map<String, Integer> groupCounts = new LinkedHashMap<>();
    Map<String, Map<String, Integer>> groupDistrictCounts = new LinkedHashMap<>();
    Map<String, Long> harbinCounts = new LinkedHashMap<>();
    Map<String, Long> provinceCounts = new LinkedHashMap<>();
    Map<String, Long> provinceFullCounts = new LinkedHashMap<>();
    Map<String, Long> outsideProvinceCounts = new LinkedHashMap<>();

    for (Map<String, Object> person : gradedPeople) {
      String group = String.valueOf(person.getOrDefault("group", ""));
      String district = String.valueOf(person.getOrDefault("district", ""));
      if (!group.isBlank()) {
        groupCounts.merge(group, 1, Integer::sum);
        groupDistrictCounts.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).merge(district, 1, Integer::sum);
      }
    }

    for (Map<String, Object> person : allPeople) {
      String outsideProvince = outsideProvince(person);
      if (!outsideProvince.isBlank()) {
        outsideProvinceCounts.merge(outsideProvince, 1L, Long::sum);
      }
      if (!isHeilongjiangHiddenInvestor(person)) continue;
      String city = String.valueOf(person.getOrDefault("householdCity", ""));
      String district = String.valueOf(person.getOrDefault("householdDistrict", ""));
      if (isHarbinCity(city) || isHarbinArea(district)) continue;
      String provinceCity = dashboardArea(city);
      if (isHeilongjiangNonHarbinCity(provinceCity)) {
        provinceFullCounts.merge(provinceCity, 1L, Long::sum);
      } else {
        provinceFullCounts.merge("省内未填写", 1L, Long::sum);
      }
    }

    for (Map<String, Object> person : heilongjiangPeople) {
      String city = String.valueOf(person.getOrDefault("householdCity", ""));
      String district = String.valueOf(person.getOrDefault("householdDistrict", ""));
      if (isHarbinCity(city) || isHarbinArea(district)) {
        String area = dashboardArea(district);
        harbinCounts.merge(isHarbinArea(area) ? area : "哈尔滨未填写", 1L, Long::sum);
      } else {
        String provinceCity = dashboardArea(city);
        if (isHeilongjiangNonHarbinCity(provinceCity)) {
          provinceCounts.merge(provinceCity, 1L, Long::sum);
        } else {
          provinceCounts.merge("省内未填写", 1L, Long::sum);
        }
      }
    }

    List<Map<String, Object>> previewPeople = gradedPeople.stream()
        .filter(person -> List.of("organizers", "responders").contains(String.valueOf(person.get("group"))))
        .limit(300)
        .toList();
    if (previewPeople.isEmpty()) {
      previewPeople = gradedPeople.stream().limit(80).toList();
    }

    Map<String, Object> dashboard = new LinkedHashMap<>();
    dashboard.put("scope", "hidden");
    dashboard.put("title", "隐名投资人架构图");
    dashboard.put("groups", hiddenGroups(groupCounts, groupDistrictCounts, heilongjiangPeople.size()));
    dashboard.put("people", previewPeople);
    dashboard.put("adminPeople", previewPeople);
    dashboard.put("excelColumns", headers);
    dashboard.put("regionStats", regionStats(harbinCounts, provinceCounts));
    dashboard.put("regionRows", regionRows(harbinCounts, provinceCounts));
    dashboard.put("provinceCityFullRows", provinceCityRows(provinceFullCounts));
    dashboard.put("outsideProvinceRows", outsideProvinceRows(outsideProvinceCounts));
    dashboard.put("riskBars", riskBars(groupCounts));
    dashboard.put("amountBuckets", amountBuckets(allPeople));
    dashboard.put("clinicBars", clinicBars());
    dashboard.put("source", Map.of("riskPeople", allPeople.size(), "table", ImportType.HIDDEN_INVESTORS.rowTable()));
    hiddenDashboardCache = dashboard;
    return dashboard;
  }

  public Map<String, Object> hiddenInvestorPeople(Map<String, String> params) {
    List<Map<String, Object>> headers = hiddenInvestorHeadersSnapshot();
    List<Map<String, Object>> filtered = new ArrayList<>();
    for (Map<String, Object> person : hiddenInvestorPeopleSnapshot()) {
      if (matchesHiddenInvestorFilters(person, params)) {
        filtered.add(person);
      }
    }

    int page = Math.max(1, toInt(params.getOrDefault("page", "1")));
    int size = Math.max(1, Math.min(2000, toInt(params.getOrDefault("size", "20"))));
    int from = Math.min((page - 1) * size, filtered.size());
    int to = Math.min(from + size, filtered.size());
    return Map.of(
        "headers", headers,
        "rows", filtered.subList(from, to),
        "total", filtered.size());
  }

  public Map<String, Object> relatedPeopleGraph(String name, String idNumber) {
    ImportType type = ImportType.RELATED_PEOPLE;
    ensureSchema(type);
    List<Map<String, Object>> headers = loadHeaders(type);
    Map<String, String> labelByKey = labelByKey(headers);
    List<Map<String, Object>> rows = loadRows(type);
    String targetName = firstNonBlank(name);
    String targetId = firstNonBlank(idNumber);

    Map<String, Object> primary = graphNode("primary", firstNonBlank(targetName, "当前人员"), targetId, "当前人员", "", "", 120, 180, true);
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<Map<String, Object>> edges = new ArrayList<>();
    List<Map<String, Object>> relatedRows = new ArrayList<>();
    Map<String, Boolean> seenRelated = new LinkedHashMap<>();
    nodes.add(primary);

    int index = 0;
    for (Map<String, Object> row : rows) {
      Object fieldsObject = row.get("fields");
      if (!(fieldsObject instanceof Map<?, ?> rawFields)) continue;
      Map<String, Object> fields = new LinkedHashMap<>();
      rawFields.forEach((key, value) -> fields.put(String.valueOf(key), value));
      RelatedIdentity main = relatedIdentity(fields, labelByKey, false);
      RelatedIdentity relation = relatedIdentity(fields, labelByKey, true);
      String rawRelationType = firstNonBlank(valueByAnyLabel(fields, labelByKey, List.of("关系", "关联关系", "关系类型", "关联类型")), "关联人");
      String relationType = rawRelationType;

      RelatedIdentity display = null;
      if (identityMatches(main, targetName, targetId)) {
        display = relation;
      } else if (identityMatches(relation, targetName, targetId)) {
        display = main;
        relationType = inverseRelation(rawRelationType, main);
      } else if (rowMatches(fields, targetName, targetId)) {
        display = relation.isBlank() ? main : relation;
      }
      if (display == null || display.isBlank()) continue;
      String relatedKey = relatedIdentityKey(display);
      if (!relatedKey.isBlank() && seenRelated.containsKey(relatedKey)) continue;
      if (!relatedKey.isBlank()) seenRelated.put(relatedKey, true);

      String nodeId = "related-" + index;
      int y = 86 + index * 132;
      nodes.add(graphNode(nodeId, firstNonBlank(display.name(), "未填写"), display.idNumber(), relationType, display.phone(), display.occupation(), 560, y, false));
      edges.add(Map.of("source", "primary", "target", nodeId, "relation", relationType, "rowIndex", index));
      relatedRows.add(Map.of(
          "name", firstNonBlank(display.name(), "未填写"),
          "idNumber", display.idNumber(),
          "phone", display.phone(),
          "occupation", display.occupation(),
          "relation", relationType,
          "fields", fields));
      index++;
    }

    int height = Math.max(380, 150 + Math.max(1, index) * 132);
    primary.put("y", height / 2);
    return Map.of(
        "total", relatedRows.size(),
        "rows", relatedRows,
        "summary", relatedRows.stream()
            .map(row -> firstNonBlank(String.valueOf(row.get("relation")), "关联人") + "：" + firstNonBlank(String.valueOf(row.get("name")), "未填写"))
            .toList(),
        "graph", Map.of("nodes", nodes, "edges", edges, "width", 760, "height", height));
  }

  private List<Map<String, Object>> hiddenInvestorHeadersSnapshot() {
    List<Map<String, Object>> cached = hiddenInvestorHeadersCache;
    if (cached != null) return cached;
    ImportType type = ImportType.HIDDEN_INVESTORS;
    ensureSchema(type);
    List<Map<String, Object>> headers = loadHeaders(type);
    hiddenInvestorHeadersCache = headers;
    return headers;
  }

  private List<Map<String, Object>> hiddenInvestorPeopleSnapshot() {
    List<Map<String, Object>> cached = hiddenInvestorPeopleCache;
    if (cached != null) return cached;
    synchronized (this) {
      cached = hiddenInvestorPeopleCache;
      if (cached != null) return cached;
      ImportType type = ImportType.HIDDEN_INVESTORS;
      ensureSchema(type);
      List<Map<String, Object>> headers = hiddenInvestorHeadersSnapshot();
      Map<String, String> labelByKey = labelByKey(headers);
      List<Map<String, Object>> rows = loadRows(type);
      List<Map<String, Object>> people = new ArrayList<>();
      int index = 0;
      for (Map<String, Object> row : rows) {
        people.add(hiddenInvestorPerson(row, labelByKey, index++));
      }
      hiddenInvestorPeopleCache = List.copyOf(people);
      return hiddenInvestorPeopleCache;
    }
  }

  private void clearHiddenInvestorCache() {
    hiddenDashboardCache = null;
    hiddenInvestorPeopleCache = null;
    hiddenInvestorHeadersCache = null;
  }

  private ParsedExcel parse(InputStream input, String fieldPrefix) throws IOException {
    IOUtils.setByteArrayMaxOverride(200_000_000);
    try (Workbook workbook = WorkbookFactory.create(input)) {
      Sheet sheet = workbook.getSheet("Sheet1");
      if (sheet == null) sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      List<Map<String, Object>> headers = headers(sheet.getRow(0), formatter, fieldPrefix);
      List<Map<String, Object>> rows = new ArrayList<>();
      for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        Row row = sheet.getRow(rowIndex);
        Map<String, Object> fields = new LinkedHashMap<>();
        boolean hasValue = false;
        for (Map<String, Object> header : headers) {
          String key = String.valueOf(header.get("key"));
          int index = ((Number) header.get("index")).intValue();
          String value = cellText(row, index, formatter);
          fields.put(key, value);
          if (!value.isBlank()) hasValue = true;
        }
        if (!hasValue) continue;
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("rowIndex", rowIndex);
        item.put("fields", fields);
        rows.add(item);
      }
      return new ParsedExcel(headers, rows);
    } catch (Exception exception) {
      if (exception instanceof IOException ioException) throw ioException;
      throw new IOException(exception);
    }
  }

  private List<Map<String, Object>> headers(Row row, DataFormatter formatter, String fieldPrefix) {
    List<Map<String, Object>> headers = new ArrayList<>();
    if (row == null) return headers;
    for (Cell cell : row) {
      String label = formatter.formatCellValue(cell).trim();
      if (label.isBlank()) continue;
      Map<String, Object> header = new LinkedHashMap<>();
      header.put("key", fieldPrefix + "_" + cell.getColumnIndex());
      header.put("label", label);
      header.put("index", cell.getColumnIndex());
      header.put("width", columnWidth(label));
      headers.add(header);
    }
    return headers;
  }

  private int columnWidth(String label) {
    if (label.contains("身份证") || label.contains("证件号")) return 210;
    if (label.length() >= 10) return 190;
    return 140;
  }

  private String cellText(Row row, int index, DataFormatter formatter) {
    if (row == null) return "";
    return formatter.formatCellValue(row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
  }

  private void ensureSchema(ImportType type) {
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS %s (
          field_key VARCHAR(64) NOT NULL PRIMARY KEY,
          label VARCHAR(255) NOT NULL,
          column_index INT NOT NULL,
          width INT NOT NULL
        )
        """.formatted(type.columnTable()));
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS %s (
          row_index INT NOT NULL PRIMARY KEY,
          payload_json LONGTEXT NOT NULL,
          search_text LONGTEXT NOT NULL
        )
        """.formatted(type.rowTable()));
  }

  private List<Map<String, Object>> loadHeaders(ImportType type) {
    return jdbcTemplate.query(
        "SELECT field_key, label, column_index, width FROM " + type.columnTable() + " ORDER BY column_index",
        (rs, rowNum) -> {
          Map<String, Object> header = new LinkedHashMap<>();
          header.put("key", rs.getString("field_key"));
          header.put("label", rs.getString("label"));
          header.put("index", rs.getInt("column_index"));
          header.put("width", rs.getInt("width"));
          header.put("fixed", "姓名".equals(rs.getString("label")) ? "left" : false);
          return header;
        });
  }

  private List<Map<String, Object>> loadRows(ImportType type) {
    return jdbcTemplate.query(
        "SELECT payload_json FROM " + type.rowTable() + " ORDER BY row_index",
        (rs, rowNum) -> parseRow(rs.getString("payload_json")));
  }

  private Map<String, Object> parseRow(String payload) {
    try {
      return objectMapper.readValue(payload, MAP_TYPE);
    } catch (IOException exception) {
      throw new IllegalStateException("无法读取导入数据 JSON", exception);
    }
  }

  private String searchText(Map<String, Object> row) {
    Object fields = row.get("fields");
    if (!(fields instanceof Map<?, ?> map)) return "";
    return map.values().stream()
        .map(value -> String.valueOf(value == null ? "" : value))
        .collect(Collectors.joining(" "));
  }

  private Map<String, Object> hiddenInvestorPerson(Map<String, Object> row, Map<String, String> labelByKey, int index) {
    Map<String, Object> fields = fields(row);
    String idNumber = firstNonBlank(fieldByLabels(fields, labelByKey, List.of(
        "身份证号", "证件号", "隐名证件号", "隐名投资人证件号", "出资人证件号")));
    String name = firstNonBlank(fieldByLabels(fields, labelByKey, List.of(
        "姓名", "隐名姓名", "隐名投资人姓名", "出资人姓名", "户名")), "隐名投资人" + (index + 1));
    String riskText = fieldByExactLabels(fields, labelByKey, List.of("自身等级", "自身级别", "自身层级"));
    String group = groupKey(riskText);
    String householdProvince = fieldByExactLabels(fields, labelByKey, List.of("户籍（省）", "户籍(省)", "户籍省"));
    String householdCity = fieldByExactLabels(fields, labelByKey, List.of("户籍（市）", "户籍(市)", "户籍市"));
    String householdDistrict = fieldByExactLabels(fields, labelByKey, List.of("户籍（区）", "户籍(区)", "户籍区"));
    String district = hiddenDashboardArea(householdProvince, householdCity, householdDistrict, firstNonBlank(fieldByLabels(fields, labelByKey, List.of(
        "省内人员简易户籍", "户籍地", "户籍地址", "属地", "属地划分", "地区", "所在地"))));
    double amount = toDouble(fieldByLabels(fields, labelByKey, List.of(
        "持有中融信托产品份额总数", "投资金额", "金额", "向显名投资金额", "向上一层投资金额", "实收信托")));

    Map<String, Object> person = new LinkedHashMap<>();
    person.put("id", "hidden-" + firstNonBlank(idNumber, String.valueOf(row.getOrDefault("rowIndex", index + 1))));
    person.put("idNumber", idNumber);
    person.put("name", name);
    person.put("gender", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("性别")), "未填写"));
    person.put("age", toInt(firstNonBlank(fieldByLabels(fields, labelByKey, List.of("年龄")), "0")));
    person.put("amount", moneyText(String.valueOf(amount)));
    person.put("trustShareAmount", amount);
    person.put("trustShareText", moneyText(String.valueOf(amount)));
    person.put("occupation", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("从业单位", "职业分类", "职业")), "未填写"));
    person.put("behavior", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("突出行为", "备注", "关系说明")), riskLabel(group)));
    person.put("visits", toInt(firstNonBlank(fieldByLabels(fields, labelByKey, List.of("到访次数", "来访次数")), "0")));
    person.put("policeStation", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("属地派出所", "派出所", "属地单位")), "未填写"));
    person.put("district", district);
    person.put("householdProvince", householdProvince);
    person.put("householdCity", householdCity);
    person.put("householdDistrict", householdDistrict);
    person.put("locality", isHarbinArea(district) ? "本市" : "外市");
    person.put("group", group);
    person.put("risk", riskLabel(group));
    person.put("avatarIndex", index);
    person.put("phone", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("联系电话", "手机号", "电话")), "未填写"));
    person.put("address", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("户籍地址", "联系地址", "地址")), "未填写"));
    person.put("currentAddress", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("现住址", "当前位置")), "未填写"));
    person.put("nation", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("民族")), "汉族"));
    person.put("otherInvestment", "隐名投资");
    person.put("hiddenInvestor", "有");
    person.put("visitDetail", "隐名投资人导入数据");
    person.put("onlineSpeech", "无");
    person.put("socialAccount", "未填写");
    person.put("vehicle", "无");
    person.put("libraryStatus", hiddenLibraryStatus(fields, labelByKey, group));
    person.put("policeWarning", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("公安预警", "公安预警（平台线索）")), "无"));
    person.put("relatedPerson", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("关联人", "关联人基本情况")), "未填写"));
    person.put("latestNote", firstNonBlank(fieldByLabels(fields, labelByKey, List.of("备注", "就诊情况")), "未填写"));
    person.put("photoUrl", null);
    person.put("excelFields", fields);
    person.put("fundIdentity", Map.of("idNumber", idNumber, "name", name));
    return person;
  }

  private Map<String, Object> fields(Map<String, Object> row) {
    Object rawFields = row.get("fields");
    Map<String, Object> fields = new LinkedHashMap<>();
    if (rawFields instanceof Map<?, ?> map) {
      map.forEach((key, value) -> fields.put(String.valueOf(key), value));
    }
    return fields;
  }

  private String hiddenLibraryStatus(Map<String, Object> fields, Map<String, String> labelByKey, String group) {
    String direct = firstNonBlank(
        fieldByLabels(fields, labelByKey, List.of("是否在库", "在库情况", "列库情况")));
    if (!direct.isBlank()) return direct;
    String level = firstNonBlank(
        fieldByLabels(fields, labelByKey, List.of("在库级别", "列库级别", "库内级别", "自身等级", "等级")));
    String reason = firstNonBlank(
        fieldByLabels(fields, labelByKey, List.of("列库原因", "在库原因", "入库原因", "列管原因")));
    if (!level.isBlank() && !reason.isBlank()) return level + "," + reason;
    if (!level.isBlank()) return level;
    if (!reason.isBlank()) return reason;
    return "不在库";
  }

  private Map<String, String> labelByKey(List<Map<String, Object>> headers) {
    Map<String, String> labels = new LinkedHashMap<>();
    for (Map<String, Object> header : headers) {
      labels.put(String.valueOf(header.get("key")), String.valueOf(header.get("label")));
    }
    return labels;
  }

  private String fieldByLabels(Map<String, Object> fields, Map<String, String> labelByKey, List<String> labels) {
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      String label = labelByKey.getOrDefault(entry.getKey(), "");
      if (labels.stream().anyMatch(label::contains)) {
        return String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
      }
    }
    return "";
  }

  private String fieldByExactLabels(Map<String, Object> fields, Map<String, String> labelByKey, List<String> labels) {
    for (Map.Entry<String, Object> entry : fields.entrySet()) {
      String label = labelByKey.getOrDefault(entry.getKey(), "").trim();
      if (labels.stream().anyMatch(label::equals)) {
        return String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
      }
    }
    return "";
  }

  private RelatedIdentity relatedIdentity(Map<String, Object> fields, Map<String, String> labelByKey, boolean relationSide) {
    if (relationSide) {
      RelatedIdentity afterRelation = relatedIdentityAfterRelationColumn(fields, labelByKey);
      if (!afterRelation.isBlank()) return afterRelation;
    }
    List<String> nameLabels = relationSide
        ? List.of("关联人姓名", "关系人姓名", "亲属姓名", "关联姓名")
        : List.of("主人员姓名", "主姓名", "本人姓名", "人员姓名", "投资人姓名", "显名姓名", "姓名");
    List<String> idLabels = relationSide
        ? List.of("关联人身份证", "关系人身份证", "亲属身份证", "关联身份证")
        : List.of("主人员身份证", "主身份证", "本人身份证", "人员身份证", "投资人身份证", "身份证号", "证件号码");
    List<String> phoneLabels = relationSide
        ? List.of("关联人电话", "关系人电话", "亲属电话")
        : List.of("主人员电话", "本人电话", "联系电话", "手机号", "电话");
    List<String> occupationLabels = relationSide
        ? List.of("关联人职业", "关系人职业", "亲属职业", "职业", "工作单位", "从业单位")
        : List.of("主人员职业", "本人职业", "职业", "工作单位", "从业单位");
    return new RelatedIdentity(
        valueByAnyLabel(fields, labelByKey, nameLabels),
        valueByAnyLabel(fields, labelByKey, idLabels),
        valueByAnyLabel(fields, labelByKey, phoneLabels),
        valueByAnyLabel(fields, labelByKey, occupationLabels));
  }

  private RelatedIdentity relatedIdentityAfterRelationColumn(Map<String, Object> fields, Map<String, String> labelByKey) {
    List<Map.Entry<String, Object>> entries = new ArrayList<>(fields.entrySet());
    int relationIndex = -1;
    for (int index = 0; index < entries.size(); index++) {
      String label = labelByKey.getOrDefault(entries.get(index).getKey(), "").trim();
      if (label.contains("关系") || label.contains("关联类型") || label.contains("关联关系")) {
        relationIndex = index;
        break;
      }
    }
    if (relationIndex < 0) return new RelatedIdentity("", "", "", "");

    String name = "";
    String idNumber = "";
    String phone = "";
    String occupation = "";
    for (int index = relationIndex + 1; index < Math.min(entries.size(), relationIndex + 8); index++) {
      Map.Entry<String, Object> entry = entries.get(index);
      String label = labelByKey.getOrDefault(entry.getKey(), "").trim();
      String value = String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
      if (value.isBlank()) continue;
      if (idNumber.isBlank() && (label.contains("身份证") || label.contains("证件") || looksLikeIdNumber(value))) {
        idNumber = value;
        continue;
      }
      if (phone.isBlank() && (label.contains("电话") || label.contains("手机") || looksLikePhone(value))) {
        phone = value;
        continue;
      }
      if (occupation.isBlank() && (label.contains("职业") || label.contains("工作") || label.contains("单位"))) {
        occupation = value;
        continue;
      }
      if (name.isBlank() && (label.contains("姓名") || label.contains("名称") || looksLikeChineseName(value))) {
        name = value;
      }
    }
    return new RelatedIdentity(name, idNumber, phone, occupation);
  }

  private boolean looksLikeIdNumber(String value) {
    return value.matches(".*\\d{6}\\d{8}\\d{3}[0-9Xx].*");
  }

  private boolean looksLikePhone(String value) {
    return value.matches(".*1\\d{10}.*");
  }

  private boolean looksLikeChineseName(String value) {
    return value.matches("[\\u4e00-\\u9fa5·]{2,8}");
  }

  private String valueByAnyLabel(Map<String, Object> fields, Map<String, String> labelByKey, List<String> labels) {
    for (String expected : labels) {
      for (Map.Entry<String, Object> entry : fields.entrySet()) {
        String label = labelByKey.getOrDefault(entry.getKey(), "").trim();
        if (label.contains(expected)) {
          return String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
        }
      }
    }
    return "";
  }

  private boolean identityMatches(RelatedIdentity identity, String targetName, String targetId) {
    if (identity == null) return false;
    if (!targetId.isBlank() && !identity.idNumber().isBlank() && identity.idNumber().contains(targetId)) return true;
    return !targetName.isBlank() && !identity.name().isBlank() && identity.name().contains(targetName);
  }

  private String relatedIdentityKey(RelatedIdentity identity) {
    if (identity == null) return "";
    String idNumber = firstNonBlank(identity.idNumber()).replaceAll("\\s+", "");
    if (!idNumber.isBlank()) return "id:" + idNumber;
    String name = firstNonBlank(identity.name()).replaceAll("\\s+", "");
    if (!name.isBlank()) return "name:" + name;
    String phone = firstNonBlank(identity.phone()).replaceAll("\\s+", "");
    if (!phone.isBlank()) return "phone:" + phone;
    return "";
  }

  private String inverseRelation(String relation, RelatedIdentity opposite) {
    String cleanRelation = firstNonBlank(relation, "关联人").replaceAll("\\s+", "");
    return switch (cleanRelation) {
      case "丈夫", "老公", "夫" -> "妻子";
      case "妻子", "老婆", "妻" -> "丈夫";
      case "父亲", "爸爸", "父" -> "子女";
      case "母亲", "妈妈", "母" -> "子女";
      case "儿子", "子" -> genderRelation(opposite, "父亲", "母亲", "父母");
      case "女儿", "女" -> genderRelation(opposite, "父亲", "母亲", "父母");
      case "哥哥", "弟弟" -> "兄弟";
      case "姐姐", "妹妹" -> "姐妹";
      default -> cleanRelation;
    };
  }

  private String genderRelation(RelatedIdentity identity, String maleLabel, String femaleLabel, String fallback) {
    String idNumber = firstNonBlank(identity == null ? "" : identity.idNumber()).replaceAll("\\s+", "");
    if (idNumber.matches("\\d{17}[0-9Xx]")) {
      int genderDigit = Character.digit(idNumber.charAt(16), 10);
      if (genderDigit >= 0) return genderDigit % 2 == 1 ? maleLabel : femaleLabel;
    }
    return fallback;
  }

  private boolean rowMatches(Map<String, Object> fields, String targetName, String targetId) {
    String text = searchText(Map.of("fields", fields));
    if (!targetId.isBlank() && text.contains(targetId)) return true;
    return !targetName.isBlank() && text.contains(targetName);
  }

  private Map<String, Object> graphNode(String id, String label, String idNumber, String level, String phone, String occupation, int x, int y, boolean primary) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("id", id);
    node.put("label", label);
    node.put("fullLabel", label);
    node.put("idNumber", idNumber);
    node.put("level", level);
    node.put("phone", phone);
    node.put("occupation", occupation);
    node.put("x", x);
    node.put("y", y);
    node.put("primary", primary);
    return node;
  }

  private record RelatedIdentity(String name, String idNumber, String phone, String occupation) {
    boolean isBlank() {
      return (name == null || name.isBlank())
          && (idNumber == null || idNumber.isBlank())
          && (phone == null || phone.isBlank());
    }
  }

  private Map<String, Object> hiddenGroups(
      Map<String, Integer> counts,
      Map<String, Map<String, Integer>> districtCounts,
      int otherCount) {
    Map<String, Object> groups = new LinkedHashMap<>();
    groups.put("organizers", group("一级隐名投资人", counts.getOrDefault("organizers", 0), "一级风险隐名投资人", "red", summary(districtCounts.get("organizers"))));
    groups.put("responders", group("二级隐名投资人", counts.getOrDefault("responders", 0), "二级风险隐名投资人", "yellow", summary(districtCounts.get("responders"))));
    groups.put("general", group("三级隐名投资人", counts.getOrDefault("general", 0), "三级风险隐名投资人", "blue", summary(districtCounts.get("general"))));
    groups.put("watch", group("四级隐名投资人", counts.getOrDefault("watch", 0), "四级风险隐名投资人", "teal", summary(districtCounts.get("watch"))));
    groups.put("arrived", group("到场非投资人", 0, "户籍地分布", "blue", ""));
    groups.put("hidden", group("其他隐名投资人", otherCount, "户籍地分布", "teal", ""));
    return groups;
  }

  private Map<String, Object> group(String title, int count, String subtitle, String tone, String summary) {
    return Map.of("title", title, "count", count, "subtitle", subtitle, "tone", tone, "summary", summary);
  }

  private String summary(Map<String, Integer> counts) {
    if (counts == null || counts.isEmpty()) return "";
    return counts.entrySet().stream()
        .sorted(Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder()))
        .limit(10)
        .map(entry -> entry.getKey() + entry.getValue() + "人")
        .collect(Collectors.joining("，"));
  }

  private List<List<Object>> regionStats(Map<String, Long> harbinCounts, Map<String, Long> provinceCounts) {
    List<List<Object>> rows = new ArrayList<>();
    harbinCounts.entrySet().stream().sorted(this::compareRegionCountAsc).forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue())));
    provinceCounts.entrySet().stream().sorted(this::compareRegionCountAsc).forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue())));
    return rows;
  }

  private List<List<List<Object>>> regionRows(Map<String, Long> harbinCounts, Map<String, Long> provinceCounts) {
    List<List<Object>> harbin = harbinCounts.entrySet().stream()
        .filter(entry -> isHarbinArea(entry.getKey()) || "哈尔滨未填写".equals(entry.getKey()))
        .sorted(this::compareRegionCountAsc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
    List<List<Object>> province = provinceCounts.entrySet().stream()
        .filter(entry -> isHeilongjiangNonHarbinCity(entry.getKey()) || "省内未填写".equals(entry.getKey()))
        .sorted(this::compareRegionCountAsc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
    List<List<Object>> harbinWithTotal = new ArrayList<>();
    harbinWithTotal.addAll(harbin);
    harbinWithTotal.add(List.of("合计", harbin.stream().mapToLong(row -> ((Number) row.get(1)).longValue()).sum()));
    List<List<Object>> provinceWithTotal = new ArrayList<>();
    provinceWithTotal.addAll(province);
    provinceWithTotal.add(List.of("合计", province.stream().mapToLong(row -> ((Number) row.get(1)).longValue()).sum()));
    return List.of(harbinWithTotal, provinceWithTotal);
  }

  private List<List<Object>> outsideProvinceRows(Map<String, Long> provinceCounts) {
    return provinceCounts.entrySet().stream()
        .filter(entry -> !entry.getKey().isBlank())
        .sorted(this::compareRegionCountDesc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
  }

  private int compareRegionCountDesc(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
    int byCount = Long.compare(right.getValue(), left.getValue());
    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
  }

  private List<List<Object>> provinceCityRows(Map<String, Long> provinceCounts) {
    return provinceCounts.entrySet().stream()
        .filter(entry -> isHeilongjiangNonHarbinCity(entry.getKey()) || "省内未填写".equals(entry.getKey()))
        .sorted(this::compareRegionCountAsc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
  }

  private int compareRegionCountAsc(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
    int byCount = Long.compare(left.getValue(), right.getValue());
    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
  }

  private List<Map<String, Object>> riskBars(Map<String, Integer> counts) {
    return List.of(
        Map.of("label", "一级", "values", List.of(counts.getOrDefault("organizers", 0), 0, 0, 0)),
        Map.of("label", "二级", "values", List.of(counts.getOrDefault("responders", 0), 0, 0, 0)),
        Map.of("label", "三级", "values", List.of(counts.getOrDefault("general", 0), 0, 0, 0)),
        Map.of("label", "四级", "values", List.of(counts.getOrDefault("watch", 0), 0, 0, 0)));
  }

  private List<Map<String, Object>> amountBuckets(List<Map<String, Object>> people) {
    List<AmountBucket> buckets = List.of(
        new AmountBucket("gte10000", "一亿以上", 100_000_000D, Double.POSITIVE_INFINITY),
        new AmountBucket("5000-10000", "五千万到一亿", 50_000_000D, 100_000_000D),
        new AmountBucket("3000-5000", "三千万到五千万", 30_000_000D, 50_000_000D),
        new AmountBucket("1000-3000", "一千万到三千万", 10_000_000D, 30_000_000D),
        new AmountBucket("500-1000", "五百万到一千万", 5_000_000D, 10_000_000D),
        new AmountBucket("300-500", "三百万到五百万", 3_000_000D, 5_000_000D),
        new AmountBucket("lt300", "三百万以下", 0D, 3_000_000D));
    int total = Math.max(1, people.size());
    List<Map<String, Object>> rows = new ArrayList<>();
    for (AmountBucket bucket : buckets) {
      long count = people.stream()
          .mapToDouble(person -> toDouble(String.valueOf(person.getOrDefault("trustShareAmount", "0"))))
          .filter(bucket::contains)
          .count();
      rows.add(Map.of(
          "key", bucket.key(),
          "label", bucket.label(),
          "count", count,
          "percent", Math.round(count * 10_000D / total) / 100D));
    }
    return rows;
  }

  private List<Integer> clinicBars() {
    return List.of(94, 76, 70, 64, 58, 52, 47, 42, 38, 34, 30, 27, 24, 22, 19, 17, 15, 13, 11, 10, 8, 7, 6, 5);
  }

  private boolean matchesHiddenInvestorFilters(Map<String, Object> person, Map<String, String> params) {
    String group = params.getOrDefault("group", "");
    if (!group.isBlank() && !"all".equals(group)) {
      String personGroup = String.valueOf(person.getOrDefault("group", ""));
      if ("hidden".equals(group)) {
        if (!personGroup.isBlank()) return false;
      } else if (!group.equals(personGroup)) {
        return false;
      }
    }
    String region = params.getOrDefault("region", "");
    if (!region.isBlank() && !matchesHiddenInvestorRegion(person, region)) {
      return false;
    }
    String province = params.getOrDefault("province", "");
    if (!province.isBlank() && !matchesHiddenInvestorProvince(person, province)) {
      return false;
    }
    String amountBucket = params.getOrDefault("amountBucket", "");
    if (!amountBucket.isBlank()
        && !amountBucketContains(amountBucket, toDouble(String.valueOf(person.getOrDefault("trustShareAmount", "0"))))) {
      return false;
    }
    String locality = params.getOrDefault("locality", "");
    if (!locality.isBlank() && !"all".equals(locality)
        && !locality.equals(String.valueOf(person.getOrDefault("locality", "")))) {
      return false;
    }
    String name = params.getOrDefault("name", "");
    if (!name.isBlank() && !String.valueOf(person.getOrDefault("name", "")).contains(name)) {
      return false;
    }
    String idNumber = params.getOrDefault("idNumber", "");
    return idNumber.isBlank() || String.valueOf(person.getOrDefault("idNumber", "")).contains(idNumber);
  }

  private boolean containsAny(Map<String, Object> person, String needle, String... keys) {
    for (String key : keys) {
      if (String.valueOf(person.getOrDefault(key, "")).contains(needle)) return true;
    }
    return false;
  }

  private boolean matchesHiddenInvestorRegion(Map<String, Object> person, String region) {
    if (!isHeilongjiangHiddenInvestor(person)) return false;
    String city = String.valueOf(person.getOrDefault("householdCity", ""));
    String district = String.valueOf(person.getOrDefault("householdDistrict", ""));
    String normalizedRegion = dashboardArea(region);
    if (isHarbinCity(city) || isHarbinArea(district)) {
      return normalizedRegion.equals(dashboardArea(district));
    }
    return normalizedRegion.equals(dashboardArea(city));
  }

  private boolean matchesHiddenInvestorProvince(Map<String, Object> person, String province) {
    String normalized = province.trim();
    return String.valueOf(person.getOrDefault("householdProvince", "")).contains(normalized)
        || String.valueOf(person.getOrDefault("address", "")).contains(normalized)
        || String.valueOf(person.getOrDefault("currentAddress", "")).contains(normalized);
  }

  private boolean amountBucketContains(String key, double amount) {
    return switch (key) {
      case "gte10000" -> amount >= 100_000_000D;
      case "5000-10000" -> amount >= 50_000_000D && amount < 100_000_000D;
      case "3000-5000" -> amount >= 30_000_000D && amount < 50_000_000D;
      case "1000-3000" -> amount >= 10_000_000D && amount < 30_000_000D;
      case "500-1000" -> amount >= 5_000_000D && amount < 10_000_000D;
      case "300-500" -> amount >= 3_000_000D && amount < 5_000_000D;
      case "lt300" -> amount >= 0D && amount < 3_000_000D;
      default -> false;
    };
  }

  private String groupKey(String riskText) {
    if (riskText == null || riskText.isBlank()) return "";
    String cleaned = riskText.trim();
    if (cleaned.contains("一级") || cleaned.matches(".*(^|[^一二三四0-9])(?:1级|1\\.0级?|1)(?:[^一二三四0-9]|$).*")) return "organizers";
    if (cleaned.contains("二级") || cleaned.matches(".*(^|[^一二三四0-9])(?:2级|2\\.0级?|2)(?:[^一二三四0-9]|$).*")) return "responders";
    if (cleaned.contains("三级") || cleaned.matches(".*(^|[^一二三四0-9])(?:3级|3\\.0级?|3)(?:[^一二三四0-9]|$).*")) return "general";
    if (cleaned.contains("四级") || cleaned.matches(".*(^|[^一二三四0-9])(?:4级|4\\.0级?|4)(?:[^一二三四0-9]|$).*")) return "watch";
    return "";
  }

  private String riskLabel(String group) {
    return switch (group) {
      case "organizers" -> "一级";
      case "responders" -> "二级";
      case "watch" -> "四级";
      case "arrived" -> "未分级";
      default -> "三级";
    };
  }

  private String dashboardArea(String value) {
    String harbinArea = HARBIN_AREAS.stream().filter(area -> value != null && value.contains(area)).findFirst().orElse("");
    if (!harbinArea.isBlank()) return harbinArea;
    String city = HEILONGJIANG_CITIES.stream().filter(area -> value != null && value.contains(area)).findFirst().orElse("");
    return firstNonBlank(city, value, "未填写");
  }

  private String hiddenDashboardArea(String province, String city, String district, String fallback) {
    if (isHeilongjiangProvince(province)) {
      if (isHarbinCity(city)) return dashboardArea(firstNonBlank(district, fallback));
      return dashboardArea(firstNonBlank(city, fallback));
    }
    return dashboardArea(fallback);
  }

  private boolean isHeilongjiangHiddenInvestor(Map<String, Object> person) {
    return isHeilongjiangProvince(String.valueOf(person.getOrDefault("householdProvince", "")));
  }

  private boolean isHeilongjiangUnclassifiedHiddenInvestor(Map<String, Object> person) {
    return isHeilongjiangHiddenInvestor(person)
        && String.valueOf(person.getOrDefault("group", "")).isBlank();
  }

  private boolean isHeilongjiangProvince(String province) {
    return province != null && province.contains("黑龙江");
  }

  private String outsideProvince(Map<String, Object> person) {
    String province = normalizeProvince(firstNonBlank(
        String.valueOf(person.getOrDefault("householdProvince", "")),
        provinceFromText(String.valueOf(person.getOrDefault("address", ""))),
        provinceFromText(String.valueOf(person.getOrDefault("currentAddress", "")))));
    return isKnownProvince(province) ? normalizeProvince(province) : "";
  }

  private String provinceFromText(String value) {
    String text = String.valueOf(value == null ? "" : value).trim();
    return CHINA_PROVINCES.stream().filter(text::contains).findFirst().orElse("");
  }

  private String normalizeProvince(String value) {
    String text = String.valueOf(value == null ? "" : value).trim();
    String matched = provinceFromText(text);
    if (!matched.isBlank()) return matched;
    return text.replace("省", "").replace("市", "").replace("自治区", "").replace("特别行政区", "").trim();
  }

  private boolean isOutsideProvince(String province) {
    String normalized = normalizeProvince(province);
    return !normalized.isBlank() && !"黑龙江".equals(normalized) && !"未填写".equals(normalized);
  }

  private boolean isKnownProvince(String province) {
    String normalized = normalizeProvince(province);
    return !normalized.isBlank() && !"未填写".equals(normalized) && CHINA_PROVINCES.contains(normalized);
  }

  private boolean isHarbinCity(String city) {
    return city != null && city.contains("哈尔滨");
  }

  private boolean isHarbinArea(String area) {
    if (area == null || area.isBlank() || "未填写".equals(area) || "总计".equals(area)) return false;
    return HARBIN_AREAS.stream().anyMatch(area::contains);
  }

  private boolean isHeilongjiangNonHarbinCity(String area) {
    if (area == null || area.isBlank() || "未填写".equals(area) || "总计".equals(area)) return false;
    return !isHarbinArea(area) && HEILONGJIANG_CITIES.stream().anyMatch(area::contains);
  }

  private int toInt(String value) {
    return (int) Math.round(toDouble(value));
  }

  private double toDouble(String value) {
    if (value == null || value.isBlank()) return 0;
    String text = value.trim().replace(",", "");
    double multiplier = 1D;
    if (text.contains("亿")) {
      multiplier = 100_000_000D;
    } else if (text.contains("万")) {
      multiplier = 10_000D;
    }
    try {
      String numeric = text.replaceAll("[^0-9.Ee+\\-]", "");
      if (numeric.isBlank()) return 0;
      return Double.parseDouble(numeric) * multiplier;
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private String moneyText(String value) {
    double amount = toDouble(value);
    if (amount <= 0) return "0万";
    return new DecimalFormat("#,##0.##").format(amount / 10_000D) + "万";
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.trim().isBlank()) return value.trim();
    }
    return "";
  }

  private record AmountBucket(String key, String label, double min, double max) {
    boolean contains(double amount) {
      return amount >= min && amount < max;
    }
  }

  public enum ImportType {
    RELATED_PEOPLE(
        "relatedPeople",
        "关联人",
        "related",
        "dashboard_import_related_person",
        "dashboard_import_related_person_column"),
    HIDDEN_INVESTORS(
        "hiddenInvestors",
        "隐名投资人",
        "hidden",
        "dashboard_import_hidden_investor",
        "dashboard_import_hidden_investor_column"),
    ADDED_PEOPLE(
        "addedPeople",
        "增加人员",
        "added",
        "dashboard_import_added_person",
        "dashboard_import_added_person_column");

    private final String key;
    private final String label;
    private final String fieldPrefix;
    private final String rowTable;
    private final String columnTable;

    ImportType(String key, String label, String fieldPrefix, String rowTable, String columnTable) {
      this.key = key;
      this.label = label;
      this.fieldPrefix = fieldPrefix;
      this.rowTable = rowTable;
      this.columnTable = columnTable;
    }

    public String key() {
      return key;
    }

    public String label() {
      return label;
    }

    public String fieldPrefix() {
      return fieldPrefix;
    }

    public String rowTable() {
      return rowTable;
    }

    public String columnTable() {
      return columnTable;
    }
  }

  private record ParsedExcel(List<Map<String, Object>> headers, List<Map<String, Object>> rows) {}
}

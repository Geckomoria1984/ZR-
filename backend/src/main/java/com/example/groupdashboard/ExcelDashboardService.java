package com.example.groupdashboard;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.IOUtils;
import org.springframework.stereotype.Service;

@Service
public class ExcelDashboardService {
  private static final int HOME_PERSON_LIMIT = 206;
  private static final List<String> HARBIN_AREAS = List.of(
      "道里", "南岗", "道外", "平房", "松北", "香坊", "呼兰", "阿城",
      "双城", "依兰", "方正", "宾县", "巴彦", "木兰", "通河", "延寿",
      "尚志", "五常");
  private static final List<String> HEILONGJIANG_CITIES = List.of(
      "齐齐哈尔", "牡丹江", "佳木斯", "大庆", "鸡西", "双鸭山",
      "伊春", "七台河", "鹤岗", "黑河", "绥化", "大兴安岭");
  private final DashboardProperties properties;
  private final Optional<DashboardDatabaseStore> databaseStore;
  private final AtomicReference<Map<String, Object>> cache = new AtomicReference<>();

  private record ExcelColumn(String key, String label, int index) {}

  public ExcelDashboardService(DashboardProperties properties, Optional<DashboardDatabaseStore> databaseStore) {
    this.properties = properties;
    this.databaseStore = databaseStore;
  }

  public Map<String, Object> dashboard() {
    Map<String, Object> current = cache.get();
    if (current != null) return current;

    Map<String, Object> loaded = databaseStore
        .filter(DashboardDatabaseStore::enabled)
        .map(store -> {
          try {
            return store.loadOrImport(this::loadDashboard);
          } catch (IOException exception) {
            throw new IllegalStateException("无法导入或读取数据库人员数据", exception);
          }
        })
        .orElseGet(() -> {
          try {
            return loadDashboard();
          } catch (IOException exception) {
            throw new IllegalStateException("无法读取首页 Excel 数据: " + properties.excelPath(), exception);
          }
        });
    loaded = normalizeDashboardRegions(loaded);
    cache.compareAndSet(null, loaded);
    return cache.get();
  }

  public Map<String, Object> importExcel(InputStream input) {
    Map<String, Object> dashboard;
    try {
      dashboard = loadDashboard(input, "uploaded");
      Optional<DashboardDatabaseStore> enabledStore = databaseStore.filter(DashboardDatabaseStore::enabled);
      if (enabledStore.isPresent()) {
        enabledStore.get().replace(dashboard);
      }
      cache.set(dashboard);
      return dashboard;
    } catch (IOException exception) {
      throw new IllegalStateException("无法导入上传的 Excel 数据", exception);
    }
  }

  private Map<String, Object> loadDashboard() throws IOException {
    IOUtils.setByteArrayMaxOverride(200_000_000);
    try (InputStream input = Files.newInputStream(properties.excelPath());
         Workbook workbook = WorkbookFactory.create(input)) {
      return loadDashboard(workbook, properties.excelPath().toString());
    } catch (Exception exception) {
      if (exception instanceof IOException ioException) throw ioException;
      throw new IOException(exception);
    }
  }

  private Map<String, Object> loadDashboard(InputStream input, String sourcePath) throws IOException {
    IOUtils.setByteArrayMaxOverride(200_000_000);
    try (Workbook workbook = WorkbookFactory.create(input)) {
      return loadDashboard(workbook, sourcePath);
    } catch (Exception exception) {
      if (exception instanceof IOException ioException) throw ioException;
      throw new IOException(exception);
    }
  }

  private Map<String, Object> loadDashboard(Workbook workbook, String sourcePath) {
      Sheet sheet = workbook.getSheet("Sheet1");
      if (sheet == null) sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter(Locale.CHINA);
      Map<String, Integer> headers = headers(sheet.getRow(0), formatter);
      List<ExcelColumn> excelColumns = excelColumns(sheet.getRow(0), formatter);
      List<Map<String, Object>> adminPeople = new ArrayList<>();
      Map<String, Long> harbinRegionCounts = new LinkedHashMap<>();
      Map<String, Long> provinceCityCounts = new LinkedHashMap<>();
      Map<String, Integer> groupCounts = new LinkedHashMap<>();
      Map<String, Map<String, Integer>> groupDistrictCounts = new LinkedHashMap<>();
      int blankRows = 0;

      for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
        Row row = sheet.getRow(rowIndex);
        String personId = normalizeId(text(row, headers, "身份证号", formatter));
        if (personId.isBlank()) {
          blankRows += 1;
          if (blankRows > 200 && !adminPeople.isEmpty()) break;
          continue;
        }
        blankRows = 0;

        String riskText = text(row, headers, "风险级别", formatter);
        String group = groupKey(riskText);
        if (group == null) {
          group = fallbackGroup(row, headers, formatter);
        }
        String district = dashboardArea(row, headers, formatter);
        if (isHarbinArea(district)) {
          harbinRegionCounts.merge(district, 1L, Long::sum);
        } else if (isHeilongjiangNonHarbinCity(district)) {
          provinceCityCounts.merge(district, 1L, Long::sum);
        }

        String adminGroup = group;
        Map<String, Object> person = person(row, headers, excelColumns, formatter, personId, adminGroup, district, adminPeople.size());
        adminPeople.add(person);
        if (!group.isBlank()) {
          groupCounts.merge(group, 1, Integer::sum);
          groupDistrictCounts.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).merge(district, 1, Integer::sum);
        }
      }

      return Map.of(
          "groups", groups(groupCounts, groupDistrictCounts),
          "people", homePeople(adminPeople),
          "adminPeople", adminPeople,
          "excelColumns", excelColumnPayload(excelColumns),
          "regionStats", regionStats(harbinRegionCounts, provinceCityCounts),
          "regionRows", regionRows(harbinRegionCounts, provinceCityCounts),
          "riskBars", riskBars(groupCounts),
          "amountBuckets", amountBuckets(adminPeople.stream().filter(this::isHeilongjiangPerson).toList()),
          "clinicBars", clinicBars(),
          "source", Map.of(
              "excelPath", sourcePath,
	              "photoDir", properties.photoDir().toString(),
	              "riskPeople", adminPeople.size()));
  }

  private Map<String, Object> normalizeDashboardRegions(Map<String, Object> dashboard) {
    Object rawPeople = dashboard.getOrDefault("adminPeople", dashboard.get("people"));
    if (!(rawPeople instanceof List<?> people)) return dashboard;

    Map<String, String> labelByKey = excelLabelByKey(dashboard.get("excelColumns"));
    Map<String, Long> harbinRegionCounts = new LinkedHashMap<>();
    Map<String, Long> provinceCityCounts = new LinkedHashMap<>();
    Map<String, Integer> groupCounts = new LinkedHashMap<>();
    Map<String, Map<String, Integer>> groupDistrictCounts = new LinkedHashMap<>();
    List<Map<String, Object>> adminPeople = new ArrayList<>();

    for (Object item : people) {
      if (!(item instanceof Map<?, ?> source)) continue;
      Map<String, Object> person = new LinkedHashMap<>();
      source.forEach((key, value) -> person.put(String.valueOf(key), value));
      String district = dashboardArea(excelFieldByLabel(person, labelByKey, "省内人员简易户籍"));
      person.put("district", district);
      person.put("locality", locality(district));
      String normalizedGroup = groupKey(firstNonBlank(
          excelFieldByLabel(person, labelByKey, "风险级别"),
          String.valueOf(person.getOrDefault("risk", ""))));
      if (normalizedGroup != null) {
        person.put("group", normalizedGroup);
        person.put("risk", riskLabel(normalizedGroup));
      }
      adminPeople.add(person);

      if (isHarbinArea(district)) {
        harbinRegionCounts.merge(district, 1L, Long::sum);
      } else if (isHeilongjiangNonHarbinCity(district)) {
        provinceCityCounts.merge(district, 1L, Long::sum);
      }

      String group = String.valueOf(person.getOrDefault("group", ""));
      if (!group.isBlank() && !"unclassified".equals(group)) {
        groupCounts.merge(group, 1, Integer::sum);
        groupDistrictCounts.computeIfAbsent(group, ignored -> new LinkedHashMap<>()).merge(district, 1, Integer::sum);
      }
    }

    Map<String, Object> normalized = new LinkedHashMap<>(dashboard);
    normalized.put("adminPeople", adminPeople);
    normalized.put("people", homePeople(adminPeople));
    normalized.put("groups", groups(groupCounts, groupDistrictCounts));
    normalized.put("regionStats", regionStats(harbinRegionCounts, provinceCityCounts));
    normalized.put("regionRows", regionRows(harbinRegionCounts, provinceCityCounts));
    normalized.put("riskBars", riskBars(groupCounts));
    normalized.put("amountBuckets", amountBuckets(adminPeople.stream().filter(this::isHeilongjiangPerson).toList()));
    return normalized;
  }

  private List<Map<String, Object>> homePeople(List<Map<String, Object>> adminPeople) {
    List<Map<String, Object>> priorityPeople = adminPeople.stream()
        .filter(this::isPrimaryGroup)
        .toList();
    int remaining = Math.max(0, HOME_PERSON_LIMIT - priorityPeople.size());
    List<Map<String, Object>> homePeople = new ArrayList<>(priorityPeople);
    adminPeople.stream()
        .filter(person -> isClassifiedGroup(person) && !isPrimaryGroup(person))
        .limit(remaining)
        .forEach(homePeople::add);
    return homePeople;
  }

  private boolean isPrimaryGroup(Map<String, Object> person) {
    String group = String.valueOf(person.getOrDefault("group", ""));
    return "organizers".equals(group) || "responders".equals(group);
  }

  private boolean isClassifiedGroup(Map<String, Object> person) {
    String group = String.valueOf(person.getOrDefault("group", ""));
    return !group.isBlank() && !"unclassified".equals(group);
  }

  private Map<String, String> excelLabelByKey(Object rawColumns) {
    Map<String, String> labels = new LinkedHashMap<>();
    if (rawColumns instanceof List<?> columns) {
      for (Object item : columns) {
        if (item instanceof Map<?, ?> column) {
          labels.put(String.valueOf(column.get("key") == null ? "" : column.get("key")), String.valueOf(column.get("label") == null ? "" : column.get("label")));
        }
      }
    }
    return labels;
  }

  private String excelFieldByLabel(Map<String, Object> person, Map<String, String> labelByKey, String label) {
    Object rawFields = person.get("excelFields");
    if (!(rawFields instanceof Map<?, ?> fields)) return "";
    for (Map.Entry<?, ?> entry : fields.entrySet()) {
      if (label.equals(labelByKey.get(String.valueOf(entry.getKey())))) {
        return String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
      }
    }
    return "";
  }

  private Map<String, Object> person(
      Row row,
      Map<String, Integer> headers,
      List<ExcelColumn> excelColumns,
      DataFormatter formatter,
      String personId,
      String group,
      String district,
      int index) {
    String sequence = text(row, headers, "序号", formatter);
    String rawName = text(row, headers, "姓名", formatter);
    String name = rawName.isBlank() || "姓名".equals(rawName) ? "人员" + firstNonBlank(sequence, String.valueOf(index + 1)) : rawName;
    String gender = firstNonBlank(text(row, headers, "性别", formatter), "未填写");
    String age = firstNonBlank(text(row, headers, "年龄", formatter), "0");
    String policeStation = normalizePoliceStation(firstNonBlank(
        text(row, headers, "属地派出所", formatter),
        text(row, headers, "属地互通", formatter),
        text(row, headers, "派出所", formatter)));
    String occupation = firstNonBlank(
        text(row, headers, "从业单位", formatter),
        text(row, headers, "职业分类", formatter),
        "未填写");

    Map<String, Object> person = new LinkedHashMap<>();
    person.put("id", "p" + (index + 1));
    person.put("idNumber", personId);
    person.put("name", name);
    person.put("gender", gender);
    person.put("age", toInt(age));
    String trustShare = text(row, headers, "持有中融信托产品份额总数", formatter);
    person.put("amount", moneyText(text(row, headers, "实收信托", formatter)));
    person.put("trustShareAmount", toDouble(trustShare));
    person.put("trustShareText", moneyText(trustShare));
    person.put("occupation", occupation);
    person.put("behavior", firstNonBlank(text(row, headers, "突出行为", formatter), riskLabel(group)));
    person.put("visits", toInt(firstNonBlank(text(row, headers, "到访次数", formatter), "0")));
    person.put("policeStation", policeStation);
    person.put("district", district);
    person.put("locality", locality(district));
    person.put("group", group);
    person.put("risk", riskLabel(group));
    person.put("avatarIndex", index);
    person.put("phone", firstNonBlank(text(row, headers, "联系电话", formatter), "未填写"));
    person.put("address", firstNonBlank(text(row, headers, "户籍地址", formatter), text(row, headers, "联系地址", formatter), "未填写"));
    person.put("currentAddress", firstNonBlank(text(row, headers, "现住址", formatter), "未填写"));
    person.put("nation", firstNonBlank(text(row, headers, "民族", formatter), "汉族"));
    person.put("otherInvestment", firstNonBlank(text(row, headers, "其他投资", formatter), "无"));
    person.put("hiddenInvestor", firstNonBlank(text(row, headers, "代持自然人", formatter), text(row, headers, "是否穿透（市专班提供）", formatter), "无"));
    person.put("visitDetail", visitDetail(row, headers, formatter));
    person.put("onlineSpeech", onlineSpeech(row, headers, formatter));
    person.put("socialAccount", firstNonBlank(text(row, headers, "社交平台账号", formatter), "未填写"));
    person.put("vehicle", firstNonBlank(text(row, headers, "车辆信息", formatter), "无"));
    person.put("libraryStatus", firstNonBlank(text(row, headers, "是否在库", formatter), riskLabel(group) + "，" + firstNonBlank(text(row, headers, "其他投资", formatter), "中植") + "投资"));
    person.put("policeWarning", firstNonBlank(text(row, headers, "公安预警（平台线索）", formatter), "无"));
    person.put("criminalRecord", text(row, headers, "前科累计情况", formatter));
    person.put("zrDisposal", text(row, headers, "ZR被处置打击人员", formatter));
    person.put("relatedPerson", firstNonBlank(text(row, headers, "关联人基本情况", formatter), "未填写"));
    person.put("responsiblePerson", nameWithPhone(text(row, headers, "包保派出所领导", formatter), text(row, headers, "包保派出所领导电话", formatter)));
    person.put("policeContact", nameWithPhone(text(row, headers, "包保派出所民警", formatter), text(row, headers, "包保派出所民警电话", formatter)));
    person.put("community", nameWithPhone(text(row, headers, "包保社区干部", formatter), text(row, headers, "包保社区干部电话", formatter)));
    person.put("latestNote", firstNonBlank(text(row, headers, "就诊情况", formatter), "个人信息字段来自测试 Excel"));
    person.put("photoUrl", hasPhoto(personId) ? "/api/photos/" + personId : null);
    Map<String, Object> excelFields = excelFields(row, excelColumns, formatter);
    putExcelField(excelFields, excelColumns, "姓名", name);
    putExcelField(excelFields, excelColumns, "身份证号", personId);
    person.put("excelFields", excelFields);
    return person;
  }

  private String visitDetail(Row row, Map<String, Integer> headers, DataFormatter formatter) {
    List<String> parts = new ArrayList<>();
    addCountPart(parts, "省金融监管局", text(row, headers, "到省金融监管局上访（次）", formatter), "次");
    addCountPart(parts, "北京职场", text(row, headers, "到北京职场上访（次）", formatter), "次");
    addCountPart(parts, "金融大厦", text(row, headers, "到中融大厦上访（次）", formatter), "次");
    return parts.isEmpty() ? "无" : String.join("，", parts);
  }

  private String onlineSpeech(Row row, Map<String, Integer> headers, DataFormatter formatter) {
    List<String> parts = new ArrayList<>();
    addCountPart(parts, "涉及ZR群", text(row, headers, "涉及多少个ZR群", formatter), "个");
    addCountPart(parts, "挑头", text(row, headers, "网络发声挑头数据", formatter), "次");
    addCountPart(parts, "响应", text(row, headers, "网络发声响应数据", formatter), "次");
    return parts.isEmpty() ? "无" : String.join("，", parts);
  }

  private void addCountPart(List<String> parts, String label, String value, String unit) {
    String cleaned = firstNonBlank(value);
    if (cleaned.isBlank() || "0".equals(cleaned) || "0.0".equals(cleaned)) return;
    String withoutUnit = cleaned.endsWith(unit) ? cleaned.substring(0, cleaned.length() - unit.length()) : cleaned;
    parts.add(label + withoutUnit + unit);
  }

  private String nameWithPhone(String name, String phone) {
    String cleanedName = firstNonBlank(name);
    String cleanedPhone = firstNonBlank(phone);
    if (cleanedName.isBlank() && cleanedPhone.isBlank()) return "未填写";
    if (cleanedName.isBlank()) return cleanedPhone;
    if (cleanedPhone.isBlank()) return cleanedName;
    return cleanedName + " " + cleanedPhone;
  }

  private Map<String, Object> groups(Map<String, Integer> counts, Map<String, Map<String, Integer>> districtCounts) {
    Map<String, Object> groups = new LinkedHashMap<>();
    groups.put("organizers", group("组织串联人员", counts.getOrDefault("organizers", 0), "网上串联、现场组织或到场40次以上", "red", summary(districtCounts.get("organizers"))));
    groups.put("responders", group("活跃响应人员", counts.getOrDefault("responders", 0), "到场20次以上、40次以下；群内响应、发表过极端言论或意见领袖", "yellow", summary(districtCounts.get("responders"))));
    groups.put("general", group("一般参与人员", counts.getOrDefault("general", 0), "有到场行为", "blue", summary(districtCounts.get("general"))));
    groups.put("watch", group("密切关注人员", counts.getOrDefault("watch", 0), "有过极端言论或意见领袖，未到场或仅群内响应", "teal", summary(districtCounts.get("watch"))));
    groups.put("arrived", group("到场非投资人", counts.getOrDefault("arrived", 0), "户籍地分布", "blue", ""));
    groups.put("hidden", group("隐名投资人", counts.getOrDefault("hidden", 0), "户籍地分布", "teal", ""));
    return groups;
  }

  private Map<String, Object> group(String title, int count, String subtitle, String tone, String summary) {
    return Map.of(
        "title", title,
        "count", count,
        "subtitle", subtitle,
        "tone", tone,
        "summary", summary);
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
    harbinCounts.entrySet().stream()
        .filter(entry -> !"未填写".equals(entry.getKey()) && !entry.getKey().isBlank())
        .sorted(this::compareRegionCountAsc)
        .limit(18)
        .forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue())));
    provinceCounts.entrySet().stream()
        .filter(entry -> !"未填写".equals(entry.getKey()) && !entry.getKey().isBlank())
        .sorted(this::compareRegionCountAsc)
        .limit(12)
        .forEach(entry -> rows.add(List.of(entry.getKey(), entry.getValue())));
    return rows;
  }

  private List<List<List<Object>>> regionRows(Map<String, Long> harbinCounts, Map<String, Long> provinceCounts) {
    List<List<Object>> harbin = harbinCounts.entrySet().stream()
        .filter(entry -> isHarbinArea(entry.getKey()))
        .sorted(this::compareRegionCountAsc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
    List<List<Object>> province = provinceCounts.entrySet().stream()
        .filter(entry -> isHeilongjiangNonHarbinCity(entry.getKey()))
        .sorted(this::compareRegionCountAsc)
        .map(entry -> List.of((Object) entry.getKey(), (Object) entry.getValue()))
        .toList();
    return List.of(harbin, province);
  }

  private int compareRegionCountAsc(Map.Entry<String, Long> left, Map.Entry<String, Long> right) {
    int byCount = Long.compare(left.getValue(), right.getValue());
    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
  }

  private boolean isHarbinArea(String area) {
    if (area == null || area.isBlank() || "未填写".equals(area) || "总计".equals(area)) return false;
    return HARBIN_AREAS.stream().anyMatch(area::contains);
  }

  private boolean isHeilongjiangNonHarbinCity(String area) {
    if (area == null || area.isBlank() || "未填写".equals(area) || "总计".equals(area)) return false;
    return !isHarbinArea(area) && HEILONGJIANG_CITIES.stream().anyMatch(area::contains);
  }

  private boolean isHeilongjiangPerson(Map<String, Object> person) {
    String district = String.valueOf(person.getOrDefault("district", ""));
    return isHarbinArea(district) || isHeilongjiangNonHarbinCity(district);
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
      Map<String, Object> row = new LinkedHashMap<>();
      row.put("key", bucket.key());
      row.put("label", bucket.label());
      row.put("count", count);
      row.put("percent", Math.round(count * 10_000D / total) / 100D);
      rows.add(row);
    }
    return rows;
  }

  private record AmountBucket(String key, String label, double min, double max) {
    boolean contains(double amount) {
      return amount >= min && amount < max;
    }
  }

  private List<Integer> clinicBars() {
    return List.of(94, 76, 70, 64, 58, 52, 47, 42, 38, 34, 30, 27, 24, 22, 19, 17, 15, 13, 11, 10, 8, 7, 6, 5);
  }

  private Map<String, Integer> headers(Row row, DataFormatter formatter) {
    Map<String, Integer> headers = new LinkedHashMap<>();
    for (Cell cell : row) {
      headers.put(formatter.formatCellValue(cell).trim(), cell.getColumnIndex());
    }
    return headers;
  }

  private List<ExcelColumn> excelColumns(Row row, DataFormatter formatter) {
    List<ExcelColumn> columns = new ArrayList<>();
    for (Cell cell : row) {
      String label = formatter.formatCellValue(cell).trim();
      if (label.isBlank()) continue;
      columns.add(new ExcelColumn("excel_" + cell.getColumnIndex(), label, cell.getColumnIndex()));
    }
    return columns;
  }

  private List<Map<String, Object>> excelColumnPayload(List<ExcelColumn> columns) {
    List<Map<String, Object>> payload = new ArrayList<>();
    for (ExcelColumn column : columns) {
      Map<String, Object> item = new LinkedHashMap<>();
      item.put("key", column.key());
      item.put("label", column.label());
      item.put("index", column.index());
      item.put("width", excelColumnWidth(column.label()));
      item.put("fixed", "姓名".equals(column.label()) ? "left" : false);
      payload.add(item);
    }
    return payload;
  }

  private Map<String, Object> excelFields(Row row, List<ExcelColumn> columns, DataFormatter formatter) {
    Map<String, Object> fields = new LinkedHashMap<>();
    for (ExcelColumn column : columns) {
      fields.put(column.key(), cellText(row, column.index(), formatter));
    }
    return fields;
  }

  private void putExcelField(Map<String, Object> fields, List<ExcelColumn> columns, String label, Object value) {
    columns.stream()
        .filter(column -> label.equals(column.label()))
        .findFirst()
        .ifPresent(column -> fields.put(column.key(), value));
  }

  private String cellText(Row row, int index, DataFormatter formatter) {
    if (row == null) return "";
    return formatter.formatCellValue(row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
  }

  private int excelColumnWidth(String label) {
    if ("身份证号".equals(label)) return 210;
    if ("姓名".equals(label)) return 110;
    if ("性别".equals(label) || "年龄".equals(label) || "序号".equals(label)) return 80;
    if (label.length() >= 18) return 240;
    if (label.length() >= 10) return 190;
    return 140;
  }

  private String text(Row row, Map<String, Integer> headers, String header, DataFormatter formatter) {
    Integer index = headers.get(header);
    if (row == null || index == null) return "";
    Cell cell = row.getCell(index);
    return cell == null ? "" : formatter.formatCellValue(cell).trim();
  }

  private String groupKey(String riskText) {
    if (riskText == null || riskText.isBlank()) return null;
    String cleaned = riskText.trim();
    if (riskText.contains("组织串联")) return "organizers";
    if (cleaned.matches(".*(^|[^一二三四0-9])(?:一级|1级|1\\.0级?|1)(?:[^一二三四0-9]|$).*")) return "organizers";
    if (riskText.contains("活跃响应")) return "responders";
    if (riskText.contains("积极响应")) return "responders";
    if (cleaned.matches(".*(^|[^一二三四0-9])(?:二级|2级|2\\.0级?|2)(?:[^一二三四0-9]|$).*")) return "responders";
    if (riskText.contains("到场非投资人") || riskText.contains("到场非")) return "arrived";
    if (riskText.contains("隐名投资人") || riskText.contains("隐名投资")) return "hidden";
    if (riskText.contains("一般参与")) return "general";
    if (cleaned.matches(".*(^|[^一二三四0-9])(?:三级|3级|3\\.0级?|3)(?:[^一二三四0-9]|$).*")) return "general";
    if (riskText.contains("重点关注") || riskText.contains("密切关注")) return "watch";
    if (cleaned.matches(".*(^|[^一二三四0-9])(?:四级|4级|4\\.0级?|4)(?:[^一二三四0-9]|$).*")) return "watch";
    return null;
  }

  private String fallbackGroup(Row row, Map<String, Integer> headers, DataFormatter formatter) {
    String hiddenInvestor = firstNonBlank(
        text(row, headers, "代持自然人", formatter),
        text(row, headers, "是否穿透（市专班提供）", formatter),
        text(row, headers, "隐名投资人", formatter));
    if (!hiddenInvestor.isBlank() && !"无".equals(hiddenInvestor)) return "hidden";
    return "arrived";
  }

  private String riskLabel(String group) {
    return switch (group) {
      case "organizers" -> "组织串联";
      case "responders" -> "活跃响应";
      case "general" -> "一般参与";
      case "watch" -> "密切关注";
      case "arrived" -> "到场非投资人";
      case "hidden" -> "隐名投资人";
      default -> "未分级";
    };
  }

  private String normalizeId(String value) {
    String cleaned = value.replaceAll("[^0-9Xx.Ee+-]", "");
    if (cleaned.isBlank()) return "";
    if (cleaned.toLowerCase(Locale.ROOT).contains("e")) {
      try {
        return new BigDecimal(cleaned).toBigInteger().toString();
      } catch (NumberFormatException ignored) {
        return cleaned;
      }
    }
    return cleaned.endsWith(".0") ? cleaned.substring(0, cleaned.length() - 2) : cleaned;
  }

  private String moneyText(String value) {
    double amount = toDouble(value);
    if (amount <= 0) return "0万";
    return new DecimalFormat("#,##0.##").format(amount / 10_000D) + "万";
  }

  private boolean hasPhoto(String personId) {
    return List.of(".png", "_5.png", ".jpg", ".jpeg").stream()
        .map(extension -> properties.photoDir().resolve(personId + extension))
        .anyMatch(Files::isRegularFile);
  }

  private String normalizePoliceStation(String value) {
    if (value.isBlank()) return "未填写";
    return value.replace("互通", "派出所");
  }

  private String locality(String area) {
    return isHarbinArea(area) ? "本市" : "外市";
  }

  private String dashboardArea(Row row, Map<String, Integer> headers, DataFormatter formatter) {
    String simpleResidence = text(row, headers, "省内人员简易户籍", formatter);
    return dashboardArea(simpleResidence);
  }

  private String dashboardArea(String simpleResidence) {
    String harbinArea = normalizeHarbinArea(simpleResidence);
    if (!harbinArea.isBlank()) return harbinArea;
    String provinceCity = normalizeHeilongjiangCity(simpleResidence);
    if (!provinceCity.isBlank()) return provinceCity;
    return firstNonBlank(simpleResidence, "未填写");
  }

  private String normalizeHarbinArea(String value) {
    if (value == null || value.isBlank()) return "";
    return HARBIN_AREAS.stream()
        .filter(value::contains)
        .findFirst()
        .orElse("");
  }

  private String normalizeHeilongjiangCity(String value) {
    if (value == null || value.isBlank()) return "";
    return HEILONGJIANG_CITIES.stream()
        .filter(value::contains)
        .findFirst()
        .orElse("");
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

  private String firstNonBlank(String... values) {
    return List.of(values).stream()
        .filter(Objects::nonNull)
        .map(String::trim)
        .filter(value -> !value.isBlank())
        .findFirst()
        .orElse("");
  }
}

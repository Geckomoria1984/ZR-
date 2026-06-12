package com.example.groupdashboard;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.stereotype.Service;

@Service
public class AdminPeopleService {
  private final ExcelDashboardService dashboardService;
  private final LinkedHashMap<String, Map<String, Object>> people = new LinkedHashMap<>();
  private List<Map<String, Object>> headers = List.of();
  private boolean initialized = false;

  public AdminPeopleService(ExcelDashboardService dashboardService) {
    this.dashboardService = dashboardService;
  }

  public synchronized Map<String, Object> list(String name, String idNumber, String gender, String risk, String amountBucket, String locality, String province, String residenceProvince, String region, boolean excludeLevelGroups, Integer age, int page, int size) {
    initialize();
    List<Map<String, Object>> filtered = people.values().stream()
        .filter(person -> contains(person.get("name"), name))
        .filter(person -> contains(person.get("idNumber"), idNumber))
        .filter(person -> gender == null || gender.isBlank() || gender.equals(person.get("gender")))
        .filter(person -> risk == null || risk.isBlank() || risk.equals(person.get("risk")))
        .filter(person -> !excludeLevelGroups || !isVisibleLevelGroup(person))
        .filter(person -> matchesAmountBucket(person, amountBucket))
        .filter(person -> locality == null || locality.isBlank() || locality.equals(person.get("locality")))
        .filter(person -> province == null || province.isBlank() || !"本省".equals(province) || isHeilongjiangPerson(person))
        .filter(person -> matchesResidenceProvince(person, residenceProvince))
        .filter(person -> matchesRegion(person, region))
        .filter(person -> age == null || age.equals(toInt(person.get("age"))))
        .sorted(Comparator.comparing(person -> String.valueOf(person.getOrDefault("id", ""))))
        .collect(Collectors.toCollection(ArrayList::new));
    int safeSize = Math.max(1, Math.min(size, 1000));
    int safePage = Math.max(1, page);
    int maxPage = Math.max(1, (filtered.size() + safeSize - 1) / safeSize);
    if (safePage > maxPage) safePage = maxPage;
    int from = Math.min((safePage - 1) * safeSize, filtered.size());
    int to = Math.min(from + safeSize, filtered.size());
    return Map.of(
        "headers", headers,
        "rows", filtered.subList(from, to),
        "total", filtered.size(),
        "page", safePage,
        "size", safeSize);
  }

  public synchronized Map<String, Object> get(String id) {
    initialize();
    Map<String, Object> person = people.get(id);
    if (person == null) throw new IllegalArgumentException("人员不存在: " + id);
    return person;
  }

  public synchronized Optional<Map<String, Object>> findByIdentity(String idNumber, String name) {
    initialize();
    String cleanedId = String.valueOf(idNumber == null ? "" : idNumber).trim();
    String cleanedName = String.valueOf(name == null ? "" : name).trim();
    return people.values().stream()
        .filter(person -> cleanedId.isBlank() || cleanedId.equals(String.valueOf(person.getOrDefault("idNumber", "")).trim()))
        .filter(person -> cleanedName.isBlank() || cleanedName.equals(String.valueOf(person.getOrDefault("name", "")).trim()))
        .findFirst()
        .map(LinkedHashMap::new);
  }

  public synchronized Map<String, Object> create(Map<String, Object> payload) {
    initialize();
    Map<String, Object> person = defaults();
    person.putAll(clean(payload));
    person.put("id", String.valueOf(payload.getOrDefault("id", "p" + UUID.randomUUID().toString().replace("-", "").substring(0, 12))));
    normalizeDerivedFields(person);
    people.put(String.valueOf(person.get("id")), person);
    return person;
  }

  public synchronized Map<String, Object> update(String id, Map<String, Object> payload) {
    initialize();
    Map<String, Object> person = people.get(id);
    if (person == null) throw new IllegalArgumentException("人员不存在: " + id);
    person.putAll(clean(payload));
    person.put("id", id);
    normalizeDerivedFields(person);
    return person;
  }

  public synchronized void delete(String id) {
    initialize();
    people.remove(id);
  }

  public synchronized Map<String, Object> importExcel(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要导入的 Excel 文件");
    try {
      reload(dashboardService.importExcel(file.getInputStream()));
    } catch (Exception exception) {
      throw new IllegalStateException("导入 Excel 失败", exception);
    }
    return Map.of(
        "imported", people.size(),
        "columns", headers.size());
  }

  private void initialize() {
    if (initialized) return;
    reload(dashboardService.dashboard());
  }

  private void reload(Map<String, Object> dashboard) {
    people.clear();
    Object rawHeaders = dashboard.get("excelColumns");
    if (rawHeaders instanceof List<?> list) {
      headers = list.stream()
          .filter(item -> item instanceof Map<?, ?>)
          .map(item -> {
            Map<String, Object> header = new LinkedHashMap<>();
            ((Map<?, ?>) item).forEach((key, value) -> header.put(String.valueOf(key), value));
            return header;
          })
          .collect(Collectors.toCollection(ArrayList::new));
    } else {
      headers = List.of();
    }

    Object rows = dashboard.getOrDefault("adminPeople", dashboard.get("people"));
    if (rows instanceof List<?> list) {
      for (Object row : list) {
        if (row instanceof Map<?, ?> source) {
          Map<String, Object> person = new LinkedHashMap<>();
          source.forEach((key, value) -> person.put(String.valueOf(key), value));
          normalizeDerivedFields(person);
          people.put(String.valueOf(person.get("id")), person);
        }
      }
    }
    initialized = true;
  }

  private Map<String, Object> defaults() {
    Map<String, Object> person = new LinkedHashMap<>();
    person.put("name", "未命名");
    person.put("idNumber", "");
    person.put("gender", "未填写");
    person.put("age", 0);
    person.put("amount", "0万");
    person.put("occupation", "未填写");
    person.put("behavior", "未填写");
    person.put("visits", 0);
    person.put("policeStation", "未填写");
    person.put("district", "未填写");
    person.put("locality", "本市");
    person.put("group", "general");
    person.put("risk", "一般参与");
    person.put("avatarIndex", people.size());
    person.put("phone", "未填写");
    person.put("address", "未填写");
    person.put("latestNote", "后台新增人员");
    person.put("photoUrl", null);
    person.put("excelFields", new LinkedHashMap<String, Object>());
    return person;
  }

  private Map<String, Object> clean(Map<String, Object> payload) {
    Map<String, Object> cleaned = new LinkedHashMap<>();
    payload.forEach((key, value) -> {
      if (value != null) cleaned.put(key, value);
    });
    return cleaned;
  }

  private void normalizeDerivedFields(Map<String, Object> person) {
    String risk = String.valueOf(person.getOrDefault("risk", ""));
    if (risk.contains("组织串联")) {
      person.put("group", "organizers");
      person.put("risk", "组织串联");
    } else if (risk.contains("活跃响应") || risk.contains("积极响应")) {
      person.put("group", "responders");
      person.put("risk", "活跃响应");
    } else if (risk.contains("密切关注") || risk.contains("重点关注")) {
      person.put("group", "watch");
      person.put("risk", "密切关注");
    } else if (risk.contains("到场非投资人") || risk.contains("到场非")) {
      person.put("group", "arrived");
      person.put("risk", "到场非投资人");
    } else if (risk.contains("隐名投资人") || risk.contains("隐名投资")) {
      person.put("group", "hidden");
      person.put("risk", "隐名投资人");
    } else if (risk.contains("一般参与")) {
      person.put("group", "general");
      person.put("risk", "一般参与");
    }
    person.put("libraryStatus", normalizedLibraryStatus(person));
    String district = String.valueOf(person.getOrDefault("district", ""));
    person.put("locality", district.contains("哈尔滨") || isHarbinDistrict(district) ? "本市" : "外市");
  }

  private String normalizedLibraryStatus(Map<String, Object> person) {
    String current = String.valueOf(person.getOrDefault("libraryStatus", "")).trim();
    if (!isLegacyLibraryStatus(current)) return current.isBlank() ? "未填写" : current;
    String direct = firstNonBlank(
        excelFieldByLabels(person, List.of("是否在库", "在库情况", "列库情况")));
    if (!direct.isBlank() && !isLegacyLibraryStatus(direct)) return direct;
    String level = firstNonBlank(
        excelFieldByLabels(person, List.of("在库级别", "列库级别", "库内级别")));
    String reason = firstNonBlank(
        excelFieldByLabels(person, List.of("列库原因", "在库原因", "入库原因", "列管原因")));
    if (!level.isBlank() && !reason.isBlank()) return level + "," + reason;
    if (!level.isBlank()) return level;
    if (!reason.isBlank()) return reason;
    return "不在库";
  }

  private boolean isLegacyLibraryStatus(String status) {
    if (status == null || status.isBlank()) return true;
    return status.contains("投资") && List.of("组织串联", "活跃响应", "一般参与", "密切关注").stream().anyMatch(status::contains);
  }

  private String excelFieldByLabels(Map<String, Object> person, List<String> labels) {
    Object rawFields = person.get("excelFields");
    if (!(rawFields instanceof Map<?, ?> fields)) return "";
    for (Map.Entry<?, ?> entry : fields.entrySet()) {
      String key = String.valueOf(entry.getKey());
      String label = headerLabel(key);
      if (labels.stream().anyMatch(label::equals)) {
        return String.valueOf(entry.getValue() == null ? "" : entry.getValue()).trim();
      }
    }
    return "";
  }

  private String headerLabel(String key) {
    for (Map<String, Object> header : headers) {
      if (key.equals(String.valueOf(header.get("key")))) {
        return String.valueOf(header.getOrDefault("label", "")).trim();
      }
    }
    return "";
  }

  private String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.trim().isBlank()) return value.trim();
    }
    return "";
  }

  private boolean contains(Object source, String query) {
    if (query == null || query.isBlank()) return true;
    return String.valueOf(source == null ? "" : source).toLowerCase(Locale.ROOT)
        .contains(query.trim().toLowerCase(Locale.ROOT));
  }

  private int toInt(Object value) {
    if (value instanceof Number number) return number.intValue();
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private boolean matchesAmountBucket(Map<String, Object> person, String bucket) {
    if (bucket == null || bucket.isBlank()) return true;
    double amount = toDouble(person.get("trustShareAmount"));
    return switch (bucket) {
      case "gte10000" -> amount >= 100_000_000D;
      case "5000-10000" -> amount >= 50_000_000D && amount < 100_000_000D;
      case "3000-5000" -> amount >= 30_000_000D && amount < 50_000_000D;
      case "1000-3000" -> amount >= 10_000_000D && amount < 30_000_000D;
      case "500-1000" -> amount >= 5_000_000D && amount < 10_000_000D;
      case "300-500" -> amount >= 3_000_000D && amount < 5_000_000D;
      case "lt300" -> amount < 3_000_000D;
      default -> true;
    };
  }

  private boolean matchesRegion(Map<String, Object> person, String region) {
    if (region == null || region.isBlank()) return true;
    String normalizedRegion = region.trim();
    return normalizedRegion.equals(String.valueOf(person.getOrDefault("district", "")).trim());
  }

  private boolean matchesResidenceProvince(Map<String, Object> person, String province) {
    if (province == null || province.isBlank()) return true;
    String normalized = province.trim();
    String householdProvince = String.valueOf(person.getOrDefault("householdProvince", "")).trim();
    if (!householdProvince.isBlank()) return normalized.equals(householdProvince);
    return String.valueOf(person.getOrDefault("address", "")).contains(normalized)
        || String.valueOf(person.getOrDefault("currentAddress", "")).contains(normalized);
  }

  private boolean isVisibleLevelGroup(Map<String, Object> person) {
    String group = String.valueOf(person.getOrDefault("group", ""));
    return List.of("organizers", "responders", "general", "watch").contains(group);
  }

  private double toDouble(Object value) {
    if (value instanceof Number number) return number.doubleValue();
    String text = String.valueOf(value == null ? "" : value).trim().replace(",", "");
    double multiplier = 1D;
    if (text.contains("亿")) {
      multiplier = 100_000_000D;
    } else if (text.contains("万")) {
      multiplier = 10_000D;
    }
    try {
      String numeric = text.replaceAll("[^0-9.Ee+\\-]", "");
      if (numeric.isBlank()) return 0D;
      return Double.parseDouble(numeric) * multiplier;
    } catch (NumberFormatException exception) {
      return 0D;
    }
  }

  private boolean isHarbinDistrict(String value) {
    return List.of("道里", "南岗", "道外", "平房", "松北", "香坊", "呼兰", "阿城", "双城", "依兰", "方正", "宾县", "巴彦", "木兰", "通河", "延寿", "尚志", "五常")
        .stream()
        .anyMatch(value::contains);
  }

  private boolean isHeilongjiangPerson(Map<String, Object> person) {
    String district = String.valueOf(person.getOrDefault("district", ""));
    return isHarbinDistrict(district) || List.of("齐齐哈尔", "牡丹江", "佳木斯", "大庆", "鸡西", "双鸭山", "伊春", "七台河", "鹤岗", "黑河", "绥化", "大兴安岭")
        .stream()
        .anyMatch(district::contains);
  }
}

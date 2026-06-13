package com.example.groupdashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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
public class DashboardFundRelationService {
  private static final TypeReference<LinkedHashMap<String, Object>> MAP_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;

  public DashboardFundRelationService(JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
  }

  @Transactional
  public Map<String, Object> importExcel(MultipartFile file) {
    if (file == null || file.isEmpty()) throw new IllegalArgumentException("请选择要导入的资金关系 Excel 文件");
    try {
      ParsedFundExcel parsed = parse(file.getInputStream());
      ensureSchema();
      jdbcTemplate.update("DELETE FROM dashboard_fund_relation");
      jdbcTemplate.update("DELETE FROM dashboard_fund_relation_column");
      for (Map<String, Object> header : parsed.headers()) {
        jdbcTemplate.update(
            "INSERT INTO dashboard_fund_relation_column (field_key, label, column_index, width) VALUES (?, ?, ?, ?)",
            header.get("key"),
            header.get("label"),
            header.get("index"),
            header.get("width"));
      }
      int rowIndex = 1;
      for (Map<String, Object> row : parsed.rows()) {
        jdbcTemplate.update(
            "INSERT INTO dashboard_fund_relation (row_index, payload_json, search_text) VALUES (?, ?, ?)",
            rowIndex++,
            objectMapper.writeValueAsString(row),
            searchText(row));
      }
      return Map.of("imported", parsed.rows().size(), "columns", parsed.headers().size());
    } catch (Exception exception) {
      throw new IllegalStateException("导入资金关系 Excel 失败", exception);
    }
  }

  public Map<String, Object> graphForPerson(Map<String, Object> person) {
    return graphForIdentity(person);
  }

  public Map<String, Object> graphForIdentity(Map<String, Object> person) {
    ensureSchema();
    String name = String.valueOf(person.getOrDefault("name", "")).trim();
    String idNumber = String.valueOf(person.getOrDefault("idNumber", "")).trim();
    List<Map<String, Object>> headers = loadHeaders();
    List<Map<String, Object>> allRows = loadRows();
    List<Map<String, Object>> rows = hasLayerColumns(headers)
        ? layeredRowsForIdentity(allRows, headers, name, idNumber)
        : allRows.stream()
            .filter(row -> matches(row, name, idNumber))
            .collect(Collectors.toCollection(ArrayList::new));
    return Map.of(
        "headers", headers,
        "rows", rows,
        "total", rows.size(),
        "graph", buildGraph(person, headers, rows));
  }

  private Map<String, Object> buildGraph(
      Map<String, Object> person,
      List<Map<String, Object>> headers,
      List<Map<String, Object>> rows) {
    if (hasLayerColumns(headers)) return buildLayeredGraph(person, headers, rows);

    String personName = String.valueOf(person.getOrDefault("name", "人员")).trim();
    String personIdNumber = String.valueOf(person.getOrDefault("idNumber", "")).trim();
    int columns = Math.max(1, Math.min(4, rows.size()));
    int rowCount = rows.isEmpty() ? 1 : (int) Math.ceil(rows.size() / (double) columns);
    int width = Math.max(980, columns * 320 + 280);
    int height = Math.max(460, rowCount * 142 + 260);
    List<Map<String, Object>> nodes = new ArrayList<>();
    List<Map<String, Object>> edges = new ArrayList<>();
    nodes.add(graphNode("person", personName.isBlank() ? "人员" : personName, "当前人员", width / 2, height - 72, true));

    int index = 0;
    for (Map<String, Object> row : rows) {
      String upstreamName = upstreamName(row, headers, personName, personIdNumber);
      String amount = amountText(row, headers);
      String nodeId = "up-" + index;
      int column = index % columns;
      int levelRow = index / columns;
      int x = 180 + column * 320;
      int y = 92 + levelRow * 142;
      nodes.add(graphNode(nodeId, upstreamName, "上一层", x, y, false));
      edges.add(graphEdge("person", nodeId, amount, row.get("rowIndex"), x, y));
      index++;
    }

    return Map.of(
        "nodes", nodes,
        "edges", edges,
        "width", width,
        "height", height);
  }

  private boolean hasLayerColumns(List<Map<String, Object>> headers) {
    return !headerKey(headers, List.of("层级")).isBlank()
        && !headerKey(headers, List.of("上级身份证号", "上级证件号", "上级ID")).isBlank();
  }

  private Map<String, Object> buildLayeredGraph(
      Map<String, Object> person,
      List<Map<String, Object>> headers,
      List<Map<String, Object>> rows) {
    String rootName = String.valueOf(person.getOrDefault("name", "显名投资人")).trim();
    String rootIdNumber = String.valueOf(person.getOrDefault("idNumber", "")).trim();
    String rootId = nodeId(rootIdNumber, rootName, 0);
    Map<String, GraphNodeDraft> drafts = new LinkedHashMap<>();
    Map<String, GraphEdgeDraft> edgeDrafts = new LinkedHashMap<>();
    drafts.put(rootId, new GraphNodeDraft(rootId, rootName, rootIdNumber, 0, true));

    String nameKey = headerKey(headers, List.of("隐名姓名", "出资人姓名", "出资人户名", "户名", "姓名"));
    String idKey = headerKey(headers, List.of("隐名证件号", "出资人证件号", "身份证号", "证件号"));
    String amountKey = headerKey(headers, List.of("向显名投资金额", "向上一层投资金额", "投资金额", "金额"));
    String layerKey = headerKey(headers, List.of("层级"));
    String parentKey = headerKey(headers, List.of("上级身份证号", "上级证件号", "上级ID"));
    int rootLayer = rootLayer(headers, rows, rootIdNumber);

    for (Map<String, Object> row : rows) {
      Map<String, String> fields = stringFields(row);
      int rawLayer = Math.max(1, intText(fields.get(layerKey)));
      int layer = Math.max(1, rawLayer - rootLayer);
      String name = fieldValue(fields, nameKey);
      String idNumber = fieldValue(fields, idKey);
      if (name.isBlank() && idNumber.isBlank()) continue;
      if (!rootIdNumber.isBlank() && rootIdNumber.equals(idNumber)) continue;
      String currentId = nodeId(idNumber, name, layer);
      drafts.putIfAbsent(currentId, new GraphNodeDraft(currentId, name, idNumber, layer, false));

      String parentIdNumber = fieldValue(fields, parentKey);
      if (parentIdNumber.isBlank()) continue;
      String parentName = parentDisplayName(fields, headers, parentIdNumber);
      String parentId = parentIdNumber.equals(rootIdNumber)
          ? rootId
          : findNodeIdByIdNumber(drafts, parentIdNumber).orElse(nodeId(parentIdNumber, "上级", Math.max(0, layer - 1)));
      drafts.putIfAbsent(parentId, new GraphNodeDraft(parentId, parentName, parentIdNumber, Math.max(0, layer - 1), parentId.equals(rootId)));

      String amount = fieldValue(fields, amountKey);
      String edgeId = currentId + "->" + parentId;
      edgeDrafts.putIfAbsent(edgeId, new GraphEdgeDraft(currentId, parentId, amount, row.get("rowIndex")));
    }

    List<GraphNodeDraft> orderedDrafts = new ArrayList<>(drafts.values());
    Map<Integer, List<GraphNodeDraft>> byLayer = orderedDrafts.stream()
        .collect(Collectors.groupingBy(GraphNodeDraft::layer, LinkedHashMap::new, Collectors.toCollection(ArrayList::new)));
    int maxCount = byLayer.values().stream().mapToInt(List::size).max().orElse(1);
    int maxLayer = byLayer.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);
    int width = Math.max(1240, maxCount * 340 + 360);
    int height = Math.max(460, (maxLayer + 1) * 150 + 130);

    List<Map<String, Object>> nodes = new ArrayList<>();
    Map<String, GraphNodeDraft> positioned = new LinkedHashMap<>();
    byLayer.forEach((layer, layerNodes) -> {
      for (int index = 0; index < layerNodes.size(); index++) {
        GraphNodeDraft draft = layerNodes.get(index);
        int x = layerNodes.size() == 1
            ? width / 2
            : 180 + Math.round(index * ((width - 360) / (float) (layerNodes.size() - 1)));
        int y = 86 + layer * 150;
        draft = draft.withPosition(x, y);
        positioned.put(draft.id(), draft);
        nodes.add(graphNode(draft.id(), displayName(draft), levelLabel(draft.layer()), x, y, draft.primary()));
      }
    });

    List<Map<String, Object>> edges = edgeDrafts.values().stream()
        .filter(edge -> positioned.containsKey(edge.source()) && positioned.containsKey(edge.target()))
        .filter(edge -> {
          GraphNodeDraft source = positioned.get(edge.source());
          GraphNodeDraft target = positioned.get(edge.target());
          return source.layer() - target.layer() == 1;
        })
        .map(edge -> {
          GraphNodeDraft source = positioned.get(edge.source());
          GraphNodeDraft target = positioned.get(edge.target());
          return graphEdge(edge.source(), edge.target(), edge.amount(), edge.rowIndex(), (source.x() + target.x()) / 2, (source.y() + target.y()) / 2);
        })
        .collect(Collectors.toCollection(ArrayList::new));

    return Map.of("nodes", nodes, "edges", edges, "width", width, "height", height);
  }

  private List<Map<String, Object>> layeredRowsForIdentity(
      List<Map<String, Object>> allRows,
      List<Map<String, Object>> headers,
      String name,
      String idNumber) {
    String visibleInvestorIdKey = headerKey(headers, List.of("显名投资人证件号"));
    String visibleInvestorNameKey = headerKey(headers, List.of("显名投资人姓名"));
    String hiddenIdKey = headerKey(headers, List.of("隐名证件号", "出资人证件号", "身份证号", "证件号"));
    String parentKey = headerKey(headers, List.of("上级身份证号", "上级证件号", "上级ID"));

    if (!idNumber.isBlank()) {
      List<Map<String, Object>> visibleRows = allRows.stream()
          .filter(row -> idNumber.equals(fieldValue(stringFields(row), visibleInvestorIdKey)))
          .collect(Collectors.toCollection(ArrayList::new));
      if (!visibleRows.isEmpty()) return visibleRows;
    } else if (!name.isBlank()) {
      List<Map<String, Object>> visibleRows = allRows.stream()
          .filter(row -> name.equals(fieldValue(stringFields(row), visibleInvestorNameKey)))
          .collect(Collectors.toCollection(ArrayList::new));
      if (!visibleRows.isEmpty()) return visibleRows;
    }

    if (idNumber.isBlank()) {
      return allRows.stream()
          .filter(row -> matches(row, name, idNumber))
          .collect(Collectors.toCollection(ArrayList::new));
    }

    List<Map<String, Object>> selected = new ArrayList<>();
    java.util.Set<String> parentIds = new java.util.LinkedHashSet<>();
    java.util.Set<Object> selectedRowIndexes = new java.util.LinkedHashSet<>();
    parentIds.add(idNumber);

    boolean changed = true;
    while (changed) {
      changed = false;
      for (Map<String, Object> row : allRows) {
        Object rowIndex = row.get("rowIndex");
        if (selectedRowIndexes.contains(rowIndex)) continue;
        Map<String, String> fields = stringFields(row);
        String parentId = fieldValue(fields, parentKey);
        if (!parentIds.contains(parentId)) continue;
        selected.add(row);
        selectedRowIndexes.add(rowIndex);
        String childId = fieldValue(fields, hiddenIdKey);
        if (!childId.isBlank() && parentIds.add(childId)) changed = true;
      }
    }

    allRows.stream()
        .filter(row -> idNumber.equals(fieldValue(stringFields(row), hiddenIdKey)))
        .filter(row -> selectedRowIndexes.add(row.get("rowIndex")))
        .forEach(selected::add);

    return selected;
  }

  private int rootLayer(List<Map<String, Object>> headers, List<Map<String, Object>> rows, String rootIdNumber) {
    if (rootIdNumber == null || rootIdNumber.isBlank()) return 0;
    String idKey = headerKey(headers, List.of("隐名证件号", "出资人证件号", "身份证号", "证件号"));
    String layerKey = headerKey(headers, List.of("层级"));
    return rows.stream()
        .map(this::stringFields)
        .filter(fields -> rootIdNumber.equals(fieldValue(fields, idKey)))
        .map(fields -> Math.max(0, intText(fields.get(layerKey))))
        .findFirst()
        .orElse(0);
  }

  private Map<String, Object> graphNode(
      String id, String label, String level, int x, int y, boolean primary) {
    Map<String, Object> node = new LinkedHashMap<>();
    node.put("id", id);
    node.put("label", shortText(label, 12));
    node.put("fullLabel", label);
    node.put("level", level);
    node.put("x", x);
    node.put("y", y);
    node.put("primary", primary);
    return node;
  }

  private Map<String, Object> graphEdge(
      String source, String target, String amount, Object rowIndex, int targetX, int targetY) {
    Map<String, Object> edge = new LinkedHashMap<>();
    edge.put("source", source);
    edge.put("target", target);
    edge.put("amount", graphAmountText(amount));
    edge.put("rowIndex", rowIndex);
    edge.put("labelX", targetX);
    edge.put("labelY", targetY + 72);
    return edge;
  }

  private String graphAmountText(String amount) {
    String text = String.valueOf(amount == null ? "" : amount).trim();
    if (text.isBlank() || "未填写".equals(text)) return "未填写";
    if (text.startsWith("向上层投资金额：") && text.endsWith("万")) return text;
    try {
      double numeric = Double.parseDouble(text.replace(",", "").replaceAll("[^0-9.-]", ""));
      double amountInWan = text.contains("万") ? numeric : numeric / 10000D;
      return "向上层投资金额：" + Math.round(amountInWan) + "万";
    } catch (NumberFormatException exception) {
      return "向上层投资金额：" + text;
    }
  }

  private String headerKey(List<Map<String, Object>> headers, List<String> needles) {
    for (String needle : needles) {
      for (Map<String, Object> header : headers) {
        String label = String.valueOf(header.getOrDefault("label", ""));
        if (label.contains(needle)) return String.valueOf(header.get("key"));
      }
    }
    return "";
  }

  private Map<String, String> stringFields(Map<String, Object> row) {
    Map<String, String> fields = new LinkedHashMap<>();
    Object raw = row.get("fields");
    if (raw instanceof Map<?, ?> map) {
      map.forEach((key, value) -> fields.put(String.valueOf(key), String.valueOf(value == null ? "" : value).trim()));
    }
    return fields;
  }

  private String fieldValue(Map<String, String> fields, String key) {
    if (key == null || key.isBlank()) return "";
    return fields.getOrDefault(key, "").trim();
  }

  private int intText(String value) {
    try {
      return Integer.parseInt(String.valueOf(value == null ? "" : value).replaceAll("[^0-9-]", ""));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  private String nodeId(String idNumber, String name, int layer) {
    if (idNumber != null && !idNumber.isBlank()) return "id-" + idNumber.trim();
    return "name-" + layer + "-" + String.valueOf(name == null ? "未填写" : name).trim();
  }

  private java.util.Optional<String> findNodeIdByIdNumber(Map<String, GraphNodeDraft> drafts, String idNumber) {
    if (idNumber == null || idNumber.isBlank()) return java.util.Optional.empty();
    return drafts.values().stream()
        .filter(node -> idNumber.equals(node.idNumber()))
        .map(GraphNodeDraft::id)
        .findFirst();
  }

  private String displayName(GraphNodeDraft draft) {
    if (draft.name() != null && !draft.name().isBlank() && !"上级".equals(draft.name())) return draft.name();
    return draft.idNumber();
  }

  private String parentDisplayName(Map<String, String> fields, List<Map<String, Object>> headers, String parentIdNumber) {
    String visibleInvestorIdKey = headerKey(headers, List.of("显名投资人证件号"));
    String visibleInvestorNameKey = headerKey(headers, List.of("显名投资人姓名"));
    if (!parentIdNumber.isBlank() && parentIdNumber.equals(fieldValue(fields, visibleInvestorIdKey))) {
      String visibleName = fieldValue(fields, visibleInvestorNameKey);
      if (!visibleName.isBlank()) return visibleName;
    }
    return parentIdNumber.isBlank() ? "上级" : parentIdNumber;
  }

  private String levelLabel(int layer) {
    if (layer <= 0) return "显名投资人";
    return switch (layer) {
      case 1 -> "一层";
      case 2 -> "二层";
      case 3 -> "三层";
      case 4 -> "四层";
      default -> layer + "层";
    };
  }

  private String upstreamName(
      Map<String, Object> row,
      List<Map<String, Object>> headers,
      String personName,
      String personIdNumber) {
    String preferred = fieldByLabel(row, headers, List.of("收款", "上级", "上一层", "产品", "账户", "被投资", "资金方"));
    if (meaningfulCounterpart(preferred, personName, personIdNumber)) return preferred;

    Object fields = row.get("fields");
    if (fields instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (meaningfulCounterpart(text, personName, personIdNumber) && !looksLikeAmount(text)) return text;
      }
    }
    return "上一层资金";
  }

  private String amountText(Map<String, Object> row, List<Map<String, Object>> headers) {
    String preferred = fieldByLabel(row, headers, List.of("投资金额", "向上一层投资", "金额", "资金", "份额"));
    if (!preferred.isBlank()) return preferred;
    Object fields = row.get("fields");
    if (fields instanceof Map<?, ?> map) {
      for (Object value : map.values()) {
        String text = String.valueOf(value == null ? "" : value).trim();
        if (looksLikeAmount(text)) return text;
      }
    }
    return "";
  }

  private String fieldByLabel(Map<String, Object> row, List<Map<String, Object>> headers, List<String> needles) {
    Object fields = row.get("fields");
    if (!(fields instanceof Map<?, ?> map)) return "";
    for (Map<String, Object> header : headers) {
      String label = String.valueOf(header.getOrDefault("label", ""));
      boolean matched = needles.stream().anyMatch(label::contains);
      if (!matched) continue;
      String key = String.valueOf(header.get("key"));
      Object value = map.get(key);
      String text = String.valueOf(value == null ? "" : value).trim();
      if (!text.isBlank()) return text;
    }
    return "";
  }

  private boolean meaningfulCounterpart(String text, String personName, String personIdNumber) {
    if (text == null || text.isBlank()) return false;
    if (!personName.isBlank() && text.contains(personName)) return false;
    return personIdNumber.isBlank() || !text.contains(personIdNumber);
  }

  private boolean looksLikeAmount(String text) {
    if (text == null || text.isBlank()) return false;
    return text.matches(".*\\d.*") && (text.contains("万") || text.contains("元") || text.matches("[0-9,]+(\\.[0-9]+)?"));
  }

  private String shortText(String text, int maxLength) {
    if (text == null || text.isBlank()) return "未填写";
    String trimmed = text.trim();
    return trimmed.length() <= maxLength ? trimmed : trimmed.substring(0, maxLength) + "...";
  }

  private ParsedFundExcel parse(InputStream input) throws IOException {
    IOUtils.setByteArrayMaxOverride(200_000_000);
    try (Workbook workbook = WorkbookFactory.create(input)) {
      Sheet sheet = workbook.getSheet("Sheet1");
      if (sheet == null) sheet = workbook.getSheetAt(0);
      DataFormatter formatter = new DataFormatter();
      List<Map<String, Object>> headers = headers(sheet.getRow(0), formatter);
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
      return new ParsedFundExcel(headers, rows);
    } catch (Exception exception) {
      if (exception instanceof IOException ioException) throw ioException;
      throw new IOException(exception);
    }
  }

  private List<Map<String, Object>> headers(Row row, DataFormatter formatter) {
    List<Map<String, Object>> headers = new ArrayList<>();
    if (row == null) return headers;
    for (Cell cell : row) {
      String label = formatter.formatCellValue(cell).trim();
      if (label.isBlank()) continue;
      Map<String, Object> header = new LinkedHashMap<>();
      header.put("key", "fund_" + cell.getColumnIndex());
      header.put("label", label);
      header.put("index", cell.getColumnIndex());
      header.put("width", columnWidth(label));
      headers.add(header);
    }
    return headers;
  }

  private int columnWidth(String label) {
    if (label.contains("身份证")) return 210;
    if (label.length() >= 10) return 190;
    return 140;
  }

  private String cellText(Row row, int index, DataFormatter formatter) {
    if (row == null) return "";
    return formatter.formatCellValue(row.getCell(index, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK)).trim();
  }

  private void ensureSchema() {
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS dashboard_fund_relation_column (
          field_key VARCHAR(64) NOT NULL PRIMARY KEY,
          label VARCHAR(255) NOT NULL,
          column_index INT NOT NULL,
          width INT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS dashboard_fund_relation (
          row_index INT NOT NULL PRIMARY KEY,
          payload_json LONGTEXT NOT NULL,
          search_text LONGTEXT NOT NULL
        )
        """);
  }

  private List<Map<String, Object>> loadHeaders() {
    return jdbcTemplate.query(
        "SELECT field_key, label, column_index, width FROM dashboard_fund_relation_column ORDER BY column_index",
        (rs, rowNum) -> {
          Map<String, Object> header = new LinkedHashMap<>();
          header.put("key", rs.getString("field_key"));
          header.put("label", rs.getString("label"));
          header.put("index", rs.getInt("column_index"));
          header.put("width", rs.getInt("width"));
          return header;
        });
  }

  private List<Map<String, Object>> loadRows() {
    return jdbcTemplate.query(
        "SELECT payload_json FROM dashboard_fund_relation ORDER BY row_index",
        (rs, rowNum) -> parseRow(rs.getString("payload_json")));
  }

  private Map<String, Object> parseRow(String payload) {
    try {
      return objectMapper.readValue(payload, MAP_TYPE);
    } catch (IOException exception) {
      throw new IllegalStateException("无法读取资金关系 JSON", exception);
    }
  }

  private String searchText(Map<String, Object> row) {
    Object fields = row.get("fields");
    if (!(fields instanceof Map<?, ?> map)) return "";
    return map.values().stream()
        .map(value -> String.valueOf(value == null ? "" : value))
        .collect(Collectors.joining(" "));
  }

  private boolean matches(Map<String, Object> row, String name, String idNumber) {
    String searchText = searchText(row);
    return (!idNumber.isBlank() && searchText.contains(idNumber))
        || (!name.isBlank() && searchText.contains(name));
  }

  private boolean matchesFundRelationRow(
      Map<String, Object> row,
      List<Map<String, Object>> headers,
      String name,
      String idNumber) {
    if (!hasLayerColumns(headers)) return matches(row, name, idNumber);
    String visibleInvestorIdKey = headerKey(headers, List.of("显名投资人证件号"));
    String visibleInvestorNameKey = headerKey(headers, List.of("显名投资人姓名"));
    Map<String, String> fields = stringFields(row);
    if (!idNumber.isBlank() && idNumber.equals(fieldValue(fields, visibleInvestorIdKey))) return true;
    return idNumber.isBlank() && !name.isBlank() && name.equals(fieldValue(fields, visibleInvestorNameKey));
  }

  private record ParsedFundExcel(List<Map<String, Object>> headers, List<Map<String, Object>> rows) {}
  private record GraphNodeDraft(String id, String name, String idNumber, int layer, boolean primary, int x, int y) {
    GraphNodeDraft(String id, String name, String idNumber, int layer, boolean primary) {
      this(id, name, idNumber, layer, primary, 0, 0);
    }

    GraphNodeDraft withPosition(int x, int y) {
      return new GraphNodeDraft(id, name, idNumber, layer, primary, x, y);
    }
  }

  private record GraphEdgeDraft(String source, String target, String amount, Object rowIndex) {}
}

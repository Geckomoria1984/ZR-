package com.example.groupdashboard;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@EnableConfigurationProperties(DashboardDatabaseProperties.class)
public class DashboardDatabaseStore {
  private static final TypeReference<LinkedHashMap<String, Object>> DASHBOARD_TYPE = new TypeReference<>() {};
  private final JdbcTemplate jdbcTemplate;
  private final ObjectMapper objectMapper;
  private final DashboardDatabaseProperties properties;

  public DashboardDatabaseStore(
      JdbcTemplate jdbcTemplate,
      ObjectMapper objectMapper,
      DashboardDatabaseProperties properties) {
    this.jdbcTemplate = jdbcTemplate;
    this.objectMapper = objectMapper;
    this.properties = properties;
  }

  public boolean enabled() {
    return properties.enabled();
  }

  @Transactional
  public synchronized Map<String, Object> loadOrImport(DashboardLoader loader) throws IOException {
    if (!enabled()) return loader.load();
    ensureSchema();

    Map<String, Object> existing = loadDashboard();
    if (existing != null && !properties.importOnStartup()) return existing;
    if (existing != null && !peopleTableEmpty()) return existing;

    Map<String, Object> dashboard = loader.load();
    replace(dashboard);
    return dashboard;
  }

  private void ensureSchema() {
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS dashboard_meta (
          meta_key VARCHAR(64) NOT NULL PRIMARY KEY,
          meta_value LONGTEXT NOT NULL
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS dashboard_excel_column (
          field_key VARCHAR(64) NOT NULL PRIMARY KEY,
          label VARCHAR(255) NOT NULL,
          column_index INT NOT NULL,
          width INT NOT NULL,
          fixed_value VARCHAR(16)
        )
        """);
    jdbcTemplate.execute("""
        CREATE TABLE IF NOT EXISTS dashboard_person (
          id VARCHAR(64) NOT NULL PRIMARY KEY,
          id_number VARCHAR(64),
          name VARCHAR(255),
          gender VARCHAR(32),
          age INT,
          district VARCHAR(255),
          risk VARCHAR(255),
          group_key VARCHAR(64),
          payload_json LONGTEXT NOT NULL
        )
        """);
  }

  private Map<String, Object> loadDashboard() throws IOException {
    List<String> values = jdbcTemplate.query(
        "SELECT meta_value FROM dashboard_meta WHERE meta_key = 'dashboard_payload'",
        (rs, rowNum) -> rs.getString("meta_value"));
    if (values.isEmpty()) return null;
    Map<String, Object> dashboard = objectMapper.readValue(values.get(0), DASHBOARD_TYPE);
    dashboard.put("adminPeople", loadPeople());
    return dashboard;
  }

  private List<Map<String, Object>> loadPeople() {
    return jdbcTemplate.query(
        "SELECT payload_json FROM dashboard_person ORDER BY id",
        (rs, rowNum) -> parsePerson(rs.getString("payload_json")));
  }

  private Map<String, Object> parsePerson(String payload) {
    try {
      return objectMapper.readValue(payload, DASHBOARD_TYPE);
    } catch (IOException exception) {
      throw new IllegalStateException("无法读取数据库人员 JSON", exception);
    }
  }

  private boolean peopleTableEmpty() {
    Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM dashboard_person", Integer.class);
    return count == null || count == 0;
  }

  @Transactional
  public synchronized void replace(Map<String, Object> dashboard) throws IOException {
    ensureSchema();
    jdbcTemplate.update("DELETE FROM dashboard_meta");
    jdbcTemplate.update("DELETE FROM dashboard_person");
    jdbcTemplate.update("DELETE FROM dashboard_excel_column");

    jdbcTemplate.update(
        "INSERT INTO dashboard_meta (meta_key, meta_value) VALUES (?, ?)",
        "dashboard_payload",
        objectMapper.writeValueAsString(publicDashboard(dashboard)));

    Object columns = dashboard.get("excelColumns");
    if (columns instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> column) insertColumn(column);
      }
    }

    Object people = dashboard.getOrDefault("adminPeople", dashboard.get("people"));
    if (people instanceof List<?> list) {
      for (Object item : list) {
        if (item instanceof Map<?, ?> person) insertPerson(person);
      }
    }
  }

  private Map<String, Object> publicDashboard(Map<String, Object> dashboard) {
    Map<String, Object> copy = new LinkedHashMap<>(dashboard);
    copy.remove("adminPeople");
    return copy;
  }

  private void insertColumn(Map<?, ?> column) {
    jdbcTemplate.update(
        "INSERT INTO dashboard_excel_column (field_key, label, column_index, width, fixed_value) VALUES (?, ?, ?, ?, ?)",
        value(column, "key"),
        value(column, "label"),
        intValue(column.get("index")),
        intValue(column.get("width")),
        value(column, "fixed"));
  }

  private void insertPerson(Map<?, ?> person) throws IOException {
    jdbcTemplate.update(
        """
        INSERT INTO dashboard_person
          (id, id_number, name, gender, age, district, risk, group_key, payload_json)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
        """,
        value(person, "id"),
        value(person, "idNumber"),
        value(person, "name"),
        value(person, "gender"),
        intValue(person.get("age")),
        value(person, "district"),
        value(person, "risk"),
        value(person, "group"),
        objectMapper.writeValueAsString(person));
  }

  private String value(Map<?, ?> source, String key) {
    Object value = source.get(key);
    if (value == null || Boolean.FALSE.equals(value)) return "";
    return String.valueOf(value);
  }

  private int intValue(Object value) {
    if (value instanceof Number number) return number.intValue();
    try {
      return Integer.parseInt(String.valueOf(value));
    } catch (NumberFormatException exception) {
      return 0;
    }
  }

  @FunctionalInterface
  public interface DashboardLoader {
    Map<String, Object> load() throws IOException;
  }
}

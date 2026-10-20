package com.jvn.core.vn;

import java.io.IOException;
import com.jvn.core.io.AtomicFiles;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Cross-save persistent meta-state store for unlocks, gallery flags, and other
 * global VN progression data.
 */
public class VnPersistentStore {
  private final Path file;
  private final Map<String, Object> values = new LinkedHashMap<>();

  public VnPersistentStore() {
    this(VnStoragePaths.persistentData());
  }

  public VnPersistentStore(Path file) {
    this.file = file;
    load();
  }

  public Path getFile() {
    return file;
  }

  public Map<String, Object> snapshot() {
    return Collections.unmodifiableMap(new LinkedHashMap<>(values));
  }

  public Object get(String key) {
    if (key == null || key.isBlank()) return null;
    return values.get(key.trim());
  }

  public boolean contains(String key) {
    if (key == null || key.isBlank()) return false;
    return values.containsKey(key.trim());
  }

  public void put(String key, Object value) {
    if (key == null || key.isBlank()) return;
    values.put(key.trim(), sanitizeValue(value));
    save();
  }

  public void remove(String key) {
    if (key == null || key.isBlank()) return;
    values.remove(key.trim());
    save();
  }

  public void clear() {
    values.clear();
    save();
  }

  public double add(String key, double delta) {
    String normalized = key == null ? "" : key.trim();
    if (normalized.isEmpty()) return 0.0;
    double current = 0.0;
    Object existing = values.get(normalized);
    if (existing instanceof Number number) {
      current = number.doubleValue();
    } else if (existing instanceof String text) {
      try {
        current = Double.parseDouble(text.trim());
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        current = 0.0;
      }
    }
    double next = current + delta;
    values.put(normalized, isWhole(next) ? (int) Math.round(next) : next);
    save();
    return next;
  }

  public void load() {
    values.clear();
    if (file == null) return;
    if (!Files.exists(file)) return;
    try {
      String json = Files.readString(file, StandardCharsets.UTF_8);
      values.putAll(parseJsonObject(json));
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
    }
  }

  public void save() {
    if (file == null) return;
    try {
      Path parent = file.getParent();
      if (parent != null) Files.createDirectories(parent);
      AtomicFiles.write(file, toJson(values).getBytes(StandardCharsets.UTF_8));
    } catch (IOException ignored) {
            // reason: I/O failure on best-effort save/load; in-memory state remains valid
    }
  }

  private static Object sanitizeValue(Object value) {
    if (value == null) return null;
    if (value instanceof Boolean || value instanceof Number) return value;
    String text = String.valueOf(value);
    if ("true".equalsIgnoreCase(text)) return Boolean.TRUE;
    if ("false".equalsIgnoreCase(text)) return Boolean.FALSE;
    try {
      if (text.contains(".")) return Double.parseDouble(text);
      return Integer.parseInt(text);
    } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
      return text;
    }
  }

  private static boolean isWhole(double value) {
    return Math.abs(value - Math.rint(value)) < 1e-9;
  }

  private static String toJson(Map<String, Object> values) {
    StringBuilder sb = new StringBuilder();
    sb.append("{\n");
    int index = 0;
    for (Map.Entry<String, Object> entry : values.entrySet()) {
      if (entry.getKey() == null || entry.getKey().isBlank()) continue;
      if (index++ > 0) sb.append(",\n");
      sb.append("  \"").append(escape(entry.getKey())).append("\": ");
      appendJsonValue(sb, entry.getValue());
    }
    if (index > 0) sb.append('\n');
    sb.append("}");
    return sb.toString();
  }

  private static void appendJsonValue(StringBuilder sb, Object value) {
    if (value == null) {
      sb.append("null");
    } else if (value instanceof Boolean || value instanceof Number) {
      sb.append(String.valueOf(value));
    } else {
      sb.append('"').append(escape(String.valueOf(value))).append('"');
    }
  }

  private static String escape(String text) {
    return text == null ? "" : text
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\r", "\\r")
        .replace("\t", "\\t");
  }

  private static Map<String, Object> parseJsonObject(String json) {
    Parser parser = new Parser(json);
    return parser.parseObject();
  }

  private static final class Parser {
    private final String input;
    private int index = 0;

    private Parser(String input) {
      this.input = input == null ? "" : input;
    }

    private Map<String, Object> parseObject() {
      Map<String, Object> out = new LinkedHashMap<>();
      skipWhitespace();
      if (!consume('{')) return out;
      skipWhitespace();
      while (index < input.length() && input.charAt(index) != '}') {
        String key = parseString();
        skipWhitespace();
        consume(':');
        skipWhitespace();
        out.put(key, parseValue());
        skipWhitespace();
        if (!consume(',')) break;
        skipWhitespace();
      }
      consume('}');
      return out;
    }

    private Object parseValue() {
      skipWhitespace();
      if (index >= input.length()) return null;
      char c = input.charAt(index);
      if (c == '"' || c == '\'') return parseString();
      if (startsWith("true")) {
        index += 4;
        return Boolean.TRUE;
      }
      if (startsWith("false")) {
        index += 5;
        return Boolean.FALSE;
      }
      if (startsWith("null")) {
        index += 4;
        return null;
      }
      return parseNumberOrString();
    }

    private Object parseNumberOrString() {
      int start = index;
      while (index < input.length()) {
        char c = input.charAt(index);
        if (c == ',' || c == '}' || Character.isWhitespace(c)) break;
        index++;
      }
      String token = input.substring(start, index).trim();
      if (token.isEmpty()) return "";
      try {
        if (token.contains(".")) return Double.parseDouble(token);
        return Integer.parseInt(token);
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        return token;
      }
    }

    private String parseString() {
      skipWhitespace();
      if (index >= input.length()) return "";
      char quote = input.charAt(index);
      if (quote != '"' && quote != '\'') return "";
      index++;
      StringBuilder sb = new StringBuilder();
      boolean escaped = false;
      while (index < input.length()) {
        char c = input.charAt(index++);
        if (escaped) {
          switch (c) {
            case 'n' -> sb.append('\n');
            case 'r' -> sb.append('\r');
            case 't' -> sb.append('\t');
            case '\\' -> sb.append('\\');
            case '"' -> sb.append('"');
            case '\'' -> sb.append('\'');
            default -> sb.append(c);
          }
          escaped = false;
          continue;
        }
        if (c == '\\') {
          escaped = true;
          continue;
        }
        if (c == quote) break;
        sb.append(c);
      }
      return sb.toString();
    }

    private boolean startsWith(String token) {
      return input.regionMatches(true, index, token, 0, token.length());
    }

    private boolean consume(char expected) {
      skipWhitespace();
      if (index >= input.length() || input.charAt(index) != expected) return false;
      index++;
      return true;
    }

    private void skipWhitespace() {
      while (index < input.length() && Character.isWhitespace(input.charAt(index))) {
        index++;
      }
    }
  }
}

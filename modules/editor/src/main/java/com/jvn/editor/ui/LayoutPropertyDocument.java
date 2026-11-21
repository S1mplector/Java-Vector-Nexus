package com.jvn.editor.ui;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/** Edits only the effective property while retaining comments, unrelated formatting and extensions. */
final class LayoutPropertyDocument {
  private record Entry(int from, int to, String key) {}

  static Properties read(String source) {
    Properties result = new Properties();
    try {
      result.load(new StringReader(source));
    } catch (IOException impossible) {
      throw new IllegalStateException(impossible);
    }
    return result;
  }

  static String edit(String source, Map<String, String> changes) {
    String newline = source.contains("\r\n") ? "\r\n" : "\n";
    List<Entry> entries = new ArrayList<>();
    Map<String, Integer> last = new HashMap<>();
    for (int from = 0; from < source.length();) {
      int to = from;
      String key = null;
      boolean continued;
      do {
        int end = source.indexOf('\n', to);
        int next = end < 0 ? source.length() : end + 1;
        String line = source.substring(to, end < 0 ? next : end);
        if (line.endsWith("\r")) line = line.substring(0, line.length() - 1);
        boolean comment = to == from && (line.stripLeading().startsWith("#") || line.stripLeading().startsWith("!"));
        int i = line.length();
        while (i > 0 && line.charAt(i - 1) == '\\') i--;
        continued = !comment && next < source.length() && (line.length() - i) % 2 == 1;
        to = next;
      } while (continued);
      Properties row = read(source.substring(from, to));
      if (!row.isEmpty()) {
        key = row.stringPropertyNames().iterator().next();
        last.put(key, from);
      }
      entries.add(new Entry(from, to, key));
      from = to;
    }
    StringBuilder out = new StringBuilder();
    for (Entry entry : entries) {
      String key = entry.key;
      if (key == null || !changes.containsKey(key)) out.append(source, entry.from, entry.to);
      else if (changes.get(key) != null) {
        if (entry.from != last.get(key)) out.append(source, entry.from, entry.to);
        else {
          out.append(escape(key, true)).append('=').append(escape(changes.get(key), false));
          if (source.charAt(entry.to - 1) == '\n') out.append(newline);
        }
      }
    }
    changes.forEach((key, value) -> {
      if (!last.containsKey(key) && value != null) {
        if (!out.isEmpty() && out.charAt(out.length() - 1) != '\n') out.append(newline);
        out.append(escape(key, true)).append('=').append(escape(value, false)).append(newline);
      }
    });
    return out.toString();
  }

  private static String escape(String value, boolean key) {
    StringBuilder out = new StringBuilder();
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      switch (c) {
        case '\\' -> out.append("\\\\");
        case '\n' -> out.append("\\n");
        case '\r' -> out.append("\\r");
        case '\t' -> out.append("\\t");
        case '\f' -> out.append("\\f");
        default -> {
          if ((c == ' ' && (i == 0 || key)) || (key && "=:#!".indexOf(c) >= 0)) out.append('\\');
          out.append(c);
        }
      }
    }
    return out.toString();
  }
}

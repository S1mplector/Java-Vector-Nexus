package com.jvn.plugin.runtime;

/** Small semantic-version matcher supporting exact, wildcard, comparison, and caret ranges. */
final class VersionRange {
  private VersionRange() {}

  static boolean accepts(String range, String version) {
    if (range == null || range.isBlank() || "*".equals(range.trim())) return true;
    int[] actual = parse(version);
    for (String clause : range.trim().split("\\s+")) {
      if (clause.endsWith(".x") || clause.endsWith(".*")) {
        String prefix = clause.substring(0, clause.length() - 2);
        int[] expected = parse(prefix);
        int specifiedParts = prefix.split("\\.").length;
        if (actual[0] != expected[0] || (specifiedParts > 1 && actual[1] != expected[1])) return false;
      } else if (clause.startsWith("^")) {
        int[] expected = parse(clause.substring(1));
        if (compare(actual, expected) < 0 || actual[0] != expected[0]) return false;
        // Before 1.0, the first non-zero component defines compatibility.
        String core = clause.substring(1).replaceFirst("^[vV]", "").split("[-+]", 2)[0];
        int specifiedParts = core.split("\\.").length;
        if (expected[0] == 0 && specifiedParts > 1 && actual[1] != expected[1]) return false;
        if (expected[0] == 0 && expected[1] == 0 && specifiedParts > 2 && actual[2] != expected[2]) return false;
      } else {
        String operator = clause.startsWith(">=") || clause.startsWith("<=") ? clause.substring(0, 2)
            : clause.startsWith(">") || clause.startsWith("<") || clause.startsWith("=") ? clause.substring(0, 1) : "=";
        int[] expected = parse(clause.substring(operator.equals("=") && !clause.startsWith("=") ? 0 : operator.length()));
        int comparison = compare(actual, expected);
        if ((">=".equals(operator) && comparison < 0) || ("<=".equals(operator) && comparison > 0)
            || (">".equals(operator) && comparison <= 0) || ("<".equals(operator) && comparison >= 0)
            || ("=".equals(operator) && comparison != 0)) return false;
      }
    }
    return true;
  }

  private static int[] parse(String value) {
    String clean = value == null ? "" : value.trim().replaceFirst("^[vV]", "").split("[-+]", 2)[0];
    String[] parts = clean.split("\\.");
    if (parts.length == 0 || parts[0].isBlank()) throw new IllegalArgumentException("Invalid version: " + value);
    int[] parsed = new int[] {0, 0, 0};
    for (int i = 0; i < Math.min(3, parts.length); i++) parsed[i] = Integer.parseInt(parts[i]);
    return parsed;
  }

  private static int compare(int[] left, int[] right) {
    for (int i = 0; i < 3; i++) {
      int comparison = Integer.compare(left[i], right[i]);
      if (comparison != 0) return comparison;
    }
    return 0;
  }
}

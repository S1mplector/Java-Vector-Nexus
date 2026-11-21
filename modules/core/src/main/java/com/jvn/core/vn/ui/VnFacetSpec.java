package com.jvn.core.vn.ui;

import java.util.List;

/**
 * A composable, reactive JVN UI tree loaded from a {@code .facet} file.
 *
 * <p>Facets deliberately build on the existing overlay lifecycle: they add
 * nested presentation nodes without introducing a second screen stack.</p>
 */
public record VnFacetSpec(String rootId, List<Node> nodes) {
  public VnFacetSpec {
    rootId = normalize(rootId, "root");
    nodes = nodes == null ? List.of() : List.copyOf(nodes);
  }

  public record Node(
      String id,
      Type type,
      String parent,
      String text,
      String value,
      String visibleIf,
      double x,
      double y,
      double width,
      double height,
      String source,
      String region
  ) {
    public Node(String id, Type type, String parent, String text, String value, String visibleIf,
                double x, double y, double width, double height) {
      this(id, type, parent, text, value, visibleIf, x, y, width, height, "", "");
    }

    public Node {
      id = normalize(id, "node");
      type = type == null ? Type.TEXT : type;
      parent = normalize(parent, "root");
      text = text == null ? "" : text.trim();
      value = value == null ? "" : value.trim();
      visibleIf = visibleIf == null ? "" : visibleIf.trim();
      source = normalize(source, "");
      region = normalize(region, "");
      x = sane(x, 0.0);
      y = sane(y, 0.0);
      width = sane(width, 1.0);
      height = sane(height, 0.1);
    }
  }

  public enum Type {
    GROUP, TEXT, IMAGE, BAR, CODE;

    public static Type parse(String raw) {
      if (raw == null || raw.isBlank()) return TEXT;
      try {
        return valueOf(raw.trim().toUpperCase(java.util.Locale.ROOT));
      } catch (IllegalArgumentException ignored) {
        return TEXT;
      }
    }
  }

  private static String normalize(String value, String fallback) {
    if (value == null || value.isBlank()) return fallback;
    return value.trim();
  }

  private static double sane(double value, double fallback) {
    return Double.isFinite(value) ? value : fallback;
  }
}

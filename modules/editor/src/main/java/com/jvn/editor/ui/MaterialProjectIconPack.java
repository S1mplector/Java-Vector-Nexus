package com.jvn.editor.ui;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import javafx.geometry.Bounds;
import javafx.scene.Group;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.Paint;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.transform.Affine;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** Previously bundled JVN Project Explorer SVG pack retained as the cross-platform default. */
final class MaterialProjectIconPack {
  private static final String RESOURCE_ROOT = "/com/jvn/editor/icons/material/";
  private static final Map<String, Optional<IconTemplate>> CACHE = new ConcurrentHashMap<>();

  private MaterialProjectIconPack() {}

  static Optional<Region> icon(String name, double size) {
    Optional<IconTemplate> template = CACHE.computeIfAbsent(normalizeName(name), MaterialProjectIconPack::loadTemplate);
    return template.map(value -> value.create(size));
  }

  static Optional<Region> folderEmblem(String name, double size) {
    Optional<IconTemplate> template = CACHE.computeIfAbsent(normalizeName(name), MaterialProjectIconPack::loadTemplate);
    return template.filter(value -> value.parts().size() > 1).map(value -> {
      List<SvgPart> marks = value.parts().stream().filter(part -> !"folder".equals(part.id()))
          .map(part -> new SvgPart(part.pathData(), Color.web("#35647e"), part.id())).toList();
      return new IconTemplate(value.viewBox(), marks).create(size);
    });
  }

  private static Optional<IconTemplate> loadTemplate(String name) {
    if (name.isBlank()) return Optional.empty();
    try (InputStream input = MaterialProjectIconPack.class.getResourceAsStream(RESOURCE_ROOT + name + ".svg")) {
      if (input == null) return Optional.empty();
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(false);
      factory.setExpandEntityReferences(false);
      setXmlFeature(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
      setXmlFeature(factory, "http://xml.org/sax/features/external-general-entities", false);
      setXmlFeature(factory, "http://xml.org/sax/features/external-parameter-entities", false);

      Document document = factory.newDocumentBuilder().parse(input);
      Element root = document.getDocumentElement();
      ViewBox viewBox = ViewBox.parse(root.getAttribute("viewBox"));
      String inheritedFill = root.hasAttribute("fill") ? root.getAttribute("fill") : "";
      List<SvgPart> parts = new ArrayList<>();
      collectParts(root, inheritedFill, parts);
      if (parts.isEmpty()) return Optional.empty();
      return Optional.of(new IconTemplate(viewBox, parts));
    } catch (Exception ex) {
      return Optional.empty();
    }
  }

  private static void setXmlFeature(DocumentBuilderFactory factory, String feature, boolean value) {
    try {
      factory.setFeature(feature, value);
    } catch (Exception ignored) {
      // Parser feature availability varies by JDK; the SVG resources are local and bundled.
    }
  }

  private static void collectParts(Element element, String inheritedFill, List<SvgPart> parts) {
    String fill = inheritedFill;
    if (element.hasAttribute("fill")) fill = element.getAttribute("fill");
    String styleFill = styleFill(element.getAttribute("style"));
    if (!styleFill.isBlank()) fill = styleFill;

    if ("path".equalsIgnoreCase(element.getTagName())) {
      String pathData = element.getAttribute("d");
      if (!pathData.isBlank() && !"none".equalsIgnoreCase(fill)) {
        Paint paint = parsePaint(fill).orElse(Color.web("#c6d1dc"));
        parts.add(new SvgPart(pathData, paint, element.getAttribute("id")));
      }
      return;
    }

    NodeList children = element.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (node instanceof Element child) collectParts(child, fill, parts);
    }
  }

  private static Optional<Paint> parsePaint(String fill) {
    if (fill == null || fill.isBlank() || "none".equalsIgnoreCase(fill)) return Optional.empty();
    try {
      return Optional.of(Color.web(fill.trim()));
    } catch (IllegalArgumentException ex) {
      return Optional.empty();
    }
  }

  private static String styleFill(String style) {
    if (style == null || style.isBlank()) return "";
    for (String declaration : style.split(";")) {
      int separator = declaration.indexOf(':');
      if (separator < 0) continue;
      String key = declaration.substring(0, separator).trim().toLowerCase(Locale.ROOT);
      if ("fill".equals(key)) return declaration.substring(separator + 1).trim();
    }
    return "";
  }

  private static String normalizeName(String name) {
    if (name == null) return "";
    return name.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "");
  }

  private record IconTemplate(ViewBox viewBox, List<SvgPart> parts) {
    private IconTemplate {
      parts = parts == null ? List.of() : List.copyOf(parts);
    }

    Region create(double requestedSize) {
      double size = requestedSize > 0 ? requestedSize : 18.0;
      Group group = new Group();
      for (SvgPart part : parts) {
        SVGPath path = new SVGPath();
        path.setContent(part.pathData());
        path.setFill(part.paint());
        group.getChildren().add(path);
      }

      Bounds bounds = group.getLayoutBounds();
      double minX = bounds.getMinX();
      double minY = bounds.getMinY();
      double width = bounds.getWidth();
      double height = bounds.getHeight();
      if (width <= 0.0 || height <= 0.0) {
        minX = viewBox.minX();
        minY = viewBox.minY();
        width = viewBox.width();
        height = viewBox.height();
      }

      double targetSize = size * 0.96;
      double scale = Math.min(targetSize / Math.max(1.0, width), targetSize / Math.max(1.0, height));
      double offsetX = (size - width * scale) / 2.0;
      double offsetY = (size - height * scale) / 2.0;
      Affine transform = new Affine();
      transform.setMxx(scale);
      transform.setMyy(scale);
      transform.setTx((-minX * scale) + offsetX);
      transform.setTy((-minY * scale) + offsetY);
      group.getTransforms().setAll(transform);

      Pane pane = new Pane(group);
      pane.setMinSize(size, size);
      pane.setPrefSize(size, size);
      pane.setMaxSize(size, size);
      pane.setClip(new Rectangle(size, size));
      pane.getStyleClass().add("project-material-icon");
      return pane;
    }
  }

  private record SvgPart(String pathData, Paint paint, String id) {
    private SvgPart {
      pathData = pathData == null ? "" : pathData;
      paint = paint == null ? Color.web("#c6d1dc") : paint;
    }
  }

  private record ViewBox(double minX, double minY, double width, double height) {
    static ViewBox parse(String raw) {
      if (raw == null || raw.isBlank()) return new ViewBox(0, 0, 16, 16);
      String[] parts = raw.trim().split("[\\s,]+");
      if (parts.length != 4) return new ViewBox(0, 0, 16, 16);
      try {
        double minX = Double.parseDouble(parts[0]);
        double minY = Double.parseDouble(parts[1]);
        double width = Math.max(1.0, Double.parseDouble(parts[2]));
        double height = Math.max(1.0, Double.parseDouble(parts[3]));
        return new ViewBox(minX, minY, width, height);
      } catch (NumberFormatException ex) {
        return new ViewBox(0, 0, 16, 16);
      }
    }
  }
}

package com.jvn.core.vn.ui;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.script.SyntaxRules;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** A literal excerpt from a project source file, with its original line numbers. */
public record FacetCodeExcerpt(String filename, SyntaxRules syntax, int firstLine, List<String> lines) {
  public FacetCodeExcerpt {
    lines = List.copyOf(lines);
  }

  public static FacetCodeExcerpt load(AssetCatalog assets, String source, String region) throws IOException {
    if (source == null || !source.matches("(?:[\\w .-]+/)*[\\w .-]+") ||
        Arrays.asList(source.split("/")).contains("..")) throw new IOException("Invalid code source");
    String filename = source.substring(source.lastIndexOf('/') + 1);
    String ext = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
    SyntaxRules syntax = switch (ext) {
      case "vns" -> SyntaxRules.VNS;
      case "jes" -> SyntaxRules.JES;
      case "properties", "facet", "layout", "menu", "style", "theme", "registry", "stagepreset" -> SyntaxRules.PROPERTIES;
      default -> throw new IOException("Unsupported code source: " + filename);
    };
    AssetType type = syntax == SyntaxRules.PROPERTIES ? AssetType.CONFIG : AssetType.SCRIPT;
    String prefix = type == AssetType.CONFIG ? "config/" : "scripts/";
    String path = source;
    if (!assets.exists(type, path) && source.startsWith(prefix)) path = source.substring(prefix.length());
    try (var in = assets.open(type, path)) {
      int limit = 256 * 1024;
      byte[] bytes = in.readNBytes(limit + 1);
      if (bytes.length > limit) throw new IOException("Code source exceeds 256 KiB: " + filename);
      List<String> lines = new String(bytes, StandardCharsets.UTF_8).lines().toList();
      if (region == null || region.isBlank()) return new FacetCodeExcerpt(filename, syntax, 1, lines);
      String comment = syntax == SyntaxRules.JES ? "// " : "# ";
      String begin = comment + "region " + region;
      String end = comment + "endregion";
      for (int i = 0; i < lines.size(); i++) {
        if (!lines.get(i).strip().equals(begin)) continue;
        for (int j = i + 1; j < lines.size(); j++) {
          if (lines.get(j).strip().equals(end))
            return new FacetCodeExcerpt(filename, syntax, i + 2, lines.subList(i + 1, j));
        }
        break;
      }
      throw new IOException("Missing code region " + region + " in " + filename);
    }
  }
}

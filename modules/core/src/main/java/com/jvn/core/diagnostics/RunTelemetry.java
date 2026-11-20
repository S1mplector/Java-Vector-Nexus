package com.jvn.core.diagnostics;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/** Versioned, single-line samples from an editor-launched FX runtime. */
public record RunTelemetry(String scene, double fps, double renderMs, long heapBytes, int width,
                           int height, int layerDraws, int otherDraws, int relights, double cpuPercent, List<Cache> caches) {
  public static final String PREFIX = "[JVN-INSPECT/1]\t";
  public static final String SESSION_ENV = "JVN_RUN_INSPECTOR";
  public static final String RELOAD = "reload";

  public static final String INVENTORY_REQUEST = "inspect-cache";
  public static final String INVENTORY_REPORT = "cache-inventory.txt";
  public static final String INVENTORY_READY = "[JVN-INSPECT] Cache inventory ready";

  public record Cache(String name, int entries, long bytes, long hits, long misses,
                      String recentResident, String lastMiss) {}

  public String encode() {
    var fields = new ArrayList<>(List.of(text(scene), Double.toString(fps), Double.toString(renderMs),
        Long.toString(heapBytes), Integer.toString(width), Integer.toString(height),
        Integer.toString(layerDraws), Integer.toString(otherDraws), Integer.toString(relights), Double.toString(cpuPercent)));
    for (Cache c : caches) fields.addAll(List.of(text(c.name), Integer.toString(c.entries),
        Long.toString(c.bytes), Long.toString(c.hits), Long.toString(c.misses),
        text(c.recentResident), text(c.lastMiss)));
    return PREFIX + String.join("\t", fields);
  }

  public static RunTelemetry decode(String line) {
    if (!line.startsWith(PREFIX) || line.length() > 65536) throw new IllegalArgumentException("Invalid sample");
    String[] f = line.substring(PREFIX.length()).split("\t", -1);
    if (f.length < 10 || (f.length - 10) % 7 != 0) throw new IllegalArgumentException("Incomplete sample");
    List<Cache> caches = new ArrayList<>();
    for (int i = 10; i < f.length; i += 7) caches.add(new Cache(read(f[i]), Integer.parseInt(f[i+1]),
        Long.parseLong(f[i+2]), Long.parseLong(f[i+3]), Long.parseLong(f[i+4]), read(f[i+5]), read(f[i+6])));
    return new RunTelemetry(read(f[0]), Double.parseDouble(f[1]), Double.parseDouble(f[2]),
        Long.parseLong(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), Integer.parseInt(f[6]),
        Integer.parseInt(f[7]), Integer.parseInt(f[8]), Double.parseDouble(f[9]), List.copyOf(caches));
  }

  private static String text(String s) {
    return Base64.getEncoder().encodeToString(s.substring(0, Math.min(s.length(), 512)).getBytes(StandardCharsets.UTF_8));
  }

  private static String read(String s) {
    return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
  }
}

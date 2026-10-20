package com.jvn.core.io;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {
  @TempDir Path directory;

  @Test
  void concurrentWritersNeverShareTemporaryFiles() throws Exception {
    Path destination = directory.resolve("save.json");
    CountDownLatch start = new CountDownLatch(1);
    var futures = new ArrayList<Future<?>>();
    try (var pool = Executors.newFixedThreadPool(8)) {
      for (int i = 0; i < 24; i++) {
        String text = Integer.toString(i).repeat(20000);
        futures.add(pool.submit(() -> {
          start.await();
          AtomicFiles.write(destination, text.getBytes(StandardCharsets.UTF_8));
          return null;
        }));
      }
      start.countDown();
      for (Future<?> future : futures) future.get(10, TimeUnit.SECONDS);
    }
    String actual = Files.readString(destination);
    assertTrue(java.util.stream.IntStream.range(0, 24)
        .anyMatch(i -> actual.equals(Integer.toString(i).repeat(20000))));
    try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
  }

  @Test
  void failedReplacementPreservesDestinationAndCleansTemporaryFile() throws Exception {
    Path destination = Files.createDirectory(directory.resolve("save.json"));
    Path existing = Files.writeString(destination.resolve("keep.txt"), "keep");
    assertThrows(IOException.class, () -> AtomicFiles.write(destination, new byte[] {1, 2}));
    assertEquals("keep", Files.readString(existing));
    try (var files = Files.list(directory)) { assertEquals(1, files.count()); }
  }
}

package com.jvn.core.io;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Replaces a file only after its complete contents have been written beside it. */
public final class AtomicFiles {
  private AtomicFiles() {}

  /**
   * Uses a unique sibling temporary file so independent writers cannot truncate
   * or delete each other's work. The last completed replacement wins. Falls back
   * to a regular replacement when the filesystem does not support atomic moves.
   */
  public static void write(Path destination, byte[] contents) throws IOException {
    Path target = destination.toAbsolutePath().normalize();
    Files.createDirectories(target.getParent());
    Path temporary = Files.createTempFile(target.getParent(), "." + target.getFileName() + "-", ".tmp");
    try {
      Files.write(temporary, contents);
      try {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
      } catch (AtomicMoveNotSupportedException ex) {
        Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
      }
    } finally {
      Files.deleteIfExists(temporary);
    }
  }
}

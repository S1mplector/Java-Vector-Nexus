package com.jvn.editor.ui.actioneditor;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.LinkedHashMap;
import javax.imageio.ImageIO;

/** Bounded, metadata-only sizing for scene import; changed files are re-read. */
public final class ImageDimensions {
    public record Size(double getWidth, double getHeight) {}
    private record Entry(long size, java.nio.file.attribute.FileTime modified, Size dimensions) {}
    private final LinkedHashMap<Path, Entry> cache = new LinkedHashMap<>(64, 0.75f, true);

    public synchronized Size read(String file) throws IOException {
        Path path = Path.of(file).toAbsolutePath().normalize();
        var attributes = Files.readAttributes(path, BasicFileAttributes.class);
        Entry cached = cache.get(path);
        if (cached != null && cached.size == attributes.size()
                && cached.modified.equals(attributes.lastModifiedTime())) return cached.dimensions;
        Size dimensions = null;
        try (var input = ImageIO.createImageInputStream(path.toFile())) {
            if (input != null) {
                var readers = ImageIO.getImageReaders(input);
                if (readers.hasNext()) {
                    var reader = readers.next();
                    try {
                        reader.setInput(input, true, true);
                        dimensions = new Size(reader.getWidth(0), reader.getHeight(0));
                    } finally { reader.dispose(); }
                }
            }
        }
        if (dimensions == null) {
            // Preserve JavaFX-supported formats without an installed ImageIO reader.
            var image = new javafx.scene.image.Image(path.toUri().toString(), false);
            dimensions = new Size(image.getWidth(), image.getHeight());
        }
        cache.put(path, new Entry(attributes.size(), attributes.lastModifiedTime(), dimensions));
        if (cache.size() > 1024) cache.remove(cache.keySet().iterator().next());
        return dimensions;
    }
}

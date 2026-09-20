package com.jvn.editor.ui.actioneditor;

import java.awt.image.BufferedImage;
import java.nio.file.Path;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ImageDimensionsTest {
    @Test void readsMetadataAndInvalidatesReplacedArt(@TempDir Path root) throws Exception {
        Path file = root.resolve("sprite.png");
        ImageIO.write(new BufferedImage(1920,1080,BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        var reader = new ImageDimensions();
        var first = reader.read(file.toString());
        assertEquals(1920, first.getWidth());
        assertEquals(1080, first.getHeight());
        assertSame(first, reader.read(file.toString()));
        ImageIO.write(new BufferedImage(512,1024,BufferedImage.TYPE_INT_ARGB), "png", file.toFile());
        assertEquals(new ImageDimensions.Size(512,1024), reader.read(file.toString()));
    }
}

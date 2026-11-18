package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import javax.swing.Icon;
import javax.swing.JCheckBox;
import org.junit.jupiter.api.Test;

class HubIconTest {
  @Test
  void everyRoleRendersDistinctArtworkAtMenuAndToolbarSizes() {
    for (int size : new int[] {14, 26, 52}) {
      Set<Integer> signatures = new HashSet<>();
      for (HubIcon.Kind kind : HubIcon.Kind.values()) {
        BufferedImage image = HubIcon.image(kind, size);
        int visible = 0;
        for (int y = 0; y < size; y++) {
          for (int x = 0; x < size; x++) {
            int alpha = image.getRGB(x, y) >>> 24;
            if (alpha > 36) visible++;
            if (size == 52 && (x == 0 || x == size - 1 || y == 0 || y == size - 1)) {
              assertTrue(alpha < 24, kind + " should leave room for its shadow");
            }
          }
        }
        assertTrue(visible >= size * size / 12, kind + " should read at " + size + " px");
        assertTrue(signatures.add(signature(image)), kind + " should have distinct artwork");
      }
      assertEquals(HubIcon.Kind.values().length, signatures.size());
    }
  }

  @Test
  void selectionIconsRespondToTheButtonModel() {
    JCheckBox box = new JCheckBox();
    Icon icon = HubIcon.selection(false, 16);
    int unchecked = signature(render(icon, box));
    box.setSelected(true);
    int checked = signature(render(icon, box));
    box.setEnabled(false);
    int disabled = signature(render(icon, box));
    assertNotEquals(unchecked, checked);
    assertNotEquals(checked, disabled);
  }

  private static BufferedImage render(Icon icon, JCheckBox box) {
    BufferedImage image = new BufferedImage(icon.getIconWidth(), icon.getIconHeight(), BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      icon.paintIcon(box, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }
    return image;
  }

  private static int signature(BufferedImage image) {
    return Arrays.hashCode(image.getRGB(0, 0, image.getWidth(), image.getHeight(), null, 0, image.getWidth()));
  }
}

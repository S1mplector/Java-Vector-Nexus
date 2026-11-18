package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import javax.swing.JMenuItem;
import javax.swing.ToolTipManager;
import org.junit.jupiter.api.Test;

class JvnHubAeroHelpIconTest {
  @Test
  void contextHelpIndicatorFollowsGlobalTooltipVisibility() {
    boolean previous = ToolTipManager.sharedInstance().isEnabled();
    JMenuItem item = new JMenuItem("Render Pipeline");
    item.setToolTipText("Configure rendering.");
    try {
      JvnHub.configureToolTipManager(true);
      assertTrue(JvnHub.hasContextHelp(item));
      JvnHub.configureToolTipManager(false);
      org.junit.jupiter.api.Assertions.assertFalse(JvnHub.hasContextHelp(item));
    } finally {
      JvnHub.configureToolTipManager(previous);
    }
  }

  @Test
  void helpOrbKeepsTheAeroPaletteAndQuestionHighlight() {
    HubIcon icon = HubIcon.of(HubIcon.Kind.HELP, 64);
    BufferedImage image = new BufferedImage(64, 64, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = image.createGraphics();
    try {
      icon.paintIcon(null, graphics, 0, 0);
    } finally {
      graphics.dispose();
    }

    int visible = 0;
    int glossyBlue = 0;
    int brightHighlight = 0;
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        Color color = new Color(image.getRGB(x, y), true);
        if (color.getAlpha() < 20) continue;
        visible++;
        if (color.getBlue() > color.getRed() + 25 && color.getBlue() > color.getGreen()) glossyBlue++;
        if (color.getRed() > 235 && color.getGreen() > 245 && color.getBlue() > 248) brightHighlight++;
      }
    }

    assertEquals(64, icon.getIconWidth());
    assertEquals(64, icon.getIconHeight());
    assertTrue(visible > 1_500, "the circular help artwork should be clearly visible");
    assertTrue(glossyBlue > 500, "the blue glass gradient should dominate the orb");
    assertTrue(brightHighlight > 40, "the white question mark, rim, and reflection should remain visible");
  }
}

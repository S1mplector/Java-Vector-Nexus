package com.jvn.hub;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.nio.file.*;
import java.util.jar.*;
import javax.imageio.ImageIO;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class HubPluginManagerUiTest {
  @TempDir Path temporary;

  @Test void rendersEmptyAndSelectedStatesWithHubControlsAndWorkingFilters() throws Exception {
    org.junit.jupiter.api.Assumptions.assumeFalse(GraphicsEnvironment.isHeadless());
    Path user = Files.createDirectories(temporary.resolve("user"));
    Path project = Files.createDirectories(temporary.resolve("project"));
    HubPluginManagerDialog[] dialog = new HubPluginManagerDialog[1];
    SwingUtilities.invokeAndWait(() -> {
      dialog[0] = new HubPluginManagerDialog(null, project, user, () -> {});
      dialog[0].setVisible(true);
    });
    try {
      waitForScan(dialog[0]);
      SwingUtilities.invokeAndWait(() -> {
        try {
          assertTrue(field(dialog[0], "emptyTitle", JLabel.class).isShowing());
          assertInstanceOf(JvnHub.FlatButton.class, field(dialog[0], "refreshButton", JButton.class));
          assertTrue(field(dialog[0], "scope", JComboBox.class).getUI().getClass().getName().contains("HubComboBoxUI"));
          capture(dialog[0], "plugin-manager-empty.png");
        } catch (Exception e) { throw new RuntimeException(e); }
      });
      try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(user.resolve("studio-tools.jar")))) {
        jar.putNextEntry(new JarEntry("jvn-plugin.json"));
        jar.write("""
            {"id":"dev.jvn.studio","name":"Studio Tools","version":"1.2.0",
             "jvnApi":"1.x","entrypoint":"dev.jvn.Studio","vendor":"JVN Labs",
             "description":"Timeline utilities and asset helpers for your visual novel workspace.",
             "capabilities":["editor.tool","asset.importer"]}
            """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        jar.closeEntry();
        jar.putNextEntry(new JarEntry("dev/jvn/Studio.class"));
        jar.write(new byte[]{0});
        jar.closeEntry();
      }
      SwingUtilities.invokeAndWait(() -> {
        try { field(dialog[0], "refreshButton", JButton.class).doClick(); }
        catch (Exception e) { throw new RuntimeException(e); }
      });
      waitForScan(dialog[0]);
      SwingUtilities.invokeAndWait(() -> {
        try {
          JList<?> list = field(dialog[0], "list", JList.class);
          assertEquals(1, list.getModel().getSize());
          list.setSelectedIndex(0);
          assertEquals("Studio Tools", field(dialog[0], "detailName", JLabel.class).getText());
          capture(dialog[0], "plugin-manager-selected.png");
          dialog[0].setSize(dialog[0].getMinimumSize());
          dialog[0].validate();
          capture(dialog[0], "plugin-manager-compact.png");
          field(dialog[0], "search", JTextField.class).setText("no match");
          assertEquals(0, list.getModel().getSize());
          assertEquals("No matching plugins", field(dialog[0], "emptyTitle", JLabel.class).getText());
        } catch (Exception e) { throw new RuntimeException(e); }
      });
    } finally { SwingUtilities.invokeAndWait(dialog[0]::dispose); }
  }

  private static void waitForScan(HubPluginManagerDialog dialog) throws Exception {
    for (int i = 0; i < 60; i++) {
      boolean[] done = {false};
      SwingUtilities.invokeAndWait(() -> {
        try { done[0] = field(dialog, "refreshButton", JButton.class).isEnabled(); }
        catch (Exception e) { throw new RuntimeException(e); }
      });
      if (done[0]) return;
      Thread.sleep(50);
    }
    fail("Plugin scan did not complete");
  }

  private static <T> T field(Object target, String name, Class<T> type) throws Exception {
    var field = target.getClass().getDeclaredField(name);
    field.setAccessible(true);
    return type.cast(field.get(target));
  }

  private static void capture(JDialog dialog, String name) throws Exception {
    String output = System.getenv("JVN_PLUGIN_PREVIEW");
    if (output == null) return;
    dialog.validate();
    var content = dialog.getContentPane();
    BufferedImage image = new BufferedImage(content.getWidth(), content.getHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D graphics = image.createGraphics();
    content.printAll(graphics);
    graphics.dispose();
    Path path = Path.of(output).resolve(name);
    Files.createDirectories(path.getParent());
    ImageIO.write(image, "png", path.toFile());
  }
}

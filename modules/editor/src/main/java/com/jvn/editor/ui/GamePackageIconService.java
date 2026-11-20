package com.jvn.editor.ui;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.io.DataOutputStream;
import java.util.Locale;
import javax.imageio.ImageIO;

final class GamePackageIconService {
  static final int MIN_SOURCE_SIZE = 512;

  record IconResult(File source, File platformIcon, int width, int height) {}

  private GamePackageIconService() {}

  static IconResult install(File pngSource, File projectRoot, String hostOs) throws IOException, InterruptedException {
    BufferedImage image = readAndValidate(pngSource);
    File packagingDir = new File(projectRoot, "packaging");
    Files.createDirectories(packagingDir.toPath());
    File storedSource = new File(packagingDir, "icon-source.png");
    if (!pngSource.getCanonicalFile().equals(storedSource.getCanonicalFile())) {
      Files.copy(pngSource.toPath(), storedSource.toPath(), StandardCopyOption.REPLACE_EXISTING);
    }

    File platformIcon;
    switch (normalizeHost(hostOs)) {
      case "macos" -> {
        platformIcon = new File(packagingDir, "icon.icns");
        writeIcns(image, platformIcon);
      }
      case "windows" -> {
        platformIcon = new File(packagingDir, "icon.ico");
        writeIco(image, platformIcon);
      }
      default -> {
        platformIcon = new File(packagingDir, "icon.png");
        Files.copy(storedSource.toPath(), platformIcon.toPath(), StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return new IconResult(storedSource, platformIcon, image.getWidth(), image.getHeight());
  }

  static BufferedImage readAndValidate(File source) throws IOException {
    if (source == null || !source.isFile()) throw new IOException("Choose a readable PNG image.");
    if (!source.getName().toLowerCase(Locale.ROOT).endsWith(".png")) {
      throw new IOException("Game icon source must be a PNG image.");
    }
    BufferedImage image = ImageIO.read(source);
    if (image == null) throw new IOException("The selected file is not a readable PNG image.");
    if (image.getWidth() != image.getHeight()) {
      throw new IOException("Game icon must be square; selected image is " + image.getWidth() + "x" + image.getHeight() + ".");
    }
    if (image.getWidth() < MIN_SOURCE_SIZE) {
      throw new IOException("Game icon must be at least " + MIN_SOURCE_SIZE + "x" + MIN_SOURCE_SIZE + ".");
    }
    if (!image.getColorModel().hasAlpha() || !hasTransparentPixel(image)) {
      throw new IOException("Game icon must include transparent pixels around the artwork.");
    }
    return image;
  }

  static void writeIco(BufferedImage source, File destination) throws IOException {
    BufferedImage icon = resize(source, 256);
    ByteArrayOutputStream png = new ByteArrayOutputStream();
    if (!ImageIO.write(icon, "png", png)) throw new IOException("Could not encode Windows icon PNG data.");
    byte[] data = png.toByteArray();
    ByteBuffer header = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN);
    header.putShort((short) 0).putShort((short) 1).putShort((short) 1);
    header.put((byte) 0).put((byte) 0).put((byte) 0).put((byte) 0);
    header.putShort((short) 1).putShort((short) 32);
    header.putInt(data.length).putInt(22);
    Files.createDirectories(destination.toPath().getParent());
    try (var output = Files.newOutputStream(destination.toPath())) {
      output.write(header.array());
      output.write(data);
    }
  }

  private static void writeIcns(BufferedImage source, File destination) throws IOException {
    int[] sizes = {16, 32, 128, 256, 512, 1024};
    String[] types = {"icp4", "icp5", "ic07", "ic08", "ic09", "ic10"};
    var chunks = new ByteArrayOutputStream();
    try (var content = new DataOutputStream(chunks)) {
      for (int i = 0; i < sizes.length; i++) {
        var png = new ByteArrayOutputStream();
        if (!ImageIO.write(resize(source, sizes[i]), "png", png)) throw new IOException("PNG encoder unavailable");
        content.writeBytes(types[i]);
        content.writeInt(8 + png.size());
        png.writeTo(content);
      }
    }
    Path temp = Files.createTempFile(destination.toPath().getParent(), "icon-", ".icns");
    try {
      try (var output = new DataOutputStream(Files.newOutputStream(temp))) {
        output.writeBytes("icns");
        output.writeInt(8 + chunks.size());
        chunks.writeTo(output);
      }
      Files.move(temp, destination.toPath(), StandardCopyOption.REPLACE_EXISTING);
    } finally {
      Files.deleteIfExists(temp);
    }
  }

  private static BufferedImage resize(BufferedImage source, int size) {
    BufferedImage target = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    Graphics2D graphics = target.createGraphics();
    try {
      graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
      graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
      graphics.drawImage(source, 0, 0, size, size, null);
    } finally {
      graphics.dispose();
    }
    return target;
  }

  private static boolean hasTransparentPixel(BufferedImage image) {
    for (int y = 0; y < image.getHeight(); y++) {
      for (int x = 0; x < image.getWidth(); x++) {
        if ((image.getRGB(x, y) >>> 24) < 255) return true;
      }
    }
    return false;
  }

  private static String normalizeHost(String hostOs) {
    String value = hostOs == null ? "" : hostOs.toLowerCase(Locale.ROOT);
    if (value.contains("mac")) return "macos";
    if (value.contains("win")) return "windows";
    return "linux";
  }

}

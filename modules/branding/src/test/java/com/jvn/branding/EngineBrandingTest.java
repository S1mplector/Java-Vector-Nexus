package com.jvn.branding;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class EngineBrandingTest {
  @Test void rendersOfficialSvgAndUsesActualBuildVersion() {
    assertEquals("0.8.0", EngineBranding.version());
    assertEquals("Made with JVN (0.8.0)", EngineBranding.accessibleCredit());
    assertTrue(new String(EngineBranding.svg(), java.nio.charset.StandardCharsets.UTF_8).contains("<svg"));
    var image = EngineBranding.logo();
    assertEquals(760, image.getWidth());
    long visible = 0;
    for (int y=0;y<image.getHeight();y++) for(int x=0;x<image.getWidth();x++)
      if ((image.getRGB(x,y) >>> 24) > 64) visible++;
    assertTrue(visible > 10000, "SVG must render actual visible logo pixels");
  }
}

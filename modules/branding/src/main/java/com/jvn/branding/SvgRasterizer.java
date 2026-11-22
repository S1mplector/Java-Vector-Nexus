package com.jvn.branding;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import org.apache.batik.transcoder.TranscoderException;
import org.apache.batik.transcoder.TranscoderInput;
import org.apache.batik.transcoder.TranscoderOutput;
import org.apache.batik.transcoder.image.ImageTranscoder;
import org.xml.sax.SAXException;

/** Renders self-contained SVG artwork for the engine's image surfaces. */
public final class SvgRasterizer {
  private static final int MAX_SVG_BYTES = 256 * 1024;

  private SvgRasterizer() {}

  public static BufferedImage render(InputStream input, int width) throws IOException {
    byte[] bytes = input.readNBytes(MAX_SVG_BYTES + 1);
    if (bytes.length > MAX_SVG_BYTES) throw new IOException("SVG is too large");
    if (width < 1 || width > 1024) throw new IOException("SVG raster size is out of range");

    try {
      var factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      factory.setXIncludeAware(false);
      var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes));
      var root = document.getDocumentElement();
      if (!"svg".equals(root.getLocalName()) || !"http://www.w3.org/2000/svg".equals(root.getNamespaceURI())) {
        throw new IOException("Expected an SVG document");
      }
      String[] viewBox = root.getAttribute("viewBox").trim().split("[,\\s]+");
      if (viewBox.length == 4 && !root.hasAttribute("width") && !root.hasAttribute("height")) {
        root.setAttribute("width", viewBox[2]);
        root.setAttribute("height", viewBox[3]);
      }

      var transcoder = new BufferedImageTranscoder();
      transcoder.addTranscodingHint(ImageTranscoder.KEY_WIDTH, (float) width);
      transcoder.addTranscodingHint(ImageTranscoder.KEY_MAX_WIDTH, (float) width);
      transcoder.addTranscodingHint(ImageTranscoder.KEY_MAX_HEIGHT, (float) width);
      transcoder.addTranscodingHint(ImageTranscoder.KEY_EXECUTE_ONLOAD, false);
      transcoder.addTranscodingHint(ImageTranscoder.KEY_ALLOW_EXTERNAL_RESOURCES, false);
      transcoder.transcode(new TranscoderInput(document), null);
      if (transcoder.image == null) throw new IOException("SVG produced no image");
      return transcoder.image;
    } catch (ParserConfigurationException | SAXException | TranscoderException ex) {
      throw new IOException("Cannot render SVG", ex);
    }
  }

  private static final class BufferedImageTranscoder extends ImageTranscoder {
    private BufferedImage image;

    @Override public BufferedImage createImage(int width, int height) {
      return new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    @Override public void writeImage(BufferedImage result, TranscoderOutput output) {
      image = result;
    }
  }
}

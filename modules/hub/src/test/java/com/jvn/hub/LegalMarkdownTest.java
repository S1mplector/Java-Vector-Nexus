package com.jvn.hub;

import static org.junit.jupiter.api.Assertions.*;
import com.jvn.licensing.LicenseTerms;
import java.io.StringReader;
import javax.swing.text.html.HTMLEditorKit;
import javax.swing.text.html.parser.ParserDelegator;
import org.junit.jupiter.api.Test;

class LegalMarkdownTest {
  @Test void rendersHeadingsListsAndInlineFormatting() {
    String html = LegalMarkdown.render("# Title\n\n## Section\n\nA **bold** and *italic* `path`.\n\n1. First\n   continued\n2. Second");
    assertTrue(html.contains("<h1>Title</h1>"));
    assertTrue(html.contains("<h2>Section</h2>"));
    assertTrue(html.contains("<strong>bold</strong>"));
    assertTrue(html.contains("<em>italic</em>"));
    assertTrue(html.contains("<code>path</code>"));
    assertTrue(html.contains("<ol><li>First continued</li><li>Second</li></ol>"));
    assertTrue(LegalMarkdown.render("- First\n- Second").contains("<ul><li>First</li><li>Second</li></ul>"));
  }

  @Test void treatsSourceHtmlAsText() {
    String html = LegalMarkdown.render("<img src=\"https://example.com/tracker\"> & text");
    assertFalse(html.contains("<img"));
    assertTrue(html.contains("&lt;img"));
    assertTrue(html.contains("&amp;"));
  }

  @Test void preservesEveryBundledDocumentWord() throws Exception {
    LicenseTerms terms = LicenseTerms.bundled();
    for (String source : new String[] {terms.license(), terms.terms()}) {
      StringBuilder visible = new StringBuilder();
      new ParserDelegator().parse(new StringReader(LegalMarkdown.render(source)),
          new HTMLEditorKit.ParserCallback() {
            @Override public void handleText(char[] data, int pos) { visible.append(data).append(' '); }
          }, true);
      String expected = source.replaceAll("(?m)^\\d+[.)] +", "")
          .replaceAll("(?m)^#{1,6} +", "").replace("`", "");
      assertEquals(expected.replaceAll("\\s+", " ").trim(), visible.toString().replaceAll("\\s+", " ").trim());
    }
  }
}

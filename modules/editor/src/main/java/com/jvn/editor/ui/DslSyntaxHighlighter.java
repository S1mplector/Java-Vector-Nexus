package com.jvn.editor.ui;

import com.jvn.core.script.SyntaxRules;

import java.util.Collection;
import java.util.List;

import org.fxmisc.richtext.CodeArea;
import org.fxmisc.richtext.model.StyleSpans;
import org.fxmisc.richtext.model.StyleSpansBuilder;

/**
 * Reusable syntax highlighter for JVN properties-based DSL files
 * (.menu, .layout, .style, dialogue.layout, .registry, .theme, etc.).
 *
 * Recognises:
 * <ul>
 *   <li>{@code # comment} lines → <b>comment</b></li>
 *   <li>Property keys (everything before {@code =}) → <b>prop-key</b></li>
 *   <li>Known DSL directives/keywords → <b>keyword</b></li>
 *   <li>Quoted strings → <b>string</b></li>
 *   <li>Numeric literals → <b>number</b></li>
 *   <li>Punctuation ({@code = , . :}) → <b>punct</b></li>
 * </ul>
 *
 * Usage:
 * <pre>{@code
 *   DslSyntaxHighlighter highlighter = DslSyntaxHighlighter.properties();
 *   highlighter.install(codeArea);
 * }</pre>
 */
public final class DslSyntaxHighlighter {

  private DslSyntaxHighlighter() {}

  /** Install this highlighter on a {@link CodeArea} so it re-highlights on every text change. */
  public void install(CodeArea codeArea) {
    codeArea.textProperty().addListener((obs, oldText, newText) -> {
      codeArea.setStyleSpans(0, computeHighlighting(newText == null ? "" : newText));
    });
    codeArea.setStyleSpans(0, computeHighlighting(codeArea.getText() == null ? "" : codeArea.getText()));
  }

  /** Compute style spans for the given text. */
  public StyleSpans<Collection<String>> computeHighlighting(String text) {
    return spans(SyntaxRules.PROPERTIES, text);
  }

  static StyleSpans<Collection<String>> spans(SyntaxRules syntax, String text) {
    StyleSpansBuilder<Collection<String>> builder = new StyleSpansBuilder<>();
    syntax.scan(text, (start, end, style) ->
        builder.add(style.isEmpty() ? List.of() : List.of(style), end - start));
    return builder.create();
  }

  private static final DslSyntaxHighlighter PROPERTIES_INSTANCE = new DslSyntaxHighlighter();

  /**
   * Returns a highlighter for JVN properties-based DSL files.
   * Thread-safe, stateless singleton — safe to share across editors.
   */
  public static DslSyntaxHighlighter properties() {
    return PROPERTIES_INSTANCE;
  }
}

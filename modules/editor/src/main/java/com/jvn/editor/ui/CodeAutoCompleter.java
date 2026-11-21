package com.jvn.editor.ui;

import javafx.application.Platform;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.stage.Popup;
import org.fxmisc.richtext.CodeArea;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

public class CodeAutoCompleter {
  public static class Suggestion {
    public final String insert;
    public final String label;
    public Suggestion(String insert) { this(insert, insert); }
    public Suggestion(String insert, String label) { this.insert = insert; this.label = label; }
    @Override public String toString() { return label; }
  }

  public static class Context {
    public final String text;
    public final int caret;
    public final String prefix; 
    public final File projectRoot;
    public Context(String text, int caret, String prefix, File projectRoot) {
      this.text = text; this.caret = caret; this.prefix = prefix; this.projectRoot = projectRoot;
    }
  }

  public interface Provider { List<Suggestion> suggest(Context ctx); }

  private final CodeArea area;
  private final Provider provider;
  private final Popup popup = new Popup();
  private final ListView<Suggestion> list = new ListView<>();
  private File projectRoot;
  private int refreshVersion;

  public CodeAutoCompleter(CodeArea area, Provider provider) {
    this.area = area; this.provider = provider;
    list.setPrefWidth(320);
    list.setFixedCellSize(26);
    list.getStyleClass().add("code-completion-list");
    list.setFocusTraversable(false);
    list.setCellFactory(v -> new ListCell<>() {
      @Override protected void updateItem(Suggestion it, boolean empty) {
        super.updateItem(it, empty);
        setText(empty || it == null ? null : it.label);
      }
    });
    popup.getContent().add(list);
    popup.setAutoHide(true);

    area.addEventFilter(KeyEvent.KEY_PRESSED, this::handleKeyPressed);
    area.addEventFilter(KeyEvent.KEY_TYPED, e -> {
      if (!e.isControlDown() && !e.isAltDown() && !e.isMetaDown()
          && e.getCharacter().length() == 1 && triggersCompletion(e.getCharacter().charAt(0))) {
        queueRefresh();
      }
    });
    area.focusedProperty().addListener((obs, old, focused) -> {
      if (!focused) dismiss();
    });
    area.caretBoundsProperty().addListener((obs, old, bounds) -> {
      if (!popup.isShowing()) return;
      if (bounds.isEmpty()) { dismiss(); return; }
      popup.setX(bounds.get().getMinX());
      popup.setY(bounds.get().getMaxY());
    });
    area.caretPositionProperty().addListener((obs, old, caret) -> popup.hide());
    area.addEventFilter(javafx.scene.input.MouseEvent.MOUSE_PRESSED, e -> dismiss());
    list.setOnMouseClicked(e -> { if (e.getClickCount() == 2) commitSelected(); });
  }

  public void setProjectRoot(File root) { this.projectRoot = root; }

  private void handleKeyPressed(KeyEvent e) {
    if (e.getCode() == KeyCode.SPACE && (e.isControlDown() || e.isMetaDown())) {
      refreshVersion++;
      showInternal();
      e.consume();
      return;
    }
    if (popup.isShowing()) {
      switch (e.getCode()) {
        case ENTER, TAB -> commitSelected();
        case ESCAPE -> dismiss();
        case UP -> list.getSelectionModel().selectPrevious();
        case DOWN -> list.getSelectionModel().selectNext();
        case PAGE_UP -> list.getSelectionModel().selectFirst();
        case PAGE_DOWN -> list.getSelectionModel().selectLast();
        case BACK_SPACE, DELETE -> { queueRefresh(); return; }
        default -> { dismiss(); return; }
      }
      list.scrollTo(list.getSelectionModel().getSelectedIndex());
      e.consume();
    } else {
      refreshVersion++;
    }
  }

  private static boolean triggersCompletion(char c) {
    return tokenChar(c) || "@[\" :".indexOf(c) >= 0;
  }

  private void dismiss() {
    refreshVersion++;
    popup.hide();
  }

  private void queueRefresh() {
    int version = ++refreshVersion;
    Platform.runLater(() -> {
      if (refreshVersion == version && area.isFocused()) showInternal();
    });
  }

  private void showInternal() {
    if (area.getSelection().getLength() != 0 || area.getScene() == null || area.getScene().getWindow() == null) {
      dismiss();
      return;
    }
    int caret = area.getCaretPosition();
    String text = area.getText();
    List<Suggestion> suggestions = provider.suggest(new Context(text, caret, currentPrefix(text, caret), projectRoot));
    var bounds = area.getCaretBounds();
    if (suggestions.isEmpty() || bounds.isEmpty()) { dismiss(); return; }

    list.getItems().setAll(suggestions);
    list.getSelectionModel().selectFirst();
    list.setPrefHeight(Math.min(8, suggestions.size()) * list.getFixedCellSize() + 6);
    var anchor = bounds.get(); // RichTextFX supplies screen coordinates.
    if (popup.isShowing()) {
      popup.setX(anchor.getMinX());
      popup.setY(anchor.getMaxY());
    } else {
      list.getStylesheets().setAll(EditorTheme.stylesheetUrl());
      popup.show(area, anchor.getMinX(), anchor.getMaxY());
    }
  }

  static int openQuote(String text, int caret) {
    int quote = -1;
    for (int i = text.lastIndexOf('\n', caret - 1) + 1; i < caret; i++) {
      char c = text.charAt(i);
      if (c == '\\' && quote >= 0) i++;
      else if (c == '"') quote = quote < 0 ? i : -1;
    }
    return quote;
  }

  static String currentPrefix(String text, int caret) {
    int quote = openQuote(text, caret);
    if (quote >= 0) return text.substring(quote + 1, caret);
    int start = caret;
    while (start > 0 && tokenChar(text.charAt(start - 1))) start--;
    if (start > 0 && (text.charAt(start - 1) == '[' || text.charAt(start - 1) == '@')) start--;
    return text.substring(start, caret);
  }

  private static boolean tokenChar(char c) {
    return Character.isLetterOrDigit(c) || "_./-=".indexOf(c) >= 0;
  }

  static int replacementEnd(String text, int caret, String insert) {
    int end = caret;
    if (openQuote(text, caret) >= 0) {
      while (end < text.length() && text.charAt(end) != '\n' && text.charAt(end) != '\r' && text.charAt(end) != '"') {
        if (text.charAt(end) == '\\' && end + 1 < text.length() && text.charAt(end + 1) != '\n') end++;
        end++;
      }
      return end;
    }
    // A key-only suggestion should preserve a value that was already typed.
    if (insert.endsWith("=") && currentPrefix(text, caret).endsWith("=")) return caret;
    while (end < text.length() && tokenChar(text.charAt(end))) {
      if (insert.endsWith("=") && text.charAt(end) == '=') return end + 1;
      end++;
    }
    if (insert.endsWith(": ") && end < text.length() && text.charAt(end) == ':') end++;
    if (!insert.isEmpty() && end < text.length() && insert.charAt(insert.length() - 1) == text.charAt(end)) end++;
    return end;
  }

  private void commitSelected() {
    Suggestion suggestion = list.getSelectionModel().getSelectedItem();
    dismiss();
    if (suggestion == null) return;
    int caret = area.getCaretPosition();
    String text = area.getText();
    int start = caret - currentPrefix(text, caret).length();
    area.replaceText(start, replacementEnd(text, caret, suggestion.insert), suggestion.insert);
    area.requestFocus();
  }

  /** Extensionless IDs relative to the asset subdirectory, as used by existing providers. */
  public static List<String> listAssetIds(File root, String subDir, String... exts) {
    if (root == null) return List.of();
    Path base = new File(root, subDir).toPath();
    List<String> ids = new ArrayList<>();
    for (String path : listAssetPaths(root, subDir, 2, exts)) {
      String id = base.relativize(root.toPath().resolve(path)).toString().replace('\\', '/');
      int dot = id.lastIndexOf('.');
      ids.add(dot > 0 ? id.substring(0, dot) : id);
    }
    Collections.sort(ids);
    return ids;
  }

  static List<String> listAssetPaths(File root, String subDir, String... exts) {
    return listAssetPaths(root, subDir, Integer.MAX_VALUE, exts);
  }

  private static List<String> listAssetPaths(File root, String subDir, int maxDepth, String... exts) {
    try {
      if (root == null) return List.of();
      File d = new File(root, subDir);
      if (!d.exists()) return List.of();
      List<String> out = new ArrayList<>();
      try (Stream<Path> files = Files.walk(d.toPath(), maxDepth)) {
        files.filter(Files::isRegularFile).forEach(p -> {
          String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
          for (String e : exts) {
            if (n.endsWith(e)) {
              String id = root.toPath().relativize(p).toString().replace('\\','/');
              out.add(id);
              break;
            }
          }
        });
      }
      Collections.sort(out);
      return out;
    } catch (Exception ignore) { return List.of(); }
  }
}

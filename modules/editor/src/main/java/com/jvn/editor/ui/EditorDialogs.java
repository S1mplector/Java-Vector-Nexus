package com.jvn.editor.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.control.TitledPane;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

public final class EditorDialogs {
  public enum ButtonStyle {
    NEUTRAL,
    ACCENT,
    DANGER
  }

  private enum ErrorType {
    PROJECT_CONTEXT("Project context"),
    FILE_IO("File or disk"),
    DSL_PARSE("DSL parse"),
    JAVA_CODE("Java code"),
    EDITOR_RUNTIME("Editor runtime"),
    PROCESS("Process / Gradle"),
    ASSET("Asset"),
    AUDIO("Audio"),
    MEMORY("JVM memory"),
    VALIDATION("Validation"),
    CLIPBOARD("Clipboard"),
    UNKNOWN("Unknown");

    private final String label;

    ErrorType(String label) {
      this.label = label;
    }

    String label() {
      return label;
    }
  }

  public static final class ActionSpec {
    private final String id;
    private final String label;
    private final ButtonStyle style;
    private final boolean closeOnAction;
    private final boolean defaultFocus;
    private final Runnable action;

    private ActionSpec(String id,
                       String label,
                       ButtonStyle style,
                       boolean closeOnAction,
                       boolean defaultFocus,
                       Runnable action) {
      this.id = (id == null || id.isBlank()) ? "ok" : id.trim();
      this.label = (label == null || label.isBlank()) ? "OK" : label.trim();
      this.style = style == null ? ButtonStyle.NEUTRAL : style;
      this.closeOnAction = closeOnAction;
      this.defaultFocus = defaultFocus;
      this.action = action != null ? action : () -> {};
    }

    public static ActionSpec neutral(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.NEUTRAL, true, false, action);
    }

    public static ActionSpec accent(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.ACCENT, true, true, action);
    }

    public static ActionSpec danger(String id, String label, Runnable action) {
      return new ActionSpec(id, label, ButtonStyle.DANGER, true, false, action);
    }

    public ActionSpec closeOnAction(boolean value) {
      return new ActionSpec(id, label, style, value, defaultFocus, action);
    }

    public ActionSpec defaultFocus(boolean value) {
      return new ActionSpec(id, label, style, closeOnAction, value, action);
    }
  }

  private EditorDialogs() {}

  public static Optional<String> show(Window owner,
                                      String title,
                                      String message,
                                      Node content,
                                      Node focusTarget,
                                      ActionSpec... actions) {
    AtomicReference<String> result = new AtomicReference<>();
    Stage stage = createStage(owner, title, message, content, focusTarget, result, actions);
    stage.showAndWait();
    return Optional.ofNullable(result.get());
  }

  public static Optional<String> show(Window owner,
                                      String title,
                                      String message,
                                      Node content,
                                      ActionSpec... actions) {
    return show(owner, title, message, content, null, actions);
  }

  public static void info(Window owner, String title, String message) {
    showNotice(owner, title, message, DialogIcon.Kind.INFO);
  }

  public static void warning(Window owner, String title, String message) {
    showNotice(owner, title, message, DialogIcon.Kind.WARNING);
  }

  public static void error(Window owner, String title, String message) {
    error(owner, title, message, null);
  }

  public static void error(Window owner,
                           String title,
                           String message,
                           Throwable cause,
                           String... recoveryHints) {
    ErrorDialogModel model = buildErrorDialogModel(title, message, cause, recoveryHints);
    Stage stage = createStage(owner, normalizeDialogText(title, "Editor error"), model.summary(),
        model.content(), null, new AtomicReference<>(), DialogIcon.Kind.ERROR,
        ActionSpec.neutral("copy", "Copy Details", () -> copyToClipboard(model.report())).closeOnAction(false),
        ActionSpec.accent("close", "Close", null));
    stage.showAndWait();
  }

  private static void showNotice(Window owner, String title, String message, DialogIcon.Kind icon) {
    createStage(owner, title, message, null, null, new AtomicReference<>(), icon,
        ActionSpec.accent("close", "Close", null)).showAndWait();
  }

  public static boolean confirm(Window owner,
                                String title,
                                String message,
                                String confirmLabel,
                                boolean danger) {
    ActionSpec cancel = ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(true);
    ActionSpec confirm = danger
        ? ActionSpec.danger("confirm", confirmLabel == null ? "Confirm" : confirmLabel, null)
        : ActionSpec.accent("confirm", confirmLabel == null ? "Confirm" : confirmLabel, null);
    return show(owner, title, message, null, cancel, confirm).filter("confirm"::equals).isPresent();
  }

  public static Optional<String> promptText(Window owner,
                                            String title,
                                            String message,
                                            String labelText,
                                            String initialValue,
                                            String promptText,
                                            String confirmLabel) {
    TextField field = new TextField(initialValue == null ? "" : initialValue);
    field.setPromptText(promptText == null ? "" : promptText);
    field.getStyleClass().add("editor-dialog-text-field");

    Label fieldLabel = new Label(labelText == null || labelText.isBlank() ? "Value" : labelText);
    fieldLabel.getStyleClass().add("editor-dialog-field-label");

    VBox content = new VBox(8, fieldLabel, field);
    content.getStyleClass().add("editor-dialog-form");

    Optional<String> result = show(
        owner,
        title,
        message,
        content,
        field,
        ActionSpec.neutral("cancel", "Cancel", null).defaultFocus(false),
        ActionSpec.accent("submit", confirmLabel == null ? "OK" : confirmLabel, null).defaultFocus(true));
    if (result.filter("submit"::equals).isPresent()) {
      return Optional.ofNullable(field.getText());
    }
    return Optional.empty();
  }

  public static <T> Optional<T> choose(Window owner,
                                       String title,
                                       String message,
                                       List<T> options,
                                       T initialValue,
                                       Function<T, String> labeler,
                                       String confirmLabel) {
    if (options == null || options.isEmpty()) return Optional.empty();
    ListView<T> listView = new ListView<>();
    listView.getItems().setAll(options);
    listView.getStyleClass().add("editor-dialog-choice-list");
    listView.setCellFactory(v -> new ListCell<>() {
      @Override
      protected void updateItem(T item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          return;
        }
        setText(labeler == null ? String.valueOf(item) : labeler.apply(item));
      }
    });
    listView.setPrefHeight(Math.min(320, 48 + options.size() * 30));
    if (initialValue != null) {
      listView.getSelectionModel().select(initialValue);
    } else {
      listView.getSelectionModel().selectFirst();
    }

    Optional<String> result = show(
        owner,
        title,
        message,
        listView,
        listView,
        ActionSpec.neutral("cancel", "Cancel", null),
        ActionSpec.accent("choose", confirmLabel == null ? "Choose" : confirmLabel, null));
    if (result.filter("choose"::equals).isPresent()) {
      return Optional.ofNullable(listView.getSelectionModel().getSelectedItem());
    }
    return Optional.empty();
  }

  public static void showTextBlock(Window owner,
                                   String title,
                                   String message,
                                   String body,
                                   String closeLabel) {
    TextArea area = new TextArea(body == null ? "" : body);
    area.setEditable(false);
    area.setWrapText(true);
    area.getStyleClass().add("editor-dialog-text-area");
    area.setPrefRowCount(16);
    show(owner, title, message, area, ActionSpec.accent("close", closeLabel == null ? "Close" : closeLabel, null));
  }

  private static ErrorDialogModel buildErrorDialogModel(String title,
                                                        String message,
                                                        Throwable cause,
                                                        String... recoveryHints) {
    String summary = normalizeDialogText(message, "The operation could not be completed.");
    Throwable root = rootCause(cause);
    ErrorType type = classifyError(summary, root, title);
    List<String> hints = recoveryHints(type, summary, root, recoveryHints);

    VBox content = new VBox(12);
    content.getStyleClass().add("editor-dialog-error-content");

    if (root != null) {
      Label causeLabel = bodyLabel(causeSummary(root));
      causeLabel.getStyleClass().add("editor-error-cause");
      Label category = sectionLabel(type.label());
      category.setMinWidth(Region.USE_PREF_SIZE);
      HBox reason = new HBox(12, category, causeLabel);
      reason.setAlignment(Pos.BASELINE_LEFT);
      reason.getStyleClass().add("editor-error-diagnostics");
      HBox.setHgrow(causeLabel, Priority.ALWAYS);
      content.getChildren().add(reason);
    }

    if (!hints.isEmpty()) {
      VBox hintBox = new VBox(7, sectionLabel("What you can do"));
      for (String hint : hints) {
        Label bullet = new Label("•");
        bullet.getStyleClass().add("editor-error-bullet");
        HBox row = new HBox(9, bullet, bodyLabel(hint));
        row.setAlignment(Pos.TOP_LEFT);
        hintBox.getChildren().add(row);
      }
      content.getChildren().add(hintBox);
    }

    String report = buildErrorReport(title, type, summary, root, cause, hints);
    TitledPane details = technicalDetailsPane(report);
    content.getChildren().add(details);
    return new ErrorDialogModel(summary, content, report);
  }

  private static Label sectionLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("editor-dialog-section-title");
    return label;
  }

  private static Label bodyLabel(String text) {
    Label label = new Label(text == null ? "" : text);
    label.getStyleClass().add("editor-dialog-message");
    label.setWrapText(true);
    label.setMaxWidth(520);
    return label;
  }

  private static TitledPane technicalDetailsPane(String report) {
    TextArea details = new TextArea(report == null ? "" : report);
    details.setEditable(false);
    details.setWrapText(false);
    details.getStyleClass().add("editor-dialog-text-area");
    details.setPrefRowCount(7);

    TitledPane pane = new TitledPane("Technical details", details);
    pane.getStyleClass().add("editor-dialog-error-details");
    pane.setGraphic(DialogIcon.of(DialogIcon.Kind.REPORT, 18));
    pane.setExpanded(false);
    pane.setAnimated(false);
    return pane;
  }

  private static List<String> recoveryHints(ErrorType type, String summary, Throwable root, String... providedHints) {
    LinkedHashSet<String> hints = new LinkedHashSet<>();
    if (providedHints != null) {
      for (String hint : providedHints) {
        if (hint != null && !hint.isBlank()) hints.add(hint.trim());
      }
    }
    if (!hints.isEmpty()) return new ArrayList<>(hints);

    String haystack = (summary + " " + (root == null ? "" : root.getMessage()))
        .toLowerCase(Locale.ROOT);
    switch (type == null ? ErrorType.UNKNOWN : type) {
      case PROJECT_CONTEXT -> hints.add("Reopen the project through the launcher so JVN has a valid project root.");
      case FILE_IO -> hints.add("Confirm the file or folder exists and your account has the required read/write permission.");
      case DSL_PARSE -> hints.add("Open the diagnostics panel for this DSL and fix the first parse error before retrying.");
      case EDITOR_RUNTIME -> hints.add("Save your work and reopen the editor from Engine Hub. Use its build tools to refresh the editor launch files if the error returns.");
      case JAVA_CODE -> hints.add("Check the Java line number and compiler/runtime details, then rerun after the code compiles.");
      case PROCESS -> hints.add("Check the run console or terminal output for the process exit reason.");
      case ASSET -> hints.add("Verify referenced assets exist under the project folder and paths are relative to the project root.");
      case AUDIO -> hints.add("Verify the audio file exists, uses a supported format, and is referenced by the correct channel.");
      case MEMORY -> {
        hints.add("Close unused previews or release their caches, then retry the action.");
        hints.add("Use Engine Hub > Tools > JVM Memory Settings for the next launch and save a heap dump if pressure returns.");
      }
      case VALIDATION -> hints.add("Correct the highlighted field or invalid value, then try the action again.");
      case CLIPBOARD -> hints.add("Focus the editor window and try copying again.");
      case UNKNOWN -> hints.add("Check the selected project, file, or folder and try the action again.");
    }
    if (haystack.contains("workspace root")) {
      hints.add("Launch JVN from the repository root or reopen the project through the launcher.");
    }
    if (haystack.contains("jvn.project") || haystack.contains("manifest")) {
      hints.add("Open a project folder that contains a readable jvn.project file.");
    }
    if (haystack.contains("open") || haystack.contains("load") || haystack.contains("read")
        || haystack.contains("not found")) {
      hints.add("Confirm the file or folder still exists and your account can read it.");
    }
    if (haystack.contains("save") || haystack.contains("write") || haystack.contains("create")) {
      hints.add("Confirm the destination folder exists and your account can write to it.");
    }
    if (haystack.contains("process") || haystack.contains("gradle") || haystack.contains("run")
        || haystack.contains("launch")) {
      hints.add("Check the run console or terminal output for process-specific errors.");
    }
    if (haystack.contains("parse") || haystack.contains("syntax") || haystack.contains("dsl")) {
      hints.add("Use the line-numbered diagnostics overlay to find the action, command, or property that failed.");
    }
    if (haystack.contains("java") || haystack.contains("compiler") || haystack.contains("compilation")) {
      hints.add("Confirm JDK 21 is active and the inline Java block or Java source compiles.");
    }
    if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("audio")) {
      hints.add("Relink missing assets from inside the project folder rather than using temporary external paths.");
    }
    if (haystack.contains("timeline") || haystack.contains("puppeteer")) {
      hints.add("Run Puppeteer verification and fix errors before exporting or registering the timeline.");
    }
    if (haystack.contains("memory") || haystack.contains("heap") || haystack.contains("outofmemory")
        || haystack.contains("garbage collection") || haystack.contains(" gc ")) {
      hints.add("Close unused previews or release their caches before retrying.");
      hints.add("Configure the next launch under Engine Hub > Tools > JVM Memory Settings.");
    }
    if (hints.isEmpty()) {
      hints.add("Check the selected project, file, or folder and try the action again.");
    }
    hints.add("Copy the technical details if you need to report the issue.");
    return new ArrayList<>(hints);
  }

  private static String buildErrorReport(String title,
                                         ErrorType type,
                                         String summary,
                                         Throwable root,
                                         Throwable cause,
                                         List<String> hints) {
    StringBuilder report = new StringBuilder();
    report.append("Title: ").append(normalizeDialogText(title, "Error")).append('\n');
    report.append("Type: ").append(type == null ? ErrorType.UNKNOWN.label() : type.label()).append('\n');
    report.append("Summary: ").append(summary).append('\n');
    if (root != null) {
      report.append("Root cause: ").append(causeSummary(root)).append('\n');
    }
    if (hints != null && !hints.isEmpty()) {
      report.append('\n').append("Suggested next steps:").append('\n');
      for (String hint : hints) {
        report.append("- ").append(hint).append('\n');
      }
    }
    if (cause != null) {
      report.append('\n').append("Stack trace:").append('\n');
      StringWriter stack = new StringWriter();
      cause.printStackTrace(new PrintWriter(stack));
      report.append(stack.toString().stripTrailing());
    }
    return report.toString().stripTrailing();
  }

  private static ErrorType classifyError(String summary, Throwable root, String title) {
    if (root instanceof LinkageError || root instanceof ClassNotFoundException) {
      return ErrorType.EDITOR_RUNTIME;
    }
    String haystack = ((title == null ? "" : title) + " "
        + (summary == null ? "" : summary) + " "
        + (root == null || root.getMessage() == null ? "" : root.getMessage()) + " "
        + (root == null ? "" : root.getClass().getSimpleName()))
        .toLowerCase(Locale.ROOT);
    if (haystack.contains("outofmemory") || haystack.contains("out of memory")
        || haystack.contains("heap space") || haystack.contains("heap pressure")
        || haystack.contains("garbage collection") || haystack.contains("gc overhead")
        || haystack.contains("memory limit")) {
      return ErrorType.MEMORY;
    }
    if (haystack.contains("workspace root") || haystack.contains("project root")
        || haystack.contains("jvn.project") || haystack.contains("manifest")) {
      return ErrorType.PROJECT_CONTEXT;
    }
    if (haystack.contains("parse") || haystack.contains("syntax") || haystack.contains("dsl")
        || haystack.contains("vns") || haystack.contains("jes")) {
      return ErrorType.DSL_PARSE;
    }
    if (haystack.contains("java") || haystack.contains("compiler") || haystack.contains("compilation")
        || haystack.contains("jdk")) {
      return ErrorType.JAVA_CODE;
    }
    if (haystack.contains("gradle") || haystack.contains("process") || haystack.contains("launch")
        || haystack.contains("run")) {
      return ErrorType.PROCESS;
    }
    if (haystack.contains("asset") || haystack.contains("image") || haystack.contains("sprite")
        || haystack.contains("missing")) {
      return ErrorType.ASSET;
    }
    if (haystack.contains("audio") || haystack.contains("media")) {
      return ErrorType.AUDIO;
    }
    if (haystack.contains("save") || haystack.contains("write") || haystack.contains("read")
        || haystack.contains("open") || haystack.contains("file") || haystack.contains("folder")
        || haystack.contains("not found") || haystack.contains("denied")) {
      return ErrorType.FILE_IO;
    }
    if (haystack.contains("invalid") || haystack.contains("validation") || haystack.contains("required")) {
      return ErrorType.VALIDATION;
    }
    if (haystack.contains("clipboard") || haystack.contains("copy")) {
      return ErrorType.CLIPBOARD;
    }
    return ErrorType.UNKNOWN;
  }

  private static String causeSummary(Throwable throwable) {
    if (throwable == null) return "Unknown error";
    String message = normalizeDialogText(throwable.getMessage(), throwable.getClass().getSimpleName());
    return throwable.getClass().getSimpleName() + ": " + message;
  }

  private static Throwable rootCause(Throwable throwable) {
    Throwable current = throwable;
    for (int depth = 0; current != null && current.getCause() != null && depth < 32; depth++) {
      Throwable next = current.getCause();
      if (next == current) break;
      current = next;
    }
    return current;
  }

  private static String normalizeDialogText(String text, String fallback) {
    String normalized = text == null ? "" : text.trim();
    if (normalized.isBlank()) return fallback == null ? "" : fallback;
    return normalized;
  }

  private static void copyToClipboard(String text) {
    ClipboardContent content = new ClipboardContent();
    content.putString(text == null ? "" : text);
    Clipboard.getSystemClipboard().setContent(content);
  }

  private static Stage createStage(Window owner,
                                   String title,
                                   String message,
                                   Node content,
                                   Node focusTarget,
                                   AtomicReference<String> result,
                                   ActionSpec... actions) {
    return createStage(owner, title, message, content, focusTarget, result, null, actions);
  }

  private static Stage createStage(Window owner,
                                   String title,
                                   String message,
                                   Node content,
                                   Node focusTarget,
                                   AtomicReference<String> result,
                                   DialogIcon.Kind notice,
                                   ActionSpec... actions) {
    StackPane overlay = new StackPane();
    overlay.getStyleClass().add("editor-dialog-overlay");

    VBox card = new VBox(12);
    card.getStyleClass().add("editor-dialog-card");
    card.setMaxWidth(560);
    card.setFillWidth(true);
    card.setPadding(new Insets(16));

    Label titleLabel = new Label(title == null || title.isBlank() ? "Dialog" : title.trim());
    titleLabel.getStyleClass().add("editor-dialog-title");
    titleLabel.setWrapText(true);

    Label messageLabel = new Label(message == null ? "" : message.trim());
    messageLabel.getStyleClass().add("editor-dialog-message");
    messageLabel.setWrapText(true);
    boolean showMessage = !messageLabel.getText().isBlank();
    messageLabel.setManaged(showMessage);
    messageLabel.setVisible(showMessage);

    VBox bodyBox = new VBox();
    bodyBox.getStyleClass().add("editor-dialog-body");
    if (content != null) {
      if (content instanceof Region region) {
        region.setMaxWidth(Double.MAX_VALUE);
      }
      bodyBox.getChildren().add(content);
    }
    bodyBox.setManaged(content != null);
    bodyBox.setVisible(content != null);
    VBox.setVgrow(bodyBox, Priority.ALWAYS);

    HBox actionsBox = new HBox(8);
    actionsBox.setAlignment(Pos.CENTER_RIGHT);

    List<ActionSpec> effectiveActions = new ArrayList<>();
    if (actions == null || actions.length == 0) {
      effectiveActions.add(ActionSpec.accent("close", "Close", null));
    } else {
      for (ActionSpec action : actions) {
        if (action != null) effectiveActions.add(action);
      }
    }

    Stage stage = new Stage(StageStyle.TRANSPARENT);
    if (owner != null) {
      stage.initOwner(owner);
      stage.initModality(Modality.WINDOW_MODAL);
    } else {
      stage.initModality(Modality.APPLICATION_MODAL);
    }

    AtomicReference<Runnable> cancelAction = new AtomicReference<>(stage::close);
    for (ActionSpec spec : effectiveActions) {
      javafx.scene.control.Button button = new javafx.scene.control.Button(spec.label);
      button.getStyleClass().add("editor-dialog-button");
      if (notice != null) {
        switch (spec.id) {
          case "copy" -> button.setGraphic(DialogIcon.of(DialogIcon.Kind.COPY, 17));
          case "close" -> button.setGraphic(DialogIcon.of(DialogIcon.Kind.CLOSE, 17));
          default -> {}
        }
        button.setDefaultButton(spec.defaultFocus);
      }
      switch (spec.style) {
        case ACCENT -> button.getStyleClass().add("editor-dialog-button-accent");
        case DANGER -> button.getStyleClass().add("editor-dialog-button-danger");
        case NEUTRAL -> button.getStyleClass().add("editor-dialog-button-neutral");
      }
      button.setOnAction(event -> {
        result.set(spec.id);
        spec.action.run();
        if (spec.closeOnAction) stage.close();
        event.consume();
      });
      actionsBox.getChildren().add(button);
      if ("cancel".equalsIgnoreCase(spec.id)) {
        cancelAction.set(() -> {
          result.set(spec.id);
          stage.close();
        });
      }
      if (spec.defaultFocus) {
        Platform.runLater(button::requestFocus);
      }
    }

    Runnable sizeNotice = () -> {};
    if (notice == null) {
      card.getChildren().add(titleLabel);
      if (showMessage) card.getChildren().add(messageLabel);
      if (content != null) card.getChildren().add(bodyBox);
    } else {
      card.getStyleClass().add("editor-notice");
      overlay.getStyleClass().add("editor-notice-overlay");
      Rectangle2D display = displayFor(owner);
      card.setPrefWidth(Math.min(560, display.getWidth() - 60));

      VBox headerText = new VBox(5, titleLabel);
      headerText.setAlignment(Pos.CENTER_LEFT);
      if (showMessage) headerText.getChildren().add(messageLabel);
      HBox header = new HBox(13, DialogIcon.of(notice, 34), headerText);
      header.getStyleClass().add("editor-notice-header");
      header.setAlignment(Pos.CENTER_LEFT);
      HBox.setHgrow(headerText, Priority.ALWAYS);
      card.getChildren().add(header);

      if (content != null) {
        ScrollPane scroll = new ScrollPane(bodyBox);
        scroll.getStyleClass().add("editor-notice-scroll");
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        double heightLimit = Math.max(140, display.getHeight() - 230);
        card.getChildren().add(scroll);
        sizeNotice = () -> {
          scroll.setPrefHeight(Math.min(heightLimit, Math.ceil(bodyBox.prefHeight(card.getPrefWidth() - 2)) + 2));
          stage.sizeToScene();
          if (stage.isShowing() && stage.getY() + stage.getHeight() > display.getMaxY()) {
            stage.setY(Math.max(display.getMinY(), display.getMaxY() - stage.getHeight()));
          }
        };
        Runnable resize = sizeNotice;
        TitledPane details = (TitledPane) content.lookup(".editor-dialog-error-details");
        if (details != null) {
          details.expandedProperty().addListener((obs, before, expanded) -> Platform.runLater(resize));
        }
      }
      actionsBox.getStyleClass().add("editor-notice-actions");
    }
    card.getChildren().add(actionsBox);

    overlay.getChildren().add(card);
    overlay.setAlignment(Pos.CENTER);
    overlay.setOnMouseClicked(event -> {
      if (event.getTarget() == overlay) {
        cancelAction.get().run();
        event.consume();
      }
    });

    Scene scene = new Scene(overlay);
    scene.setFill(Color.TRANSPARENT);
    EditorTheme.apply(scene);
    scene.setOnKeyPressed(event -> {
      if (event.getCode() == KeyCode.ESCAPE) {
        cancelAction.get().run();
        event.consume();
      }
    });

    stage.setTitle(titleLabel.getText());
    stage.setScene(scene);
    stage.setResizable(false);
    if (notice != null) overlay.applyCss();
    sizeNotice.run();
    stage.sizeToScene();
    Platform.runLater(() -> {
      if (focusTarget != null) {
        focusTarget.requestFocus();
      }
    });
    return stage;
  }

  private static Rectangle2D displayFor(Window owner) {
    if (owner != null) {
      var screens = Screen.getScreensForRectangle(owner.getX(), owner.getY(), owner.getWidth(), owner.getHeight());
      if (!screens.isEmpty()) return screens.get(0).getVisualBounds();
    }
    return Screen.getPrimary().getVisualBounds();
  }

  private record ErrorDialogModel(String summary, VBox content, String report) {
  }
}

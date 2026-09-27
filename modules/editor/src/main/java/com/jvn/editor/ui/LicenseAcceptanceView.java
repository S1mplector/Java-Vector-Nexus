package com.jvn.editor.ui;

import com.jvn.licensing.AcceptanceStore;
import com.jvn.licensing.LicenseTerms;
import java.nio.file.Files;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;

/** Shared Editor and Launcher page; no launch/export gating. */
public final class LicenseAcceptanceView {
  private LicenseAcceptanceView() {}

  public static void show(Window owner, String appVersion) {
    try {
      LicenseTerms terms = LicenseTerms.bundled();
      AcceptanceStore store = AcceptanceStore.local();
      create(owner, appVersion, store, terms).show();
    } catch (Exception e) { error(owner, e); }
  }

  public static boolean requireAcceptance(Window owner) {
    boolean implicitExit = javafx.application.Platform.isImplicitExit();
    javafx.application.Platform.setImplicitExit(false);
    try {
      var terms = LicenseTerms.bundled(); var store = AcceptanceStore.local();
      if (store.status(terms).state() == AcceptanceStore.State.CURRENT) return true;
      create(owner, LicenseTerms.applicationVersion(), store, terms, true).showAndWait();
      return store.status(terms).state() == AcceptanceStore.State.CURRENT;
    } catch (Exception e) { error(owner, e); return false; }
    finally { javafx.application.Platform.setImplicitExit(implicitExit); }
  }

  static Stage create(Window owner, String appVersion, AcceptanceStore store, LicenseTerms terms) {
    return create(owner, appVersion, store, terms, false);
  }

  static Stage create(Window owner, String appVersion, AcceptanceStore store, LicenseTerms terms, boolean required) {
      Stage stage = new Stage();
      if (owner != null) stage.initOwner(owner);
      stage.initModality(Modality.WINDOW_MODAL);
      stage.setTitle("JVN License & Terms");
      Label status = new Label();
      Runnable refresh = () -> status.setText(store.status(terms).detail());
      refresh.run();
      Label heading = new Label("Review the JVN License and Terms of Use");
      heading.setGraphic(AeroIcon.of(AeroIcon.Kind.DOCUMENTATION, 34));
      heading.setGraphicTextGap(12);
      heading.getStyleClass().add("legal-heading");
      Label optional = new Label(required
          ? "Accept the current license and terms to use JVN. Declining closes this application."
          : "Current acceptance is required for developer tools and game exports. Closing this page does not accept.");
      optional.setWrapText(true);
      optional.getStyleClass().add("legal-muted");
      status.getStyleClass().add("legal-status");
      TabPane documents = new TabPane();
      documents.getTabs().addAll(tab("License " + terms.licenseVersion(), terms.license()),
          tab("Terms " + terms.termsVersion(), terms.terms()),
          tab("Receipt details", "Storage: " + store.root() + "\n\nBundle SHA-256:\n" + terms.bundleHash()
              + "\n\nReceipts contain your supplied name, optional organization, local time, document copies and a local signature."
              + "\nNothing is uploaded. Local signatures do not verify identity or provide a trusted timestamp."
              + "\nYou can export receipts or delete local records at the storage location."
              + "\nExports include your supplied name. The private signing key is never exported."));
      TextField name = new TextField();
      name.setId("acceptanceName"); name.setPromptText("Your name (required, self-declared)");
      TextField organization = new TextField(); organization.setPromptText("Organization (optional)");
      GridPane fields = new GridPane(); fields.setHgap(12); fields.setVgap(8);
      fields.addRow(0, new Label("Your name"), name);
      fields.addRow(1, new Label("Organization"), organization);
      GridPane.setHgrow(name, Priority.ALWAYS); GridPane.setHgrow(organization, Priority.ALWAYS);
      Label statement = new Label(LicenseTerms.CONSENT); statement.setWrapText(true);
      CheckBox consent = new CheckBox("I agree to the License and Terms of Use");
      consent.setId("acceptanceConsent");
      Button accept = new Button("Accept and save receipt");
      accept.setId("acceptanceSave");
      accept.setGraphic(AeroIcon.of(AeroIcon.Kind.MANIFEST, 22));
      accept.getStyleClass().add("legal-primary");
      accept.disableProperty().bind(consent.selectedProperty().not().or(
          javafx.beans.binding.Bindings.createBooleanBinding(() -> name.getText().isBlank(), name.textProperty())));
      Button export = new Button("Export receipt…");
      export.setGraphic(AeroIcon.of(AeroIcon.Kind.REVEAL, 22));
      export.setDisable(store.status(terms).receipt() == null);
      accept.setOnAction(event -> {
        try {
          store.accept(terms, terms.bundleHash(), name.getText(), organization.getText(), appVersion,
              "editor-checkbox-v1", consent.isSelected());
          consent.setSelected(false); refresh.run(); export.setDisable(false);
          if (required) stage.close();
          else EditorDialogs.info(stage, "Acceptance saved", "Your receipt was saved locally. You can export a copy.");
        } catch (Exception e) { error(stage, e); }
      });
      export.setOnAction(event -> {
        var current = store.status(terms);
        if (current.receipt() == null) { refresh.run(); export.setDisable(true); return; }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export receipt — includes your supplied name");
        chooser.setInitialFileName("jvn-acceptance-receipt.zip");
        var destination = chooser.showSaveDialog(stage);
        if (destination == null) return;
        try {
          AcceptanceStore.exportReceipt(current.receipt(), destination.toPath());
          EditorDialogs.info(stage, "Receipt exported", "Keep your exported receipt secure.");
        } catch (Exception e) { error(stage, e); }
      });
      Button save = new Button("Save documents…");
      save.setGraphic(AeroIcon.of(AeroIcon.Kind.DOCUMENTATION, 22));
      save.setOnAction(event -> {
        DirectoryChooser chooser = new DirectoryChooser(); chooser.setTitle("Save a copy of the terms");
        var destination = chooser.showDialog(stage);
        if (destination == null) return;
        try {
          var folder = Files.createTempDirectory(destination.toPath(), "jvn-terms-");
          Files.writeString(folder.resolve("LICENSE.md"), terms.license());
          Files.writeString(folder.resolve("TERMS.md"), terms.terms());
          EditorDialogs.info(stage, "Documents saved", folder.toString());
        } catch (Exception e) { error(stage, e); }
      });
      Button close = new Button(required ? "Decline and exit" : "Close"); close.setCancelButton(true);
      close.setGraphic(AeroIcon.of(AeroIcon.Kind.ARROW_BACK, 20));
      close.setOnAction(event -> stage.close());
      FlowPane buttons = new FlowPane(8, 8, save, export, close, accept);
      VBox body = new VBox(12, heading, status, optional, documents, fields, statement, consent, buttons);
      body.getStyleClass().add("legal-page");
      body.setPadding(new Insets(20)); VBox.setVgrow(documents, Priority.ALWAYS);
      stage.setScene(new Scene(body, 960, 780));
      EditorTheme.apply(stage);
      var css = LicenseAcceptanceView.class.getResource("/com/jvn/editor/legal.css");
      if (css != null) stage.getScene().getStylesheets().add(css.toExternalForm());
      stage.setMinWidth(720); stage.setMinHeight(640);
      return stage;
  }

  private static Tab tab(String title, String content) {
    TextArea text = new TextArea(content); text.setEditable(false); text.setWrapText(true);
    text.positionCaret(0);
    Tab tab = new Tab(title, text);
    tab.setGraphic(AeroIcon.of(title.startsWith("Receipt") ? AeroIcon.Kind.MANIFEST : AeroIcon.Kind.DOCUMENTATION, 18));
    tab.setClosable(false); return tab;
  }
  private static void error(Window owner, Exception e) {
    EditorDialogs.info(owner, "License operation failed", "Could not complete the license operation. " + e.getMessage());
  }
}

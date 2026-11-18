package com.jvn.editor.ui;

import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.editor.commands.CommandStack;
import com.jvn.editor.commands.FunctionalCommand;
import com.jvn.editor.commands.SetDoublePropertyCommand;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import java.util.Objects;
import java.util.OptionalDouble;
import java.util.function.Consumer;
import java.util.function.DoubleConsumer;
import java.util.function.DoubleSupplier;
import java.util.function.Supplier;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;

/** Property editor for the entity selected in the JES scene viewport. */
public class InspectorView extends VBox {
  private static final double PROPERTY_LABEL_WIDTH = 108;

  private final Consumer<String> setStatus;
  private final Label selectionLabel = new Label("No entity selected");
  private final Label feedbackLabel = new Label("Select an entity in the JES viewport to edit it.");
  private final VBox content = new VBox(8);

  @SuppressWarnings("unused")
  private JesScene2D scene;
  private Entity2D selected;
  private CommandStack commands;

  public InspectorView(Consumer<String> setStatus) {
    this.setStatus = setStatus == null ? ignored -> {} : setStatus;
    getStyleClass().add("sidebar-tool-root");
    setSpacing(0);
    setFillWidth(true);

    Label title = new Label("Inspector");
    title.getStyleClass().add("sidebar-tool-title");
    HBox titleRow = new HBox(6, title, SidebarToolHelp.button(this, "Inspector", """
        The Inspector edits the entity selected in the JES scene viewport.

        Fields are grouped by purpose and change with the selected entity type. Press Enter or
        leave a text/number field to apply it. Valid changes participate in editor undo/redo;
        unchanged values do not create undo entries.

        Invalid numbers are rejected and restored to the current scene value. Alpha values are
        normally between 0 and 1, origins are normalized pivot coordinates, and particle ranges
        should keep minimum values at or below their corresponding maximums."""));
    titleRow.setAlignment(Pos.CENTER_LEFT);

    selectionLabel.getStyleClass().add("sidebar-tool-summary");
    feedbackLabel.getStyleClass().add("sidebar-tool-status");
    selectionLabel.setWrapText(true);
    feedbackLabel.setWrapText(true);

    VBox header = new VBox(6, titleRow, selectionLabel);
    header.getStyleClass().add("sidebar-tool-header");
    content.setPadding(new Insets(10));
    VBox footer = new VBox(feedbackLabel);
    footer.setPadding(new Insets(8, 10, 10, 10));
    footer.getStyleClass().add("sidebar-tool-footer");
    getChildren().addAll(header, content, footer);
    rebuild();
  }

  public void setScene(JesScene2D scene) {
    this.scene = scene;
  }

  public void setSelection(Entity2D entity) {
    selected = entity;
    rebuild();
  }

  public void setCommandStack(CommandStack stack) {
    commands = stack;
  }

  private void rebuild() {
    content.getChildren().clear();
    if (selected == null) {
      selectionLabel.setText("No entity selected");
      feedbackLabel.setText("Select an entity in the JES viewport to edit it.");
      Label empty = new Label("Nothing to inspect");
      empty.getStyleClass().add("sidebar-tool-subtitle");
      content.getChildren().add(empty);
      return;
    }

    selectionLabel.setText(friendlyTypeName(selected));
    feedbackLabel.setText("Press Enter or leave a field to apply a change.");
    addSection("Transform");
    content.getChildren().addAll(
        makeNumberFieldCmd("X", selected::getX, value -> selected.setPosition(value, selected.getY())),
        makeNumberFieldCmd("Y", selected::getY, value -> selected.setPosition(selected.getX(), value)),
        makeNumberFieldCmd("Rotation", selected::getRotationDeg, selected::setRotationDeg),
        makeNumberFieldCmd("Scale X", selected::getScaleX,
            value -> selected.setScale(value, selected.getScaleY())),
        makeNumberFieldCmd("Scale Y", selected::getScaleY,
            value -> selected.setScale(selected.getScaleX(), value)),
        makeNumberFieldCmd("Origin X", selected::getOriginX,
            value -> selected.setOrigin(value, selected.getOriginY())),
        makeNumberFieldCmd("Origin Y", selected::getOriginY,
            value -> selected.setOrigin(selected.getOriginX(), value)));

    addSection("Rendering");
    content.getChildren().addAll(
        makeNumberFieldCmd("Depth (Z)", selected::getZ, selected::setZ),
        makeBooleanField("Visible", selected.isVisible(), selected::isVisible, selected::setVisible),
        makeNumberFieldCmd("Parallax X", selected::getParallaxX,
            value -> selected.setParallax(value, selected.getParallaxY())),
        makeNumberFieldCmd("Parallax Y", selected::getParallaxY,
            value -> selected.setParallax(selected.getParallaxX(), value)),
        makeNumberFieldCmd("Blur", selected::getBlurRadius, selected::setBlurRadius),
        makeNumberFieldCmd("Brightness", selected::getBrightness, selected::setBrightness));

    if (selected instanceof Panel2D panel) {
      addSection("Panel");
      content.getChildren().addAll(
          makeNumberFieldCmd("Width", panel::getWidth, value -> panel.setSize(value, panel.getHeight())),
          makeNumberFieldCmd("Height", panel::getHeight, value -> panel.setSize(panel.getWidth(), value)));
    } else if (selected instanceof PhysicsBodyEntity2D physicsEntity) {
      addPhysicsFields(physicsEntity);
    } else if (selected instanceof Label2D label) {
      addLabelFields(label);
    } else if (selected instanceof Sprite2D sprite) {
      addSpriteFields(sprite);
    } else if (selected instanceof ParticleEmitter2D emitter) {
      addParticleFields(emitter);
    }
  }

  private void addPhysicsFields(PhysicsBodyEntity2D physicsEntity) {
    RigidBody2D body = physicsEntity.getBody();
    if (body == null) {
      setFeedback("This physics entity does not currently have a rigid body.");
      return;
    }
    addSection("Physics Body");
    content.getChildren().addAll(
        makeNumberFieldCmd("Mass", body::getMass, body::setMass),
        makeNumberFieldCmd("Restitution", body::getRestitution, body::setRestitution),
        makeBooleanField("Static", body.isStatic(), body::isStatic, body::setStatic),
        makeBooleanField("Sensor", body.isSensor(), body::isSensor, body::setSensor),
        makeNumberFieldCmd("Velocity X", body::getVx, value -> body.setVelocity(value, body.getVy())),
        makeNumberFieldCmd("Velocity Y", body::getVy, value -> body.setVelocity(body.getVx(), value)));
  }

  private void addLabelFields(Label2D label) {
    addSection("Text");
    content.getChildren().addAll(
        makeTextFieldCmd("Text", () -> nullToEmpty(label.getText()), label::setText),
        makeNumberFieldCmd("Font Size", label::getSize,
            value -> label.setFont(label.getFontFamily(), value, label.isBold())),
        makeBooleanField("Bold", label.isBold(), label::isBold,
            value -> label.setFont(label.getFontFamily(), label.getSize(), value)));

    ComboBox<Label2D.Align> alignment = new ComboBox<>();
    alignment.getItems().addAll(Label2D.Align.values());
    alignment.getSelectionModel().select(label.getAlign());
    alignment.setMaxWidth(Double.MAX_VALUE);
    alignment.setOnAction(event -> {
      Label2D.Align oldValue = label.getAlign();
      Label2D.Align newValue = alignment.getValue();
      if (newValue == null || newValue == oldValue) return;
      execute("Change text alignment", () -> label.setAlign(newValue), () -> label.setAlign(oldValue));
      setFeedback("Updated alignment = " + newValue);
    });
    content.getChildren().add(propertyRow("Alignment", alignment));

    ColorPicker color = new ColorPicker(new Color(
        label.getColorR(), label.getColorG(), label.getColorB(), label.getAlpha()));
    color.setMaxWidth(Double.MAX_VALUE);
    color.setOnAction(event -> {
      double oldR = label.getColorR();
      double oldG = label.getColorG();
      double oldB = label.getColorB();
      double oldA = label.getAlpha();
      Color next = color.getValue();
      if (sameColor(oldR, oldG, oldB, oldA, next)) return;
      execute("Change text color",
          () -> label.setColor(next.getRed(), next.getGreen(), next.getBlue(), next.getOpacity()),
          () -> label.setColor(oldR, oldG, oldB, oldA));
      setFeedback("Updated text color.");
    });
    content.getChildren().addAll(
        propertyRow("Color", color),
        makeNumberFieldCmd("Alpha", label::getAlpha,
            value -> label.setColor(label.getColorR(), label.getColorG(), label.getColorB(), value)));
  }

  private void addSpriteFields(Sprite2D sprite) {
    addSection("Sprite");
    content.getChildren().addAll(
        makeTextFieldCmd("Image", () -> nullToEmpty(sprite.getImagePath()), sprite::setImagePath),
        makeNumberFieldCmd("Width", sprite::getWidth, value -> sprite.setSize(value, sprite.getHeight())),
        makeNumberFieldCmd("Height", sprite::getHeight, value -> sprite.setSize(sprite.getWidth(), value)),
        makeNumberFieldCmd("Alpha", sprite::getAlpha, sprite::setAlpha));
  }

  private void addParticleFields(ParticleEmitter2D emitter) {
    addSection("Emission");
    content.getChildren().addAll(
        makeNumberFieldCmd("Emission Rate", emitter::getEmissionRate, emitter::setEmissionRate),
        makeNumberFieldCmd("Minimum Life", emitter::getMinLife,
            value -> emitter.setLifeRange(value, emitter.getMaxLife())),
        makeNumberFieldCmd("Maximum Life", emitter::getMaxLife,
            value -> emitter.setLifeRange(emitter.getMinLife(), value)),
        makeNumberFieldCmd("Minimum Size", emitter::getMinSize,
            value -> emitter.setSizeRange(value, emitter.getMaxSize(), emitter.getEndSizeScale())),
        makeNumberFieldCmd("Maximum Size", emitter::getMaxSize,
            value -> emitter.setSizeRange(emitter.getMinSize(), value, emitter.getEndSizeScale())),
        makeNumberFieldCmd("End Size Scale", emitter::getEndSizeScale,
            value -> emitter.setSizeRange(emitter.getMinSize(), emitter.getMaxSize(), value)),
        makeNumberFieldCmd("Minimum Speed", emitter::getMinSpeed,
            value -> emitter.setSpeedRange(value, emitter.getMaxSpeed())),
        makeNumberFieldCmd("Maximum Speed", emitter::getMaxSpeed,
            value -> emitter.setSpeedRange(emitter.getMinSpeed(), value)),
        makeNumberFieldCmd("Minimum Angle", emitter::getMinAngle,
            value -> emitter.setAngleRange(value, emitter.getMaxAngle())),
        makeNumberFieldCmd("Maximum Angle", emitter::getMaxAngle,
            value -> emitter.setAngleRange(emitter.getMinAngle(), value)),
        makeNumberFieldCmd("Gravity Y", emitter::getGravityY, emitter::setGravity));

    addSection("Appearance");
    content.getChildren().addAll(
        makeParticleColorField("Start Color", emitter, true),
        makeParticleColorField("End Color", emitter, false),
        makeTextFieldCmd("Texture", () -> nullToEmpty(emitter.getTexture()), emitter::setTexture),
        makeBooleanField("Additive", emitter.isAdditive(), emitter::isAdditive, emitter::setAdditive));
  }

  private HBox makeParticleColorField(String name, ParticleEmitter2D emitter, boolean start) {
    double oldR = start ? emitter.getStartR() : emitter.getEndR();
    double oldG = start ? emitter.getStartG() : emitter.getEndG();
    double oldB = start ? emitter.getStartB() : emitter.getEndB();
    double oldA = start ? emitter.getStartA() : emitter.getEndA();
    ColorPicker picker = new ColorPicker(new Color(oldR, oldG, oldB, oldA));
    picker.setMaxWidth(Double.MAX_VALUE);
    picker.setOnAction(event -> {
      Color next = picker.getValue();
      double currentR = start ? emitter.getStartR() : emitter.getEndR();
      double currentG = start ? emitter.getStartG() : emitter.getEndG();
      double currentB = start ? emitter.getStartB() : emitter.getEndB();
      double currentA = start ? emitter.getStartA() : emitter.getEndA();
      if (sameColor(currentR, currentG, currentB, currentA, next)) return;
      Runnable apply = start
          ? () -> emitter.setStartColor(next.getRed(), next.getGreen(), next.getBlue(), next.getOpacity())
          : () -> emitter.setEndColor(next.getRed(), next.getGreen(), next.getBlue(), next.getOpacity());
      Runnable undo = start
          ? () -> emitter.setStartColor(currentR, currentG, currentB, currentA)
          : () -> emitter.setEndColor(currentR, currentG, currentB, currentA);
      execute("Change " + name.toLowerCase(), apply, undo);
      setFeedback("Updated " + name.toLowerCase() + ".");
    });
    return propertyRow(name, picker);
  }

  private HBox makeNumberFieldCmd(String label, DoubleSupplier getter, DoubleConsumer setter) {
    TextField field = new TextField(formatNumber(getter.getAsDouble()));
    field.setAccessibleText(label);
    field.setTooltip(new Tooltip("Press Enter or leave the field to apply " + label.toLowerCase()));
    Runnable commit = () -> commitNumber(label, field, getter, setter);
    field.setOnAction(event -> commit.run());
    field.focusedProperty().addListener((observable, wasFocused, focused) -> {
      if (wasFocused && !focused) commit.run();
    });
    return propertyRow(label, field);
  }

  private void commitNumber(String label, TextField field, DoubleSupplier getter, DoubleConsumer setter) {
    OptionalDouble parsed = parseFiniteDouble(field.getText());
    if (parsed.isEmpty()) {
      field.setText(formatNumber(getter.getAsDouble()));
      setFeedback("Invalid " + label.toLowerCase() + ": enter a finite number.");
      return;
    }
    double oldValue = getter.getAsDouble();
    double newValue = parsed.getAsDouble();
    if (Double.compare(oldValue, newValue) == 0) {
      field.setText(formatNumber(oldValue));
      return;
    }
    if (commands != null) {
      commands.pushAndExecute(new SetDoublePropertyCommand(
          getter, setter, newValue, "Adjust " + label));
    } else {
      setter.accept(newValue);
    }
    field.setText(formatNumber(newValue));
    setFeedback("Updated " + label.toLowerCase() + " = " + formatNumber(newValue));
  }

  private HBox makeTextFieldCmd(String label, Supplier<String> getter, Consumer<String> setter) {
    TextField field = new TextField(nullToEmpty(getter.get()));
    field.setAccessibleText(label);
    field.setTooltip(new Tooltip("Press Enter or leave the field to apply " + label.toLowerCase()));
    Runnable commit = () -> {
      String oldValue = nullToEmpty(getter.get());
      String newValue = nullToEmpty(field.getText());
      if (Objects.equals(oldValue, newValue)) return;
      execute("Change " + label, () -> setter.accept(newValue), () -> setter.accept(oldValue));
      setFeedback("Updated " + label.toLowerCase() + ".");
    };
    field.setOnAction(event -> commit.run());
    field.focusedProperty().addListener((observable, wasFocused, focused) -> {
      if (wasFocused && !focused) commit.run();
    });
    return propertyRow(label, field);
  }

  private CheckBox makeBooleanField(
      String label, boolean initialValue, Supplier<Boolean> getter, Consumer<Boolean> setter) {
    CheckBox checkBox = new CheckBox(label);
    checkBox.setSelected(initialValue);
    checkBox.setOnAction(event -> {
      boolean oldValue = Boolean.TRUE.equals(getter.get());
      boolean newValue = checkBox.isSelected();
      if (oldValue == newValue) return;
      execute("Toggle " + label, () -> setter.accept(newValue), () -> setter.accept(oldValue));
      setFeedback("Updated " + label.toLowerCase() + " = " + newValue);
    });
    return checkBox;
  }

  private HBox propertyRow(String labelText, javafx.scene.Node control) {
    Label label = new Label(labelText);
    label.setMinWidth(PROPERTY_LABEL_WIDTH);
    label.setPrefWidth(PROPERTY_LABEL_WIDTH);
    label.setLabelFor(control);
    HBox row = new HBox(8, label, control);
    row.setAlignment(Pos.CENTER_LEFT);
    if (control instanceof Region region) {
      region.setMaxWidth(Double.MAX_VALUE);
      HBox.setHgrow(region, Priority.ALWAYS);
    }
    return row;
  }

  private void addSection(String title) {
    Label heading = new Label(title);
    heading.getStyleClass().add("sidebar-tool-section-title");
    if (!content.getChildren().isEmpty()) VBox.setMargin(heading, new Insets(8, 0, 0, 0));
    content.getChildren().add(heading);
  }

  private void execute(String description, Runnable apply, Runnable undo) {
    if (commands != null) commands.pushAndExecute(new FunctionalCommand(description, apply, undo));
    else apply.run();
  }

  private void setFeedback(String message) {
    String safe = message == null ? "" : message;
    feedbackLabel.setText(safe);
    setStatus.accept(safe);
  }

  static OptionalDouble parseFiniteDouble(String value) {
    if (value == null || value.isBlank()) return OptionalDouble.empty();
    try {
      double parsed = Double.parseDouble(value.trim());
      return Double.isFinite(parsed) ? OptionalDouble.of(parsed) : OptionalDouble.empty();
    } catch (NumberFormatException ex) {
      return OptionalDouble.empty();
    }
  }

  private static String formatNumber(double value) {
    return Double.toString(value);
  }

  private static boolean sameColor(double r, double g, double b, double a, Color color) {
    return color != null
        && Double.compare(r, color.getRed()) == 0
        && Double.compare(g, color.getGreen()) == 0
        && Double.compare(b, color.getBlue()) == 0
        && Double.compare(a, color.getOpacity()) == 0;
  }

  private static String friendlyTypeName(Entity2D entity) {
    String simple = entity.getClass().getSimpleName();
    if (simple.endsWith("2D")) simple = simple.substring(0, simple.length() - 2);
    return simple.replaceAll("([a-z])([A-Z])", "$1 $2");
  }

  private static String nullToEmpty(String value) {
    return value == null ? "" : value;
  }
}

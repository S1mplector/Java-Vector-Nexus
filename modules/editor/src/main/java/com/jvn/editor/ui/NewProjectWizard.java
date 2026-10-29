package com.jvn.editor.ui;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Properties;

import com.jvn.core.project.StoryMapPaths;
import com.jvn.core.vn.VnStoragePaths;
import com.jvn.editor.vcs.GitVcsService;

import javafx.css.Styleable;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContentDisplay;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextArea;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.*;

/**
 * Project creation wizard for VN projects.
 * Keeps setup compact and aligned with the current engine/editor workflow.
 */
public class NewProjectWizard extends Stage {
  private enum ProjectTemplate {
    STARTER_STORY("Starter Story", "A lean starter project with a playable story flow, menu pack, and demo assets."),
    TUTORIAL_WORKSPACE("Tutorial Workspace", "A full learning scaffold with the starter story, 30 tutorial scripts, and demo content."),
    BLANK_SANDBOX("Blank Sandbox", "A stripped-down project for custom pipelines, no demo assets, and blank menu wiring."),
    CUSTOM("Custom", "A manual combination of modules and starter content.");

    private final String label;
    private final String description;

    ProjectTemplate(String label, String description) {
      this.label = label;
      this.description = description;
    }

    String label() {
      return label;
    }

    String description() {
      return description;
    }

    @Override
    public String toString() {
      return label;
    }
  }

  private static final class PreviewNode {
    private final String name;
    private final String detail;
    private final boolean directory;
    private final ProjectFileIcons.Kind kind;

    private PreviewNode(String name, String detail, boolean directory, ProjectFileIcons.Kind kind) {
      this.name = name;
      this.detail = detail;
      this.directory = directory;
      this.kind = kind;
    }
  }

  // Result
  private File createdProjectDir = null;
  private final boolean gitAvailable;
  private boolean syncingFolderName = false;
  private boolean syncingProjectTemplate = false;

  // Form fields
  private Label lblHeaderSubtitle;
  private ComboBox<ProjectTemplate> cmbProjectTemplate;
  private Label lblProjectTemplateSummary;
  private TextField txtProjectName;
  private TextField txtFolderName;
  private CheckBox chkAutoFolderName;
  private TextField txtAuthor;
  private TextField txtLocation;
  private ComboBox<String> cmbResolution;
  private ComboBox<String> cmbTheme;
  private CheckBox chkCustomResolution;
  private TextField txtCustomWidth;
  private TextField txtCustomHeight;
  private Label lblAspectRatio;
  private ComboBox<String> cmbRuntimeUi;
  private ComboBox<String> cmbAudioBackend;
  private ComboBox<String> cmbLocale;
  private Spinner<Integer> spTextSpeed;
  private Spinner<Double> spBgmVolume;
  private Spinner<Double> spSfxVolume;
  private Spinner<Double> spVoiceVolume;
  private Spinner<Integer> spAutoDelay;
  private CheckBox chkSkipUnreadDefault;
  private CheckBox chkSkipAfterChoicesDefault;
  private Spinner<Integer> spPhysicsFixedStep;
  private Spinner<Integer> spPhysicsMaxSubsteps;
  private Spinner<Double> spPhysicsFriction;
  private TextField txtInputProfilePath;
  private CheckBox chkStarterStory;
  private CheckBox chkTutorialPack;
  private CheckBox chkBundledDemoAssets;
  private CheckBox chkTitleScreen;
  private CheckBox chkSaveSystem;
  private CheckBox chkSettingsMenu;
  private CheckBox chkHistoryBacklog;
  private CheckBox chkBlankMenus;
  private CheckBox chkGitInit;
  private CheckBox chkInitialCommit;
  private Label lblBlankMenuWarning;
  private TextArea txtDescription;
  private TreeView<PreviewNode> treeStructurePreview;
  private Label lblPreview;
  private Label lblTargetPath;
  private Label lblEstimatedSize;
  private Label lblValidation;
  private Button btnCreate;
  private static final String STYLE_FIELD_VALID = "new-project-wizard-field-valid";
  private static final String STYLE_FIELD_ERROR = "new-project-wizard-field-error";
  private static final String STYLE_VALIDATION_READY = "new-project-wizard-validation-ready";
  private static final String STYLE_VALIDATION_ERROR = "new-project-wizard-validation-error";

  // Project paths
  private static final String ENTRY_SCRIPT_PATH = "scripts/story/prologue.vns";
  private static final String STORY_TUTORIAL_SCRIPT_PATH = "scripts/story/tutorial_hub.vns";
  private static final String STORY_BRANCH_SCRIPT_PATH = "scripts/story/branch_demo.vns";
  private static final String STORY_EPILOGUE_SCRIPT_PATH = "scripts/story/epilogue.vns";
  private static final String TUTORIAL_DIALOGUE_SCRIPT_PATH = "scripts/tutorial/01_dialogue_basics.vns";
  private static final String TUTORIAL_NARRATION_SCRIPT_PATH = "scripts/tutorial/02_narration_and_pacing.vns";
  private static final String TUTORIAL_EXPRESSIONS_SCRIPT_PATH = "scripts/tutorial/03_expressions_and_characters.vns";
  private static final String TUTORIAL_IMAGES_SCRIPT_PATH = "scripts/tutorial/04_images_and_backgrounds.vns";
  private static final String TUTORIAL_TRANSITIONS_SCRIPT_PATH = "scripts/tutorial/05_transitions_and_effects.vns";
  private static final String TUTORIAL_AUDIO_SCRIPT_PATH = "scripts/tutorial/06_audio_and_music.vns";
  private static final String TUTORIAL_VARIABLES_SCRIPT_PATH = "scripts/tutorial/07_variables_and_conditions.vns";
  private static final String TUTORIAL_MOVEMENT_SCRIPT_PATH = "scripts/tutorial/08_character_movement.vns";
  private static final String TUTORIAL_PUPPETEER_SCRIPT_PATH = "scripts/tutorial/09_puppeteer_timeline.vns";
  private static final String TUTORIAL_MENUS_SCRIPT_PATH = "scripts/tutorial/10_choices_and_menus.vns";
  private static final String TUTORIAL_SUBROUTINES_SCRIPT_PATH = "scripts/tutorial/11_subroutines_and_flow.vns";
  private static final String TUTORIAL_BEST_PRACTICES_SCRIPT_PATH = "scripts/tutorial/12_best_practices.vns";
  private static final String TUTORIAL_CAMERA_SCRIPT_PATH = "scripts/tutorial/13_camera_and_staging.vns";
  private static final String TUTORIAL_LOCALIZATION_SCRIPT_PATH = "scripts/tutorial/14_localization_and_textkeys.vns";
  private static final String TUTORIAL_UI_LAYOUT_SCRIPT_PATH = "scripts/tutorial/15_ui_layout_and_theme.vns";
  private static final String TUTORIAL_TESTING_RELEASE_SCRIPT_PATH = "scripts/tutorial/16_testing_and_release.vns";
  private static final String TUTORIAL_VNS_JES_SCRIPT_PATH = "scripts/tutorial/17_vns_jes_integration.vns";
  private static final String JES_STUDIO_SCENE_PATH = "scripts/scenes/studio_tour.jes";
  private static final String ARC_PROLOGUE = "Prologue";
  private static final String ARC_TUTORIAL_HUB = "TutorialHub";
  private static final String ARC_BRANCH_DEMO = "BranchDemo";
  private static final String ARC_EPILOGUE = "Epilogue";
  private static final String ARC_T01_DIALOGUE = "T01_Dialogue";
  private static final String ARC_T02_NARRATION = "T02_Narration";
  private static final String ARC_T03_EXPRESSIONS = "T03_Expressions";
  private static final String ARC_T04_IMAGES = "T04_Images";
  private static final String ARC_T05_TRANSITIONS = "T05_Transitions";
  private static final String ARC_T06_AUDIO = "T06_Audio";
  private static final String ARC_T07_VARIABLES = "T07_Variables";
  private static final String ARC_T08_MOVEMENT = "T08_Movement";
  private static final String ARC_T09_PUPPETEER = "T09_Puppeteer";
  private static final String ARC_T10_MENUS = "T10_Menus";
  private static final String ARC_T11_SUBROUTINES = "T11_Subroutines";
  private static final String ARC_T12_BEST_PRACTICES = "T12_BestPractices";
  private static final String ARC_T13_CAMERA = "T13_Camera";
  private static final String ARC_T14_LOCALIZATION = "T14_Localization";
  private static final String ARC_T15_UI_LAYOUT = "T15_UILayout";
  private static final String ARC_T16_TESTING_RELEASE = "T16_TestingRelease";
  private static final String ARC_T17_VNS_JES = "T17_VnsJes";
  private static final String CHARACTERS_SCRIPT_PATH = "scripts/definitions/characters.vns";
  private static final String CHARACTERS_INCLUDE_PATH = "/definitions/characters.vns";
  private static final String STORY_MAP_PATH = StoryMapPaths.DEFAULT_PATH;
  private static final String SETTINGS_PATH = "config/settings/vn.settings";
  private static final String DIALOGUE_LAYOUT_PATH = "config/ui/dialogue.layout";
  private static final String MENU_THEME_PATH = "config/menu/theme/menu.theme";
  private static final String MENU_REGISTRY_PATH = "config/menu/registry/menu.registry";
  private static final String MENU_MAIN_PATH = "config/menu/menus/main.menu";
  private static final String MENU_EXTRAS_PATH = "config/menu/menus/extras.menu";
  private static final String MENU_CREDITS_PATH = "config/menu/menus/credits.menu";
  private static final String MENU_CONFIRM_EXIT_PATH = "config/menu/menus/confirm_exit.menu";
  private static final String MENU_LOAD_PATH = "config/menu/menus/load.menu";
  private static final String MENU_SAVE_PATH = "config/menu/menus/save.menu";
  private static final String MENU_SETTINGS_PATH = "config/menu/menus/settings.menu";
  private static final String MENU_LAYOUT_DEFAULT_PATH = "config/menu/layouts/default.layout";
  private static final String MENU_LAYOUT_SUBMENU_PATH = "config/menu/layouts/submenu.layout";
  private static final String MENU_LAYOUT_SETTINGS_PATH = "config/menu/layouts/settings.layout";
  private static final String MENU_LAYOUT_SLOTS_PATH = "config/menu/layouts/slots.layout";
  private static final String MENU_STYLE_DEFAULT_PATH = "config/menu/styles/default.style";
  private static final String MENU_STYLE_SUBMENU_PATH = "config/menu/styles/submenu.style";
  private static final String MENU_STYLE_SETTINGS_PATH = "config/menu/styles/settings.style";
  private static final String MENU_STYLE_SLOT_PATH = "config/menu/styles/slot.style";
  private static final String DEFAULT_MENU_BG_ASSET_PATH = "assets/demo/backgrounds/menu.png";
  private static final String BUNDLED_DEMO_ASSETS_DIR = "misc/demo-assets";
  private static final String BUNDLED_DEMO_RESOURCE_ROOT =
      "com/jvn/editor/templates/new-project/demo-assets/";
  private static final List<String> BUNDLED_DEMO_RESOURCE_FILES = List.of(
      "demo_bg/game.png",
      "demo_bg/menu.png",
      "Lavender_test_sprite/base/lavender_test_sprite_base.png",
      "Lavender_test_sprite/eyes/lavender_test_sprite_eyes_angry.png",
      "Lavender_test_sprite/eyes/lavender_test_sprite_eyes_closed.png",
      "Lavender_test_sprite/eyes/lavender_test_sprite_eyes_cross_closed.png",
      "Lavender_test_sprite/eyes/lavender_test_sprite_eyes_half_closed.png",
      "Lavender_test_sprite/eyes/lavender_test_sprite_eyes_neutral.png",
      "Lavender_test_sprite/mouth/lavender_test_sprite_mouth_happy.png",
      "Lavender_test_sprite/mouth/lavender_test_sprite_mouth_neutral.png",
      "Lavender_test_sprite/mouth/lavender_test_sprite_mouth_o.png",
      "Lavender_test_sprite/mouth/lavender_test_sprite_mouth_smile.png",
      "demo_bgm/03 - Definitely Our Town.mp3",
      "demo_bgm/DOCUMENTATION & LICENSE.pdf");
  private static final String BUNDLED_DEMO_BG_DIR = "demo_bg";
  private static final String BUNDLED_DEMO_SPRITE_LAYERED_DIR = "Lavender_test_sprite";
  private static final String BUNDLED_DEMO_SPRITE_LEGACY_DIR = "demo_sprite_codel";
  private static final String BUNDLED_DEMO_BGM_DIR = "demo_bgm";

  public NewProjectWizard(Stage owner) {
    initOwner(owner);
    initModality(Modality.APPLICATION_MODAL);
    initStyle(StageStyle.DECORATED);
    setTitle("Create New Visual Novel Project");
    setWidth(920);
    setHeight(760);
    setMinWidth(860);
    setMinHeight(680);
    setResizable(true);
    gitAvailable = new GitVcsService().isGitAvailable();

    BorderPane root = new BorderPane();
    addStyleClasses(root, "welcome-center-root", "new-project-wizard-root");

    VBox header = createHeader();
    root.setTop(header);

    ScrollPane scrollPane = new ScrollPane(createMainContent());
    scrollPane.setFitToWidth(true);
    addStyleClasses(scrollPane, "new-project-wizard-scroll");
    root.setCenter(scrollPane);

    HBox footer = createFooter();
    root.setBottom(footer);

    Scene scene = new Scene(root);
    EditorTheme.apply(scene);
    setScene(scene);

    applySelectedProjectTemplate();
    updateDerivedFields();
  }

  private VBox createHeader() {
    VBox header = new VBox(8);
    header.setPadding(new Insets(20, 28, 14, 28));
    addStyleClasses(header, "welcome-hero-card", "new-project-wizard-header");

    Label title = new Label("Create New Visual Novel");
    addStyleClasses(title, "welcome-heading");

    lblHeaderSubtitle = new Label("");
    addStyleClasses(lblHeaderSubtitle, "new-project-wizard-subtitle");

    Label hint = new Label("All settings can be changed later in the editor.");
    addStyleClasses(hint, "welcome-section-meta");

    header.getChildren().addAll(title, lblHeaderSubtitle, hint);
    return header;
  }

  private VBox createMainContent() {
    VBox content = new VBox(16);
    content.setPadding(new Insets(18, 28, 18, 28));
    addStyleClasses(content, "new-project-wizard-body");

    content.getChildren().addAll(
        createSection("Project Template", "Pick a starting point, then tune the scaffold below.", createProjectTemplatePane()),
        createSection("Project Basics", "Name, author, target directory, and output path.", createProjectBasicsGrid()),
        createSection("Engine Profile", "Runtime defaults and entry points for this project.", createEngineProfileGrid()),
        createSection("Playback Defaults", "Initial text/audio/physics/input settings for this project profile.", createPlaybackDefaultsPane()),
        createSection("Feature Modules", "Choose the base modules to scaffold.", createFeatureModulesPane()),
        createSection("Version Control", "Initialize Git so multi-person collaboration works from day one.", createVersionControlPane()),
        createSection("Generated Layout", "Preview the exact folders/files that will be created.", createGeneratedLayoutPane()),
        createSection("Project Notes", "Optional description saved to the project manifest and README.", createDescriptionArea())
    );

    return content;
  }

  private VBox createSection(String title, String subtitle, Region content) {
    VBox section = new VBox(10);
    section.setPadding(new Insets(14));
    addStyleClasses(section, "welcome-section-card", "new-project-wizard-section");

    Label titleLabel = new Label(title);
    addStyleClasses(titleLabel, "welcome-section-title");

    Label subtitleLabel = new Label(subtitle);
    addStyleClasses(subtitleLabel, "welcome-section-meta");

    Separator sep = new Separator();

    section.getChildren().addAll(titleLabel, subtitleLabel, sep, content);
    return section;
  }

  private Region createProjectTemplatePane() {
    VBox box = new VBox(10);

    cmbProjectTemplate = new ComboBox<>();
    cmbProjectTemplate.getItems().addAll(ProjectTemplate.values());
    cmbProjectTemplate.setValue(ProjectTemplate.STARTER_STORY);
    cmbProjectTemplate.setPrefWidth(260);
    styleField(cmbProjectTemplate);
    tip(cmbProjectTemplate, "Template presets change starter scripts, menu setup, and demo content. You can still override any option below.");
    cmbProjectTemplate.setOnAction(e -> applySelectedProjectTemplate());

    lblProjectTemplateSummary = new Label();
    lblProjectTemplateSummary.setWrapText(true);
    addStyleClasses(lblProjectTemplateSummary, "new-project-wizard-summary");

    FlowPane tags = new FlowPane();
    tags.setHgap(10);
    tags.setVgap(6);
    tags.getChildren().addAll(
        detailTag("Starter Story", "Sample prologue, branch, and epilogue scripts."),
        detailTag("Tutorial Pack", "Optional 30-topic learning set wired from tutorial_hub.vns."),
        detailTag("Blank Sandbox", "Minimal story content with blank menu registry wiring."),
        detailTag("Manual Override", "Selecting Custom preserves your checkbox choices.")
    );

    box.getChildren().addAll(cmbProjectTemplate, lblProjectTemplateSummary, tags);
    return box;
  }

  private Region createProjectBasicsGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    txtProjectName = createTextField("My Visual Novel");
    tip(txtProjectName, "Display name for the project. The folder name syncs automatically until you turn Auto off.");
    txtFolderName = createTextField(sanitizeName(txtProjectName.getText()));
    txtFolderName.setPrefWidth(280);
    tip(txtFolderName, "Project directory name. Safe characters only: letters, numbers, dot, underscore, hyphen.");
    chkAutoFolderName = createCheckBox("Auto", true);
    tip(chkAutoFolderName, "Keep folder name synced to the project name.");
    txtAuthor = createTextField("Anonymous");
    tip(txtAuthor, "Author name written to jvn.project manifest and README.");
    txtLocation = createTextField(System.getProperty("user.home") + "/JVN Projects");
    txtLocation.setPrefWidth(440);
    tip(txtLocation, "Parent directory where the project folder will be created.");

    txtProjectName.textProperty().addListener((o, ov, nv) -> {
      if (chkAutoFolderName != null && chkAutoFolderName.isSelected()) {
        syncFolderNameToProjectName();
      }
      updateDerivedFields();
    });
    txtLocation.textProperty().addListener((o, ov, nv) -> updateDerivedFields());
    txtFolderName.textProperty().addListener((o, ov, nv) -> {
      if (!syncingFolderName) {
        updateDerivedFields();
      }
    });
    chkAutoFolderName.selectedProperty().addListener((o, ov, nv) -> {
      boolean auto = nv != null && nv;
      txtFolderName.setDisable(auto);
      if (auto) {
        syncFolderNameToProjectName();
      }
      updateDerivedFields();
    });

    Button btnBrowse = new Button("Browse...");
    btnBrowse.setGraphic(CssIcon.folder("#d5b36a"));
    btnBrowse.setContentDisplay(ContentDisplay.LEFT);
    btnBrowse.setOnAction(e -> browseLocation());
    styleSecondaryButton(btnBrowse);

    HBox locationRow = new HBox(8, txtLocation, btnBrowse);
    HBox.setHgrow(txtLocation, Priority.ALWAYS);
    HBox folderRow = new HBox(8, txtFolderName, chkAutoFolderName);
    folderRow.setAlignment(Pos.CENTER_LEFT);
    HBox.setHgrow(txtFolderName, Priority.ALWAYS);
    txtFolderName.setDisable(true);

    lblTargetPath = new Label();
    lblTargetPath.setWrapText(true);
    addStyleClasses(lblTargetPath, "new-project-wizard-path");

    Label slugHint = new Label("Folder name is sanitized automatically for cross-platform safety.");
    addStyleClasses(slugHint, "welcome-section-meta");

    grid.add(createLabel("Project Name"), 0, 0);
    grid.add(txtProjectName, 1, 0);
    grid.add(createLabel("Folder Name"), 0, 1);
    grid.add(folderRow, 1, 1);
    grid.add(createLabel("Author"), 0, 2);
    grid.add(txtAuthor, 1, 2);
    grid.add(createLabel("Location"), 0, 3);
    grid.add(locationRow, 1, 3);
    grid.add(createLabel("Output Path"), 0, 4);
    grid.add(lblTargetPath, 1, 4);
    grid.add(slugHint, 1, 5);

    GridPane.setHgrow(txtProjectName, Priority.ALWAYS);
    GridPane.setHgrow(folderRow, Priority.ALWAYS);
    GridPane.setHgrow(txtAuthor, Priority.ALWAYS);
    GridPane.setHgrow(locationRow, Priority.ALWAYS);

    return grid;
  }

  private Region createEngineProfileGrid() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    cmbResolution = new ComboBox<>();
    cmbResolution.getItems().addAll(
        "7680x4320 (8K UHD)",
        "5120x2880 (5K)",
        "5120x1440 (Super UltraWide)",
        "3840x2160 (4K UHD)",
        "3440x1440 (UltraWide QHD)",
        "2560x1440 (QHD)",
        "2560x1080 (UltraWide FHD)",
        "1920x1080 (Full HD)",
        "1600x900 (HD+)",
        "1366x768 (WXGA)",
        "1280x720 (HD)",
        "960x540 (qHD)"
    );
    cmbResolution.setValue("1920x1080 (Full HD)");
    cmbResolution.setPrefWidth(230);
    styleField(cmbResolution);
    tip(cmbResolution, "Target rendering resolution. Affects dialogue layout scaling and menu positioning.");

    chkCustomResolution = createCheckBox("Custom Resolution", false);
    txtCustomWidth = createTextField("2560");
    txtCustomWidth.setPrefWidth(90);
    txtCustomHeight = createTextField("1440");
    txtCustomHeight.setPrefWidth(90);
    txtCustomWidth.setDisable(true);
    txtCustomHeight.setDisable(true);
    Label resolutionSeparator = new Label("x");
    addStyleClasses(resolutionSeparator, "welcome-section-meta");
    HBox customRow = new HBox(8, chkCustomResolution, txtCustomWidth, resolutionSeparator, txtCustomHeight);
    customRow.setAlignment(Pos.CENTER_LEFT);

    lblAspectRatio = new Label();
    addStyleClasses(lblAspectRatio, "welcome-section-meta");

    cmbTheme = new ComboBox<>();
    cmbTheme.getItems().addAll(
        "Monochrome Sketch",
        "Dark Elegant",
        "Light Clean",
        "Retro Game",
        "Nature Green",
        "Custom"
    );
    cmbTheme.setValue("Monochrome Sketch");
    cmbTheme.setPrefWidth(230);
    styleField(cmbTheme);
    tip(cmbTheme, "Color palette preset for menu theme. Affects title, items, hints, and background colors.");

    cmbRuntimeUi = new ComboBox<>();
    cmbRuntimeUi.getItems().addAll("fx", "swing");
    cmbRuntimeUi.setValue("fx");
    cmbRuntimeUi.setPrefWidth(230);
    styleField(cmbRuntimeUi);
    tip(cmbRuntimeUi, "UI toolkit for the runtime renderer. 'fx' (JavaFX) is recommended for most projects.");

    cmbAudioBackend = new ComboBox<>();
    cmbAudioBackend.getItems().addAll("auto", "simp3", "fx");
    cmbAudioBackend.setValue("auto");
    cmbAudioBackend.setPrefWidth(230);
    styleField(cmbAudioBackend);
    tip(cmbAudioBackend, "Audio playback backend. 'auto' selects the best available (simp3 for MP3, fx for WAV).");

    cmbLocale = new ComboBox<>();
    cmbLocale.getItems().addAll("en", "de", "es", "fr", "it", "ja", "ko", "pt-BR", "tr", "zh-CN");
    cmbLocale.setEditable(true);
    cmbLocale.setValue("en");
    cmbLocale.setPrefWidth(230);
    styleField(cmbLocale);
    tip(cmbLocale, "Default locale for text mapping. Creates config/locales/<locale>.properties. Type a custom code if needed.");

    cmbResolution.setOnAction(e -> updateDerivedFields());
    cmbTheme.setOnAction(e -> updateDerivedFields());
    cmbRuntimeUi.setOnAction(e -> updateDerivedFields());
    cmbAudioBackend.setOnAction(e -> updateDerivedFields());
    cmbLocale.setOnAction(e -> updateDerivedFields());
    chkCustomResolution.selectedProperty().addListener((o, ov, nv) -> {
      boolean custom = nv != null && nv;
      txtCustomWidth.setDisable(!custom);
      txtCustomHeight.setDisable(!custom);
      cmbResolution.setDisable(custom);
      updateDerivedFields();
    });
    txtCustomWidth.textProperty().addListener((o, ov, nv) -> updateDerivedFields());
    txtCustomHeight.textProperty().addListener((o, ov, nv) -> updateDerivedFields());

    lblPreview = new Label();
    addStyleClasses(lblPreview, "new-project-wizard-summary");

    Label entryInfo = new Label(
        "Entry script: " + ENTRY_SCRIPT_PATH + "\n" +
        "Arc scripts: " + STORY_TUTORIAL_SCRIPT_PATH + ", " + STORY_BRANCH_SCRIPT_PATH + ", " + STORY_EPILOGUE_SCRIPT_PATH + "\n" +
        "Story map: " + STORY_MAP_PATH + "\n" +
        "Dialogue layout: " + DIALOGUE_LAYOUT_PATH + "\n" +
        "Menu registry: " + MENU_REGISTRY_PATH
    );
    addStyleClasses(entryInfo, "new-project-wizard-path");

    grid.add(createLabel("Resolution"), 0, 0);
    grid.add(cmbResolution, 1, 0);
    grid.add(createLabel("Custom"), 0, 1);
    grid.add(customRow, 1, 1);
    grid.add(createLabel("Aspect"), 0, 2);
    grid.add(lblAspectRatio, 1, 2);
    grid.add(createLabel("Menu Theme"), 0, 3);
    grid.add(cmbTheme, 1, 3);
    grid.add(createLabel("Runtime UI"), 0, 4);
    grid.add(cmbRuntimeUi, 1, 4);
    grid.add(createLabel("Audio Backend"), 0, 5);
    grid.add(cmbAudioBackend, 1, 5);
    grid.add(createLabel("Locale"), 0, 6);
    grid.add(cmbLocale, 1, 6);
    grid.add(createLabel("Preset"), 0, 7);
    grid.add(lblPreview, 1, 7);
    grid.add(new Label(""), 0, 8);
    grid.add(entryInfo, 1, 8);

    return grid;
  }

  private Region createPlaybackDefaultsPane() {
    GridPane grid = new GridPane();
    grid.setHgap(14);
    grid.setVgap(10);

    spTextSpeed = createIntSpinner(10, 120, 35, 1);
    tip(spTextSpeed, "Characters per second for dialogue typewriter effect (10–120).");
    spAutoDelay = createIntSpinner(500, 5000, 2000, 100);
    tip(spAutoDelay, "Milliseconds to wait before auto-advancing dialogue (500–5000).");
    spBgmVolume = createDoubleSpinner(0.0, 1.0, 0.70, 0.05);
    tip(spBgmVolume, "Default background music volume (0.0–1.0).");
    spSfxVolume = createDoubleSpinner(0.0, 1.0, 0.80, 0.05);
    tip(spSfxVolume, "Default sound effects volume (0.0–1.0).");
    spVoiceVolume = createDoubleSpinner(0.0, 1.0, 1.00, 0.05);
    tip(spVoiceVolume, "Default voice audio volume (0.0–1.0).");
    chkSkipUnreadDefault = createCheckBox("Skip unread text by default", false);
    tip(chkSkipUnreadDefault, "When enabled, skip mode also skips text the player hasn't seen yet.");
    chkSkipAfterChoicesDefault = createCheckBox("Skip after choices by default", false);
    tip(chkSkipAfterChoicesDefault, "When enabled, skip mode continues automatically after making a choice.");
    spPhysicsFixedStep = createIntSpinner(0, 50, 0, 5);
    tip(spPhysicsFixedStep, "Fixed timestep in ms for physics simulation. 0 = variable timestep.");
    spPhysicsMaxSubsteps = createIntSpinner(1, 8, 4, 1);
    tip(spPhysicsMaxSubsteps, "Maximum physics sub-steps per frame (1–8).");
    spPhysicsFriction = createDoubleSpinner(0.0, 1.0, 0.20, 0.05);
    tip(spPhysicsFriction, "Default friction coefficient for physics bodies (0.0–1.0).");
    txtInputProfilePath = createTextField(System.getProperty("user.home") + "/.jvn/input-bindings.properties");
    tip(txtInputProfilePath, "Path to input bindings profile. Shared across projects by default.");

    Label note = new Label(
        "These defaults are written to config/settings/vn.settings and can be changed later in Settings Editor.\n"
        + "Physics fixed step: 0 means variable timestep."
    );
    note.setWrapText(true);
    addStyleClasses(note, "welcome-section-meta");

    grid.add(createLabel("Text Speed"), 0, 0);
    grid.add(spTextSpeed, 1, 0);
    grid.add(createLabel("Auto Delay"), 0, 1);
    grid.add(spAutoDelay, 1, 1);
    grid.add(createLabel("BGM Volume"), 0, 2);
    grid.add(spBgmVolume, 1, 2);
    grid.add(createLabel("SFX Volume"), 0, 3);
    grid.add(spSfxVolume, 1, 3);
    grid.add(createLabel("Voice Volume"), 0, 4);
    grid.add(spVoiceVolume, 1, 4);
    grid.add(createLabel("Skip Defaults"), 0, 5);
    grid.add(new VBox(6, chkSkipUnreadDefault, chkSkipAfterChoicesDefault), 1, 5);
    grid.add(createLabel("Physics Step"), 0, 6);
    grid.add(spPhysicsFixedStep, 1, 6);
    grid.add(createLabel("Physics Substeps"), 0, 7);
    grid.add(spPhysicsMaxSubsteps, 1, 7);
    grid.add(createLabel("Default Friction"), 0, 8);
    grid.add(spPhysicsFriction, 1, 8);
    grid.add(createLabel("Input Profile"), 0, 9);
    grid.add(txtInputProfilePath, 1, 9);
    grid.add(new Label(""), 0, 10);
    grid.add(note, 1, 10);

    GridPane.setHgrow(txtInputProfilePath, Priority.ALWAYS);
    return grid;
  }

  private Region createFeatureModulesPane() {
    VBox box = new VBox(10);

    Label intro = new Label("These options control both scaffolding and starter content.");
    addStyleClasses(intro, "new-project-wizard-summary");

    chkStarterStory = createCheckBox("Starter Story Flow", true);
    chkTutorialPack = createCheckBox("Guided Tutorial Pack (16 scripts)", false);
    chkBundledDemoAssets = createCheckBox("Bundled Demo Assets (Lavender/White Stage/BGM)", true);
    chkTitleScreen = createCheckBox("Main Menu Profile Pack", true);
    chkSaveSystem = createCheckBox("Load/Save Menu Profiles", true);
    chkSettingsMenu = createCheckBox("Settings Menu Profile", true);
    chkHistoryBacklog = createCheckBox("History/Backlog Defaults", true);
    chkBlankMenus = createCheckBox("Start from Zero (Custom Menus)", false);

    lblBlankMenuWarning = new Label(
        "Recommended for projects with extensive custom menu plans or assets.\n"
        + "Until menu layouts are configured and wired in the registry, the following\n"
        + "in-game GUI features will be unavailable:\n"
        + "  \u2022 Save Game / Load Game menus\n"
        + "  \u2022 Rollback / state restore from menus\n"
        + "  \u2022 Settings menu\n"
        + "  \u2022 Main menu / title screen navigation\n"
        + "  \u2022 In-game pause overlay\n"
        + "Game progress can only be viewed through VNS file preview until menus are set up."
    );
    lblBlankMenuWarning.setWrapText(true);
    lblBlankMenuWarning.setPadding(new Insets(8, 12, 8, 12));
    addStyleClasses(lblBlankMenuWarning, "new-project-wizard-warning-card");
    lblBlankMenuWarning.setVisible(false);
    lblBlankMenuWarning.setManaged(false);

    chkStarterStory.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkTutorialPack.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkBundledDemoAssets.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkTitleScreen.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkHistoryBacklog.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());
    chkSaveSystem.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv && !chkTitleScreen.isSelected()) chkTitleScreen.setSelected(true);
      updateDerivedFields();
    });
    chkSettingsMenu.selectedProperty().addListener((o, ov, nv) -> {
      if (nv != null && nv && !chkTitleScreen.isSelected()) chkTitleScreen.setSelected(true);
      updateDerivedFields();
    });
    chkBlankMenus.selectedProperty().addListener((o, ov, nv) -> {
      boolean blank = nv != null && nv;
      chkTitleScreen.setDisable(blank);
      chkSaveSystem.setDisable(blank);
      chkSettingsMenu.setDisable(blank);
      if (blank) {
        chkTitleScreen.setSelected(false);
        chkSaveSystem.setSelected(false);
        chkSettingsMenu.setSelected(false);
      }
      lblBlankMenuWarning.setVisible(blank);
      lblBlankMenuWarning.setManaged(blank);
      updateDerivedFields();
    });

    GridPane options = new GridPane();
    options.setHgap(28);
    options.setVgap(8);
    options.add(chkStarterStory, 0, 0);
    options.add(chkTutorialPack, 1, 0);
    options.add(chkBundledDemoAssets, 0, 1);
    options.add(chkTitleScreen, 1, 1);
    options.add(chkSaveSystem, 0, 2);
    options.add(chkSettingsMenu, 1, 2);
    options.add(chkHistoryBacklog, 0, 3);
    options.add(chkBlankMenus, 1, 3);

    FlowPane details = new FlowPane();
    details.setVgap(4);
    details.setHgap(16);
    details.getChildren().addAll(
        detailTag("Starter Story", "Sample prologue, route split, and epilogue scripts."),
        detailTag("Tutorial Pack", "Thirty lessons across JVN linked from the tutorial hub."),
        detailTag("Demo Assets", "Copies bundled lavender/audio and plain white stage starter assets."),
        detailTag("Menu Profiles", "Creates config/menu registry, screens, layout and style."),
        detailTag("Save/Load", "Adds load.menu and save.menu defaults."),
        detailTag("Settings", "Adds settings.menu profile entries."),
        detailTag("History Defaults", "Marks backlog defaults in vn.settings."),
        detailTag("Blank Menus", "No default menus. Build everything from scratch.")
    );

    box.getChildren().addAll(intro, options, lblBlankMenuWarning, details);
    return box;
  }

  private Region detailTag(String title, String subtitle) {
    VBox tag = new VBox(2);
    tag.setPadding(new Insets(8, 10, 8, 10));
    addStyleClasses(tag, "new-project-wizard-detail-tag");

    Label t = new Label(title);
    addStyleClasses(t, "new-project-wizard-detail-tag-title");

    Label s = new Label(subtitle);
    addStyleClasses(s, "new-project-wizard-detail-tag-copy");

    tag.getChildren().addAll(t, s);
    return tag;
  }

  private Region createVersionControlPane() {
    VBox box = new VBox(10);

    Label intro = new Label(
        "Optional: enable Git initialization for team workflows. " +
        "If disabled (or unavailable), project creation is unaffected."
    );
    intro.setWrapText(true);
    addStyleClasses(intro, "new-project-wizard-summary");

    chkGitInit = createCheckBox("Initialize Git repository", false);
    chkInitialCommit = createCheckBox("Create initial commit", false);

    if (!gitAvailable) {
      chkGitInit.setSelected(false);
      chkInitialCommit.setSelected(false);
      chkGitInit.setDisable(true);
      chkInitialCommit.setDisable(true);
    }

    chkGitInit.selectedProperty().addListener((o, ov, nv) -> {
      boolean enabled = nv != null && nv;
      chkInitialCommit.setDisable(!enabled);
      if (!enabled) {
        chkInitialCommit.setSelected(false);
      } else {
        if (!chkInitialCommit.isSelected()) chkInitialCommit.setSelected(true);
      }
      updateDerivedFields();
    });

    chkInitialCommit.selectedProperty().addListener((o, ov, nv) -> updateDerivedFields());

    Label note = new Label(
        gitAvailable
            ? "Default ignore patterns are optimized for JVN projects and generated runtime files."
            : "Git is not detected on PATH. You can still create and run projects normally."
    );
    note.setWrapText(true);
    addStyleClasses(note, "welcome-section-meta");

    box.getChildren().addAll(intro, chkGitInit, chkInitialCommit, note);
    return box;
  }

  private Region createGeneratedLayoutPane() {
    VBox box = new VBox(8);

    treeStructurePreview = new TreeView<>();
    treeStructurePreview.setShowRoot(true);
    treeStructurePreview.setPrefHeight(360);
    addStyleClasses(treeStructurePreview, "new-project-wizard-structure-tree");
    treeStructurePreview.setCellFactory(tree -> new TreeCell<>() {
      @Override
      protected void updateItem(PreviewNode item, boolean empty) {
        super.updateItem(item, empty);
        if (empty || item == null) {
          setText(null);
          setGraphic(null);
          return;
        }
        Label nameLabel = new Label(item.name);
        nameLabel.getStyleClass().add(item.directory ? "new-project-wizard-tree-dir-label" : "new-project-wizard-tree-file-label");
        HBox row = new HBox(6, previewIcon(item), nameLabel);
        row.setAlignment(Pos.CENTER_LEFT);
        if (item.detail != null && !item.detail.isBlank()) {
          Label detailLabel = new Label(item.detail);
          detailLabel.getStyleClass().add("new-project-wizard-tree-detail-label");
          row.getChildren().add(detailLabel);
        }
        setText(null);
        setGraphic(row);
      }
    });

    Label note = new Label("This preview updates live based on your selected modules.");
    addStyleClasses(note, "welcome-section-meta");

    box.getChildren().addAll(treeStructurePreview, note);
    return box;
  }

  private Region createDescriptionArea() {
    VBox box = new VBox(8);

    Label info = new Label("Optional project description:");
    addStyleClasses(info, "new-project-wizard-summary");

    txtDescription = new TextArea();
    txtDescription.setPromptText("Example: A sci-fi mystery told across branching routes.");
    txtDescription.setPrefRowCount(3);
    txtDescription.setWrapText(true);
    addStyleClasses(txtDescription, "new-project-wizard-description");

    box.getChildren().addAll(info, txtDescription);
    return box;
  }

  private HBox createFooter() {
    HBox footer = new HBox(12);
    footer.setPadding(new Insets(14, 28, 18, 28));
    footer.setAlignment(Pos.CENTER_RIGHT);
    addStyleClasses(footer, "new-project-wizard-footer");

    lblEstimatedSize = new Label();
    addStyleClasses(lblEstimatedSize, "welcome-section-meta");

    lblValidation = new Label();
    lblValidation.setWrapText(true);
    lblValidation.setMaxWidth(380);
    addStyleClasses(lblValidation, "new-project-wizard-validation");

    Button btnCancel = new Button("Cancel");
    btnCancel.setPrefWidth(110);
    btnCancel.setGraphic(CssIcon.clearX("#f0a1b2"));
    btnCancel.setContentDisplay(ContentDisplay.LEFT);
    styleSecondaryButton(btnCancel);
    btnCancel.setOnAction(e -> close());

    btnCreate = new Button("Create Project");
    btnCreate.setPrefWidth(150);
    btnCreate.setGraphic(CssIcon.plusBold("#8bcf98"));
    btnCreate.setContentDisplay(ContentDisplay.LEFT);
    stylePrimaryButton(btnCreate);
    btnCreate.setOnAction(e -> createProject());

    Region spacer = new Region();
    HBox.setHgrow(spacer, Priority.ALWAYS);

    footer.getChildren().addAll(lblEstimatedSize, lblValidation, spacer, btnCancel, btnCreate);
    return footer;
  }

  private Label createLabel(String text) {
    Label label = new Label(text);
    label.setMinWidth(110);
    addStyleClasses(label, "new-project-wizard-form-label");
    return label;
  }

  private TextField createTextField(String defaultValue) {
    TextField tf = new TextField(defaultValue);
    tf.setPrefWidth(280);
    styleField(tf);
    return tf;
  }

  private CheckBox createCheckBox(String text, boolean selected) {
    CheckBox cb = new CheckBox(text);
    cb.setSelected(selected);
    addStyleClasses(cb, "new-project-wizard-checkbox");
    return cb;
  }

  private void browseLocation() {
    DirectoryChooser chooser = new DirectoryChooser();
    chooser.setTitle("Choose Project Location");
    File initial = new File(txtLocation.getText().trim());
    if (initial.exists() && initial.isDirectory()) chooser.setInitialDirectory(initial);
    File dir = chooser.showDialog(this);
    if (dir != null) {
      txtLocation.setText(dir.getAbsolutePath());
      updateDerivedFields();
    }
  }

  private void updateDerivedFields() {
    syncProjectTemplateSelection();
    updatePresetPreview();
    updateTargetPathLabel();
    updateStructurePreview();
    updateEstimatedSize();
    validateForm();
  }

  private void applySelectedProjectTemplate() {
    if (syncingProjectTemplate || cmbProjectTemplate == null) return;
    ProjectTemplate template = cmbProjectTemplate.getValue();
    if (template == null || template == ProjectTemplate.CUSTOM) {
      updateProjectTemplateSummary();
      updateDerivedFields();
      return;
    }
    syncingProjectTemplate = true;
    try {
      boolean blank = template == ProjectTemplate.BLANK_SANDBOX;
      if (chkStarterStory != null) chkStarterStory.setSelected(template != ProjectTemplate.BLANK_SANDBOX);
      if (chkTutorialPack != null) chkTutorialPack.setSelected(template == ProjectTemplate.TUTORIAL_WORKSPACE);
      if (chkBundledDemoAssets != null) chkBundledDemoAssets.setSelected(!blank);
      if (chkTitleScreen != null) chkTitleScreen.setSelected(!blank);
      if (chkSaveSystem != null) chkSaveSystem.setSelected(!blank);
      if (chkSettingsMenu != null) chkSettingsMenu.setSelected(!blank);
      if (chkHistoryBacklog != null) chkHistoryBacklog.setSelected(!blank);
      if (chkBlankMenus != null) chkBlankMenus.setSelected(blank);
    } finally {
      syncingProjectTemplate = false;
    }
    updateProjectTemplateSummary();
    updateDerivedFields();
  }

  private void syncProjectTemplateSelection() {
    ProjectTemplate resolved = resolveProjectTemplateFromSelections();
    if (cmbProjectTemplate != null && !syncingProjectTemplate && cmbProjectTemplate.getValue() != resolved) {
      syncingProjectTemplate = true;
      try {
        cmbProjectTemplate.setValue(resolved);
      } finally {
        syncingProjectTemplate = false;
      }
    }
    updateProjectTemplateSummary();
  }

  private ProjectTemplate resolveProjectTemplateFromSelections() {
    boolean starterStory = chkStarterStory != null && chkStarterStory.isSelected();
    boolean tutorialPack = chkTutorialPack != null && chkTutorialPack.isSelected();
    boolean demoAssets = chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected();
    boolean titleScreen = chkTitleScreen != null && chkTitleScreen.isSelected();
    boolean saveSystem = chkSaveSystem != null && chkSaveSystem.isSelected();
    boolean settingsMenu = chkSettingsMenu != null && chkSettingsMenu.isSelected();
    boolean historyBacklog = chkHistoryBacklog != null && chkHistoryBacklog.isSelected();
    boolean blankMenus = chkBlankMenus != null && chkBlankMenus.isSelected();

    if (starterStory && !tutorialPack && demoAssets && titleScreen && saveSystem && settingsMenu && historyBacklog && !blankMenus) {
      return ProjectTemplate.STARTER_STORY;
    }
    if (starterStory && tutorialPack && demoAssets && titleScreen && saveSystem && settingsMenu && historyBacklog && !blankMenus) {
      return ProjectTemplate.TUTORIAL_WORKSPACE;
    }
    if (!starterStory && !tutorialPack && !demoAssets && !titleScreen && !saveSystem && !settingsMenu && !historyBacklog && blankMenus) {
      return ProjectTemplate.BLANK_SANDBOX;
    }
    return ProjectTemplate.CUSTOM;
  }

  private void updateProjectTemplateSummary() {
    if (lblProjectTemplateSummary == null) return;
    ProjectTemplate template = cmbProjectTemplate == null || cmbProjectTemplate.getValue() == null
        ? ProjectTemplate.STARTER_STORY
        : cmbProjectTemplate.getValue();
    String summary = switch (template) {
      case STARTER_STORY -> "Recommended default. Creates a playable story skeleton, demo assets, and ready-to-run menu profiles without the larger tutorial pack.";
      case TUTORIAL_WORKSPACE -> "Creates the starter story plus the full 30-script guided tutorial pack and tutorial hub routes.";
      case BLANK_SANDBOX -> "Creates the runtime/config skeleton with blank story scripts, no bundled assets, and blank menu wiring.";
      case CUSTOM -> "Custom combination detected. The wizard will scaffold exactly what your current checkboxes describe.";
    };
    lblProjectTemplateSummary.setText(summary);
  }

  private void syncFolderNameToProjectName() {
    if (txtFolderName == null) return;
    syncingFolderName = true;
    try {
      txtFolderName.setText(sanitizeName(txtProjectName == null ? "" : txtProjectName.getText()));
    } finally {
      syncingFolderName = false;
    }
  }

  private void validateForm() {
    if (lblValidation == null || btnCreate == null) return;
    String name = txtProjectName == null ? "" : txtProjectName.getText().trim();
    String location = txtLocation == null ? "" : txtLocation.getText().trim();

    List<String> errors = new ArrayList<>();
    String folderName = resolveFolderName();

    if (name.isEmpty()) {
      errors.add("Project name is required.");
      setFieldState(txtProjectName, STYLE_FIELD_ERROR);
    } else if (sanitizeName(name).isBlank()) {
      errors.add("Name must contain at least one letter or number.");
      setFieldState(txtProjectName, STYLE_FIELD_ERROR);
    } else {
      setFieldState(txtProjectName, STYLE_FIELD_VALID);
    }

    if (txtFolderName != null) {
      if (folderName.isBlank()) {
        errors.add("Folder name is required.");
        setFieldState(txtFolderName, STYLE_FIELD_ERROR);
      } else if (!isSafeFolderName(folderName)) {
        errors.add("Folder name can only use letters, numbers, dot, underscore, or hyphen.");
        setFieldState(txtFolderName, STYLE_FIELD_ERROR);
      } else {
        setFieldState(txtFolderName, STYLE_FIELD_VALID);
      }
    }

    if (location.isEmpty()) {
      errors.add("Project location is required.");
      setFieldState(txtLocation, STYLE_FIELD_ERROR);
    } else {
      File base = new File(location);
      if (base.exists() && !base.isDirectory()) {
        errors.add("Project location must be a directory.");
        setFieldState(txtLocation, STYLE_FIELD_ERROR);
      } else if (base.exists() && !base.canWrite()) {
        errors.add("Project location is not writable.");
        setFieldState(txtLocation, STYLE_FIELD_ERROR);
      } else if (folderName.isBlank()) {
        setFieldState(txtLocation, STYLE_FIELD_ERROR);
      } else {
        File target = new File(location, folderName);
        if (target.exists()) {
          errors.add("Folder already exists: " + folderName);
          setFieldState(txtLocation, STYLE_FIELD_ERROR);
        } else {
          setFieldState(txtLocation, STYLE_FIELD_VALID);
        }
      }
    }

    if (chkCustomResolution != null && chkCustomResolution.isSelected()) {
      boolean widthValid = validateCustomDimensionField(txtCustomWidth);
      boolean heightValid = validateCustomDimensionField(txtCustomHeight);
      if (!widthValid || !heightValid) {
        errors.add("Custom resolution must use whole numbers between 320 and 8192.");
      }
      setFieldState(txtCustomWidth, widthValid ? STYLE_FIELD_VALID : STYLE_FIELD_ERROR);
      setFieldState(txtCustomHeight, heightValid ? STYLE_FIELD_VALID : STYLE_FIELD_ERROR);
    } else {
      setFieldState(txtCustomWidth, null);
      setFieldState(txtCustomHeight, null);
    }

    if (cmbLocale != null) {
      String locale = cmbLocale.getValue() == null ? "" : cmbLocale.getValue().trim();
      if (locale.isBlank()) {
        errors.add("Locale is required.");
      } else if (!locale.matches("[A-Za-z0-9_-]+")) {
        errors.add("Locale may only use letters, numbers, hyphen, or underscore.");
      }
    }

    if (errors.isEmpty()) {
      setValidationMessage(true, "\u2714 Ready to create");
    } else {
      setValidationMessage(false, String.join(" ", errors));
    }
  }

  private boolean validateCustomDimensionField(TextField field) {
    if (field == null) return false;
    String raw = field.getText();
    if (raw == null || raw.isBlank()) return false;
    try {
      int value = Integer.parseInt(raw.trim());
      return value >= 320 && value <= 8192;
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return false;
    }
  }

  private String resolveFolderName() {
    if (chkAutoFolderName != null && chkAutoFolderName.isSelected()) {
      return sanitizeName(txtProjectName == null ? "" : txtProjectName.getText());
    }
    return txtFolderName == null ? "" : txtFolderName.getText().trim();
  }

  static boolean isSafeFolderName(String name) {
    return name != null && !name.isBlank() && name.matches("[A-Za-z0-9._-]+");
  }

  private void updatePresetPreview() {
    if (lblPreview == null || cmbTheme == null || cmbResolution == null) return;
    String name = txtProjectName == null ? "" : txtProjectName.getText().trim();
    if (name.isBlank()) name = "Untitled";
    int[] resolution = getScaledResolution();
    String res = resolution[0] + "x" + resolution[1];
    String ratio = formatAspectRatio(resolution[0], resolution[1]);
    String theme = cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue();
    String runtimeUi = cmbRuntimeUi == null || cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue();
    String audioBackend = cmbAudioBackend == null || cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue();
    String locale = cmbLocale == null || cmbLocale.getValue() == null ? "en" : cmbLocale.getValue();
    String source = chkCustomResolution != null && chkCustomResolution.isSelected() ? "custom" : "preset";
    ProjectTemplate template = cmbProjectTemplate == null || cmbProjectTemplate.getValue() == null
        ? ProjectTemplate.STARTER_STORY
        : cmbProjectTemplate.getValue();
    if (lblAspectRatio != null) {
      lblAspectRatio.setText(ratio + " (" + source + ")");
    }
    if (lblHeaderSubtitle != null) {
      lblHeaderSubtitle.setText(template.label() + " • " + resolveFolderName() + " • " + res + " • " + runtimeUi + "/" + audioBackend);
    }
    lblPreview.setText("\"" + name + "\" • " + res + " • " + ratio + " • " + theme
        + " • " + runtimeUi + "/" + audioBackend + " • " + locale);
  }

  private void updateTargetPathLabel() {
    if (lblTargetPath == null) return;
    String location = txtLocation == null ? "" : txtLocation.getText().trim();
    String folderName = resolveFolderName();
    if (location.isBlank()) {
      lblTargetPath.setText("(select a location)");
      return;
    }
    if (folderName.isBlank()) {
      lblTargetPath.setText(new File(location).getAbsolutePath());
      return;
    }
    lblTargetPath.setText(new File(location, folderName).getAbsolutePath());
  }

  private void updateStructurePreview() {
    if (treeStructurePreview == null) return;
    String folderName = resolveFolderName();
    if (folderName.isBlank()) folderName = "my_visual_novel";
    treeStructurePreview.setRoot(buildStructurePreviewTree(folderName));
  }

  private void updateEstimatedSize() {
    if (lblEstimatedSize == null) return;
    lblEstimatedSize.setText("Estimated scaffold size: ~" + estimateProjectSizeKb() + " KB");
  }

  private long estimateProjectSizeKb() {
    long kb = 36;
    if (chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected()) {
      kb += estimateBundledDemoAssetsKb();
    }
    if (chkStarterStory != null && chkStarterStory.isSelected()) kb += 4;
    if (chkTutorialPack != null && chkTutorialPack.isSelected()) kb += 8;
    if (shouldCreateMenuPack()) kb += 8;
    if (chkSaveSystem != null && chkSaveSystem.isSelected()) kb += 3;
    if (chkSettingsMenu != null && chkSettingsMenu.isSelected()) kb += 2;
    if (chkHistoryBacklog != null && chkHistoryBacklog.isSelected()) kb += 1;
    if (shouldSetupGit()) kb += 1;
    return kb;
  }

  private String buildStructurePreviewText(String projectFolderName) {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem != null && chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu != null && chkSettingsMenu.isSelected();
    boolean includeDemoAssets = chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected();
    boolean includeStarterStory = chkStarterStory != null && chkStarterStory.isSelected();
    boolean includeTutorialPack = chkTutorialPack != null && chkTutorialPack.isSelected();
    String locale = cmbLocale == null || cmbLocale.getValue() == null || cmbLocale.getValue().isBlank() ? "en" : cmbLocale.getValue().trim();

    StringBuilder sb = new StringBuilder();
    sb.append(projectFolderName).append("/\n");
    sb.append("\u251c\u2500\u2500 config/\n");
    sb.append("\u2502   \u251c\u2500\u2500 settings/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 vn.settings\n");
    sb.append("\u2502   \u251c\u2500\u2500 story/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 story.storymap\n");
    sb.append("\u2502   \u251c\u2500\u2500 ui/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 dialogue.layout\n");
    sb.append("\u2502   \u251c\u2500\u2500 locales/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 ").append(locale).append(".properties\n");
    sb.append("\u2502   \u251c\u2500\u2500 puppeteer/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 clips/\n");
    boolean blankMenus = shouldStartBlankMenus();
    if (includeMenuPack) {
      sb.append("\u2502   \u2514\u2500\u2500 menu/\n");
      sb.append("\u2502       \u251c\u2500\u2500 registry/\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 menu.registry\n");
      sb.append("\u2502       \u251c\u2500\u2500 theme/\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 menu.theme\n");
      sb.append("\u2502       \u251c\u2500\u2500 menus/\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 main.menu\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 extras.menu\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 credits.menu\n");
      boolean lastMenu = !includeSave && !includeSettings;
      sb.append("\u2502       \u2502   ").append(lastMenu ? "\u2514" : "\u251c").append("\u2500\u2500 confirm_exit.menu\n");
      if (includeSave) {
        sb.append("\u2502       \u2502   \u251c\u2500\u2500 load.menu\n");
        sb.append("\u2502       \u2502   ").append(includeSettings ? "\u251c" : "\u2514").append("\u2500\u2500 save.menu\n");
      }
      if (includeSettings) {
        sb.append("\u2502       \u2502   \u2514\u2500\u2500 settings.menu\n");
      }
      sb.append("\u2502       \u251c\u2500\u2500 layouts/\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 default.layout\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 submenu.layout\n");
      sb.append("\u2502       \u2502   \u251c\u2500\u2500 settings.layout\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 slots.layout\n");
      sb.append("\u2502       \u2514\u2500\u2500 styles/\n");
      sb.append("\u2502           \u251c\u2500\u2500 default.style\n");
      sb.append("\u2502           \u251c\u2500\u2500 submenu.style\n");
      sb.append("\u2502           \u251c\u2500\u2500 settings.style\n");
      sb.append("\u2502           \u2514\u2500\u2500 slot.style\n");
    } else if (blankMenus) {
      sb.append("\u2502   \u2514\u2500\u2500 menu/                    (blank \u2013 build from scratch)\n");
      sb.append("\u2502       \u251c\u2500\u2500 registry/\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 menu.registry    (empty)\n");
      sb.append("\u2502       \u251c\u2500\u2500 menus/               (add .menu files here)\n");
      sb.append("\u2502       \u251c\u2500\u2500 layouts/             (add .layout files here)\n");
      sb.append("\u2502       \u2514\u2500\u2500 styles/              (add .style files here)\n");
    }
    sb.append("\u251c\u2500\u2500 scripts/\n");
    sb.append("\u2502   \u251c\u2500\u2500 story/\n");
    sb.append("\u2502   \u2502   \u251c\u2500\u2500 prologue.vns\n");
    sb.append("\u2502   \u2502   \u251c\u2500\u2500 tutorial_hub.vns\n");
    sb.append("\u2502   \u2502   \u251c\u2500\u2500 branch_demo.vns\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 epilogue.vns\n");
    sb.append("\u2502   \u251c\u2500\u2500 tutorial/\n");
    if (includeTutorialPack) {
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 01_dialogue_basics.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 02_narration_and_pacing.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 03_expressions_and_characters.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 04_images_and_backgrounds.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 05_transitions_and_effects.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 06_audio_and_music.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 07_variables_and_conditions.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 08_character_movement.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 09_puppeteer_timeline.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 10_choices_and_menus.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 11_subroutines_and_flow.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 12_best_practices.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 13_camera_and_staging.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 14_localization_and_textkeys.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 15_ui_layout_and_theme.vns\n");
      sb.append("\u2502   \u2502   \u251c\u2500\u2500 16_testing_and_release.vns\n");
      sb.append("\u2502   \u2502   \u2514\u2500\u2500 17_vns_jes_integration.vns\n");
      sb.append("│   │   └── 18–30: plugins, systems, tools and production\n");
    } else {
      sb.append("\u2502   \u2502   \u2514\u2500\u2500 (empty until tutorial pack is added)\n");
    }
    sb.append("\u2502   \u251c\u2500\u2500 routes/\n");
    sb.append("\u2502   \u251c\u2500\u2500 scenes/\n");
    if (includeStarterStory || includeTutorialPack) {
      sb.append("\u2502   \u2502   \u2514\u2500\u2500 studio_tour.jes\n");
    }
    sb.append("\u2502   \u251c\u2500\u2500 definitions/\n");
    sb.append("\u2502   \u2502   \u2514\u2500\u2500 characters.vns\n");
    sb.append("\u2502   \u251c\u2500\u2500 common/\n");
    sb.append("\u2502   \u2514\u2500\u2500 system/\n");
    sb.append("\u251c\u2500\u2500 assets/\n");
    if (includeDemoAssets) {
      sb.append("\u2502   \u2514\u2500\u2500 demo/\n");
      sb.append("\u2502       \u251c\u2500\u2500 backgrounds/\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 field/\n");
      sb.append("\u2502       \u251c\u2500\u2500 characters/\n");
      sb.append("\u2502       \u2502   \u2514\u2500\u2500 lavender/\n");
      sb.append("\u2502       \u2514\u2500\u2500 audio/\n");
    } else {
      sb.append("\u2502   \u2514\u2500\u2500 (add art, audio, and fonts as needed)\n");
    }
    sb.append("\u251c\u2500\u2500 save/\n");
    if (shouldSetupGit()) {
      sb.append("\u251c\u2500\u2500 .gitignore\n");
    }
    sb.append("\u251c\u2500\u2500 README.md\n");
    sb.append("\u2514\u2500\u2500 jvn.project\n");

    return sb.toString();
  }

  private TreeItem<PreviewNode> buildStructurePreviewTree(String projectFolderName) {
    String preview = buildStructurePreviewText(projectFolderName);
    String[] lines = preview.split("\\R");
    TreeItem<PreviewNode> root = null;
    List<TreeItem<PreviewNode>> stack = new ArrayList<>();
    for (String line : lines) {
      if (line == null || line.isBlank()) continue;
      int branchIndex = line.indexOf("├── ");
      if (branchIndex < 0) branchIndex = line.indexOf("└── ");
      if (branchIndex < 0) {
        PreviewNode node = new PreviewNode(stripTrailingSlash(line.trim()), null, true, ProjectFileIcons.Kind.ROOT);
        root = new TreeItem<>(node);
        stack.clear();
        stack.add(root);
        continue;
      }

      int depth = (branchIndex / 4) + 1;
      while (stack.size() > depth) {
        stack.remove(stack.size() - 1);
      }
      if (stack.isEmpty()) continue;

      TreeItem<PreviewNode> item = new TreeItem<>(parsePreviewNode(line.substring(branchIndex + 4)));
      stack.get(stack.size() - 1).getChildren().add(item);
      stack.add(item);
    }

    if (root == null) {
      root = new TreeItem<>(new PreviewNode(projectFolderName, null, true, ProjectFileIcons.Kind.ROOT));
    }
    expandPreviewTree(root);
    return root;
  }

  private PreviewNode parsePreviewNode(String rawContent) {
    String content = rawContent == null ? "" : rawContent.stripTrailing();
    String name = content;
    String detail = null;
    int detailIndex = findDetailStart(content);
    if (detailIndex >= 0) {
      name = content.substring(0, detailIndex).stripTrailing();
      detail = content.substring(detailIndex).trim();
    }
    boolean directory = name.endsWith("/");
    String displayName = directory ? stripTrailingSlash(name) : name;
    ProjectFileIcons.Kind kind = resolvePreviewItemKind(displayName, directory, detail);
    return new PreviewNode(displayName, detail, directory, kind);
  }

  private int findDetailStart(String content) {
    if (content == null) return -1;
    for (int i = 0; i < content.length() - 2; i++) {
      if (content.charAt(i) == ' '
          && content.charAt(i + 1) == ' '
          && content.substring(i).trim().startsWith("(")) {
        return i;
      }
    }
    return -1;
  }

  private ProjectFileIcons.Kind resolvePreviewItemKind(String name, boolean directory, String detail) {
    return ProjectFileIcons.kindFor(name, directory, false);
  }

  private Region previewIcon(PreviewNode node) {
    return ProjectFileIcons.iconFor(node != null ? node.kind : ProjectFileIcons.Kind.DOCUMENT);
  }

  private void expandPreviewTree(TreeItem<PreviewNode> item) {
    if (item == null) return;
    item.setExpanded(true);
    for (TreeItem<PreviewNode> child : item.getChildren()) {
      expandPreviewTree(child);
    }
  }

  private String stripTrailingSlash(String value) {
    if (value == null || value.isBlank()) return value;
    return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
  }

  private long estimateBundledDemoAssetsKb() {
    File sourceRoot = resolveBundledDemoAssetsRoot();
    if (sourceRoot == null || !sourceRoot.isDirectory()) {
      // Fallback for packaged builds where source folders are not directly discoverable.
      return 20480;
    }
    long bytes = computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_BG_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_SPRITE_LAYERED_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_SPRITE_LEGACY_DIR))
        + computeDirectorySize(new File(sourceRoot, BUNDLED_DEMO_BGM_DIR));
    if (bytes <= 0) return 20480;
    return Math.max(1, (bytes + 1023) / 1024);
  }

  private long computeDirectorySize(File dir) {
    if (dir == null || !dir.exists()) return 0;
    if (dir.isFile()) return dir.length();
    File[] children = dir.listFiles();
    if (children == null) return 0;
    long total = 0;
    for (File child : children) {
      total += computeDirectorySize(child);
    }
    return total;
  }

  private boolean shouldStartBlankMenus() {
    return chkBlankMenus != null && chkBlankMenus.isSelected();
  }

  private boolean shouldCreateMenuPack() {
    if (shouldStartBlankMenus()) return false;
    return (chkTitleScreen != null && chkTitleScreen.isSelected())
        || (chkSaveSystem != null && chkSaveSystem.isSelected())
        || (chkSettingsMenu != null && chkSettingsMenu.isSelected());
  }

  private boolean shouldSetupGit() {
    return gitAvailable && chkGitInit != null && chkGitInit.isSelected();
  }

  private boolean shouldCreateInitialCommit() {
    return shouldSetupGit() && chkInitialCommit != null && chkInitialCommit.isSelected();
  }

  private void createProject() {
    String displayName = txtProjectName.getText().trim();
    if (displayName.isEmpty()) {
      showError("Please enter a project name.");
      return;
    }

    String folderName = resolveFolderName();
    if (folderName.isBlank()) {
      showError("Please enter a folder name.");
      return;
    }
    if (!isSafeFolderName(folderName)) {
      showError("Folder name can only use letters, numbers, dot, underscore, or hyphen.");
      return;
    }

    String locationRaw = txtLocation.getText().trim();
    if (locationRaw.isEmpty()) {
      showError("Please choose a project location.");
      return;
    }

    File location = new File(locationRaw);
    boolean createdBaseLocation = false;
    if (!location.exists()) {
      if (!location.mkdirs()) {
        showError("Failed to create base location: " + location.getAbsolutePath());
        return;
      }
      createdBaseLocation = true;
    }

    File projectDir = new File(location, folderName);
    if (projectDir.exists()) {
      showError("A project with this name already exists at the selected location.");
      return;
    }

    try {
      createProjectStructure(projectDir, displayName);
      createdProjectDir = projectDir;
      close();
    } catch (Exception ex) {
      cleanupFailedProjectCreation(projectDir, location, createdBaseLocation);
      showError("Failed to create project: " + ex.getMessage());
    }
  }

  private void createProjectStructure(File dir, String displayName) throws Exception {
    boolean includeMenuPack = shouldCreateMenuPack();
    boolean includeSave = chkSaveSystem.isSelected();
    boolean includeSettings = chkSettingsMenu.isSelected();
    boolean includeDemoAssets = chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected();
    boolean includeStarterStory = chkStarterStory != null && chkStarterStory.isSelected();
    boolean includeTutorialPack = chkTutorialPack != null && chkTutorialPack.isSelected();
    boolean gitRequested = shouldSetupGit();
    boolean gitCommitRequested = shouldCreateInitialCommit();
    boolean gitEnabled = false;
    boolean gitInitialCommit = false;

    if (!dir.exists() && !dir.mkdirs()) {
      throw new Exception("Failed to create project directory: " + dir.getAbsolutePath());
    }
    if (!dir.isDirectory()) {
      throw new Exception("Project path is not a directory: " + dir.getAbsolutePath());
    }
    createDirectories(dir, includeMenuPack);
    boolean useLayeredLavenderDemo = false;
    if (includeDemoAssets) {
      useLayeredLavenderDemo = copyBundledDemoAssets(dir);
    }

    createCharactersScript(dir, includeDemoAssets, useLayeredLavenderDemo);
    createStoryScripts(
        dir,
        displayName,
        includeStarterStory,
        includeTutorialPack,
        includeDemoAssets,
        useLayeredLavenderDemo
    );
    if (includeStarterStory || includeTutorialPack) {
      createJesStudioScene(dir, includeDemoAssets);
    }
    createStoryMap(dir, displayName, includeTutorialPack);

    createSettings(dir);
    createDialogueLayout(dir);
    createLocaleStub(dir);

    if (includeMenuPack) {
      createMenuTheme(dir, displayName);
      createMenuCustomizationScaffold(dir, displayName, includeSave, includeSettings);
    } else if (shouldStartBlankMenus()) {
      createBlankMenuScaffold(dir);
    }

    if (includeTutorialPack) configureTutorialPresentation(dir, includeMenuPack);

    if (gitRequested) {
      GitVcsService vcs = new GitVcsService();
      String commitMessage = "Initialize " + displayName + " project scaffold";
      try {
        vcs.bootstrapRepository(dir, gitCommitRequested, commitMessage);
        gitEnabled = true;
        gitInitialCommit = gitCommitRequested;
      } catch (GitVcsService.GitVcsException ex) {
        if (gitCommitRequested) {
          try {
            // Fallback: keep repo init even if commit fails due to missing user identity.
            vcs.bootstrapRepository(dir, false, commitMessage);
            gitEnabled = true;
            gitInitialCommit = false;
          } catch (GitVcsService.GitVcsException ignored) {
            // Non-fatal: project creation should never fail due to Git state.
          }
        }
      }
    }

    int[] resolution = getScaledResolution();
    createManifest(
        dir,
        displayName,
        resolution[0],
        resolution[1],
        includeMenuPack,
        includeSave,
        includeSettings,
        gitEnabled,
        gitInitialCommit
    );

    createReadme(
        dir,
        displayName,
        includeMenuPack,
        includeSave,
        includeSettings,
        includeDemoAssets,
        gitEnabled,
        gitInitialCommit
    );
  }

  private void configureTutorialPresentation(File dir, boolean menus) throws Exception {
    for (String name : List.of("lofi-music-2.mp3", "CaveatBrush-Regular.ttf", "CaveatBrush-OFL.txt", "CREDITS.txt")) {
      String path = "assets/tutorial/" + name;
      Path target = dir.toPath().resolve(path);
      Files.createDirectories(target.getParent());
      try (var input = getClass().getResourceAsStream("/com/jvn/editor/templates/new-project/" + path)) {
        if (input == null) throw new java.io.IOException("Missing tutorial asset: " + path);
        Files.copy(input, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
      }
    }
    // Each entry remains independently previewable; shared story variables keep the loop uninterrupted.
    try (var scripts = Files.walk(dir.toPath().resolve("scripts"))) {
      for (Path script : scripts.filter(p -> p.toString().endsWith(".vns")).toList()) {
        String source = Files.readString(script);
        source = source.replace("assets/demo/audio/03 - Definitely Our Town.mp3", "assets/tutorial/lofi-music-2.mp3");
        source = source.replace("@label start\n", "@label start\n[if !tutorial_music_started]\n[bgm \"assets/tutorial/lofi-music-2.mp3\"]\n[flag tutorial_music_started]\n[endif]\n");
        Files.writeString(script, source);
      }
    }
    Files.writeString(dir.toPath().resolve(DIALOGUE_LAYOUT_PATH), """

        # Lavender workshop: translucent black panel, clear white text, waist-up framing.
        textBoxColor=#000000B8
        textBoxOpacity=0.72
        nameBoxColor=#000000B8
        nameTextColor=#E3D2F4
        dialogueTextColor=#FFFFFF
        dialogueTextRightPadding=54
        characterHeightFactor=1.5
        characterBaselineY=1.5
        """, java.nio.file.StandardOpenOption.APPEND);
    double uiScale = getScaledResolution()[1] / 720.0;
    Files.writeString(dir.toPath().resolve(DIALOGUE_LAYOUT_PATH), String.format(Locale.ROOT, """
        textBoxHeight=0.22
        textBoxY=0.74
        dialogueTextFontSize=%d
        nameTextFontSize=%d
        nameBoxHeight=%d
        nameBoxYOffset=-%d
        nameBoxWidth=%d
        nameTextBaselineOffset=%d
        dialogueTextHorizontalPadding=%d
        dialogueTextTopPadding=%d
        choiceFontSize=%d
        choiceHeight=%d
        """, Math.max(18, (int) Math.round(24 * uiScale)), Math.max(14, (int) Math.round(18 * uiScale)),
        (int) Math.round(32 * uiScale), (int) Math.round(32 * uiScale), (int) Math.round(160 * uiScale),
        (int) Math.round(22 * uiScale), (int) Math.round(24 * uiScale), (int) Math.round(24 * uiScale),
        Math.max(15, (int) Math.round(20 * uiScale)), Math.max(32, (int) Math.round(44 * uiScale))),
        java.nio.file.StandardOpenOption.APPEND);
    if (!menus) return;
    Files.writeString(dir.toPath().resolve(MENU_THEME_PATH), """

        # Tutorial presentation: animated dock and still handwritten title.
        mainMenuPresentation=dock
        titleFontFamily=assets/tutorial/CaveatBrush-Regular.ttf
        titleFontSize=108
        titleColor=#27242d
        itemColor=#332d40
        itemSelectedColor=#64478b
        hintColor=#716779
        accentColor=#a88ac5
        bgm=assets/tutorial/lofi-music-2.mp3
        bgmVolume=0.55
        """, java.nio.file.StandardOpenOption.APPEND);
    Path main = dir.toPath().resolve(MENU_MAIN_PATH);
    String menu = Files.readString(main).replaceAll("(?m)^titleText=.*$", "titleText=JVN v0.8.0 Tutorial");
    menu = menu.replace("item.new_game.label=Start New Game", "item.new_game.label=Begin");
    menu = menu.replace("hintsText=Enter/Click: Select    Esc: Back", "hintsText=Arrow keys to explore  /  Enter or click to open");
    Files.writeString(main, menu + "\nsubtitleText=A white page. Thirty lessons. One curious Lavender.\n");
  }

  private void cleanupFailedProjectCreation(File projectDir, File baseLocation, boolean createdBaseLocation) {
    if (projectDir != null && projectDir.exists()) {
      try {
        deleteDirectoryRecursively(projectDir.toPath());
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        // Best-effort rollback. The original creation exception is shown to the user.
      }
    }
    if (createdBaseLocation && baseLocation != null) {
      try {
        if (baseLocation.isDirectory()) {
          String[] children = baseLocation.list();
          if (children != null && children.length == 0) {
            baseLocation.delete();
          }
        }
      } catch (Exception ignored) {
            // reason: non-critical operation; exception swallowed to prevent crash propagation
        // Non-fatal cleanup path.
      }
    }
  }

  private void deleteDirectoryRecursively(Path root) throws Exception {
    if (root == null || !Files.exists(root)) return;
    try (var walk = Files.walk(root)) {
      walk.sorted(Comparator.reverseOrder()).forEach(path -> {
        try {
          Files.deleteIfExists(path);
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof Exception cause) throw cause;
      throw ex;
    }
  }

  private void createDirectories(File dir, boolean includeMenuPack) throws Exception {
    // Config
    ensureDirectory(dir, "config/settings");
    ensureDirectory(dir, "config/story");
    ensureDirectory(dir, "config/ui");
    ensureDirectory(dir, "config/locales");
    ensureDirectory(dir, "config/puppeteer/clips");
    if (includeMenuPack || shouldStartBlankMenus()) {
      ensureDirectory(dir, "config/menu/registry");
      ensureDirectory(dir, "config/menu/menus");
      ensureDirectory(dir, "config/menu/layouts");
      ensureDirectory(dir, "config/menu/styles");
    }
    if (includeMenuPack) {
      ensureDirectory(dir, "config/menu/theme");
    }

    // Scripts
    ensureDirectory(dir, "scripts/story");
    ensureDirectory(dir, "scripts/tutorial");
    ensureDirectory(dir, "scripts/scenes");
    ensureDirectory(dir, "scripts/routes");
    ensureDirectory(dir, "scripts/definitions");
    ensureDirectory(dir, "scripts/common");
    ensureDirectory(dir, "scripts/system");

    // Assets
    ensureDirectory(dir, "assets");

    // Save location
    ensureDirectory(dir, "save");
  }

  private void createBlankMenuScaffold(File dir) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write("# Menu registry (blank project scaffold)\n");
      fw.write("# File format: Java properties (key=value)\n");
      fw.write("# This file registers which menu DSL files runtime should discover.\n");
      fw.write("#\n");
      fw.write("# defaultMenu: initial menu id opened by [mainmenu] and title flow.\n");
      fw.write("# menus: comma-separated menu screen ids (.menu files in config/menu/menus/).\n");
      fw.write("# layouts: comma-separated layout ids (.layout files in config/menu/layouts/).\n");
      fw.write("# styles: comma-separated style ids (.style files in config/menu/styles/).\n");
      fw.write("#\n");
      fw.write("# Example:\n");
      fw.write("# defaultMenu=main\n");
      fw.write("# menus=main,load,save,settings\n");
      fw.write("# layouts=default,submenu,settings,slots\n");
      fw.write("# styles=default,submenu,settings,slot\n");
    }
  }

  private void ensureDirectory(File root, String relativePath) throws Exception {
    File directory = new File(root, relativePath);
    if (directory.exists()) {
      if (!directory.isDirectory()) {
        throw new Exception("Expected directory but found file: " + directory.getAbsolutePath());
      }
      return;
    }
    if (!directory.mkdirs()) {
      throw new Exception("Failed to create directory: " + directory.getAbsolutePath());
    }
  }

  private boolean copyBundledDemoAssets(File projectRoot) throws Exception {
    if (copyBundledDemoAssetsFromResources(projectRoot)) return true;

    File sourceRoot = resolveBundledDemoAssetsRoot();
    if (sourceRoot == null || !sourceRoot.isDirectory()) return false;

    writeWhiteTutorialBackgrounds(projectRoot);
    boolean copiedLayeredLavender = false;
    File layeredSource = new File(sourceRoot, BUNDLED_DEMO_SPRITE_LAYERED_DIR);
    if (layeredSource.isDirectory()) {
      copyDirectoryContents(layeredSource, new File(projectRoot, "assets/demo/characters/lavender"));
      copiedLayeredLavender = true;
    }
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_SPRITE_LEGACY_DIR),
        new File(projectRoot, "assets/demo/characters/lavender")
    );
    copyDirectoryContents(
        new File(sourceRoot, BUNDLED_DEMO_BGM_DIR),
        new File(projectRoot, "assets/demo/audio")
    );
    return copiedLayeredLavender;
  }

  static void writeWhiteTutorialBackgrounds(File projectRoot) throws java.io.IOException {
    Path folder = projectRoot.toPath().resolve("assets/demo/backgrounds");
    Files.createDirectories(folder);
    var image = new java.awt.image.BufferedImage(1920, 1080, java.awt.image.BufferedImage.TYPE_INT_RGB);
    var graphics = image.createGraphics();
    try { graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, 1920, 1080); }
    finally { graphics.dispose(); }
    for (String name : List.of("game.png", "menu.png")) javax.imageio.ImageIO.write(image, "png", folder.resolve(name).toFile());
  }

  private boolean copyBundledDemoAssetsFromResources(File projectRoot) throws Exception {
    ClassLoader loader = Thread.currentThread().getContextClassLoader();
    if (loader.getResource(BUNDLED_DEMO_RESOURCE_ROOT + "demo_bg/game.png") == null) return false;

    for (String relative : BUNDLED_DEMO_RESOURCE_FILES) {
      String targetRelative;
      if (relative.startsWith("demo_bg/")) {
        targetRelative = "assets/demo/backgrounds/" + relative.substring("demo_bg/".length());
      } else if (relative.startsWith("Lavender_test_sprite/")) {
        targetRelative = "assets/demo/characters/lavender/"
            + relative.substring("Lavender_test_sprite/".length());
      } else if (relative.startsWith("demo_bgm/")) {
        targetRelative = "assets/demo/audio/" + relative.substring("demo_bgm/".length());
      } else {
        continue;
      }

      String resourcePath = BUNDLED_DEMO_RESOURCE_ROOT + relative;
      try (java.io.InputStream in = loader.getResourceAsStream(resourcePath)) {
        if (in == null) throw new IllegalStateException("Missing bundled demo asset: " + resourcePath);
        Path target = projectRoot.toPath().resolve(targetRelative);
        Files.createDirectories(target.getParent());
        Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
      }
    }
    return true;
  }

  private File resolveBundledDemoAssetsRoot() {
    File cwd = new File(System.getProperty("user.dir", ".")).getAbsoluteFile();
    File cursor = cwd;
    for (int i = 0; i < 6 && cursor != null; i++) {
      File candidate = new File(cursor, BUNDLED_DEMO_ASSETS_DIR);
      if (candidate.isDirectory()) return candidate;
      File legacyCandidate = new File(cursor, "demo-assets");
      if (legacyCandidate.isDirectory()) return legacyCandidate;
      File legacyBg = new File(cursor, BUNDLED_DEMO_BG_DIR);
      File layeredSprites = new File(cursor, BUNDLED_DEMO_SPRITE_LAYERED_DIR);
      File legacySprites = new File(cursor, BUNDLED_DEMO_SPRITE_LEGACY_DIR);
      if (legacyBg.isDirectory() && (layeredSprites.isDirectory() || legacySprites.isDirectory())) return cursor;
      cursor = cursor.getParentFile();
    }
    return null;
  }

  private void copyDirectoryContents(File source, File destination) throws Exception {
    if (source == null || !source.exists() || !source.isDirectory()) return;
    Path sourcePath = source.toPath();
    try (var stream = Files.walk(sourcePath)) {
      stream.forEach(path -> {
        try {
          Path relative = sourcePath.relativize(path);
          Path target = destination.toPath().resolve(relative);
          if (Files.isDirectory(path)) {
            Files.createDirectories(target);
          } else {
            Files.createDirectories(target.getParent());
            Files.copy(path, target, StandardCopyOption.REPLACE_EXISTING);
          }
        } catch (Exception ex) {
          throw new RuntimeException(ex);
        }
      });
    } catch (RuntimeException ex) {
      if (ex.getCause() instanceof Exception cause) throw cause;
      throw ex;
    }
  }

  private int[] parseResolution() {
    if (chkCustomResolution != null && chkCustomResolution.isSelected()) {
      int width = parseDimension(txtCustomWidth == null ? null : txtCustomWidth.getText(), 2560, "width");
      int height = parseDimension(txtCustomHeight == null ? null : txtCustomHeight.getText(), 1440, "height");
      return new int[] {width, height};
    }
    String raw = cmbResolution == null ? null : cmbResolution.getValue();
    if (raw == null || raw.isBlank()) return new int[] {1920, 1080};
    String[] first = raw.split(" ");
    String[] parts = first[0].split("x");
    if (parts.length != 2) return new int[] {1920, 1080};
    int width = parseDimension(parts[0], 1920, "width");
    int height = parseDimension(parts[1], 1080, "height");
    return new int[] {width, height};
  }

  private int[] getScaledResolution() {
    int[] resolution = parseResolution();
    double scaling = Screen.getPrimary().getOutputScaleX();

    int width = (int) (resolution[0] / scaling);
    int height = (int) (resolution[1] / scaling);

    return new int[] {width, height};
  }

  private int parseDimension(String raw, int fallback, String axis) {
    if (raw == null || raw.isBlank()) return fallback;
    try {
      int value = Integer.parseInt(raw.trim());
      if (value < 320) return 320;
      if (value > 8192) return 8192;
      return value;
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return fallback;
    }
  }

  private String formatAspectRatio(int width, int height) {
    if (width <= 0 || height <= 0) return "unknown";
    int gcd = greatestCommonDivisor(width, height);
    return (width / gcd) + ":" + (height / gcd);
  }

  private int greatestCommonDivisor(int a, int b) {
    int x = Math.abs(a);
    int y = Math.abs(b);
    while (y != 0) {
      int tmp = x % y;
      x = y;
      y = tmp;
    }
    return x == 0 ? 1 : x;
  }

  private void createManifest(File dir,
                              String displayName,
                              int width,
                              int height,
                              boolean includeMenuPack,
                              boolean includeSave,
                              boolean includeSettings,
                              boolean gitEnabled,
                              boolean gitInitialCommit) throws Exception {
    Properties manifest = new Properties();
    manifest.setProperty("name", displayName);
    manifest.setProperty("author", txtAuthor.getText().trim());
    manifest.setProperty("id", VnStoragePaths.resolveGameId(manifest, dir.toPath()));
    manifest.setProperty("type", "vn");
    manifest.setProperty("entryVns", ENTRY_SCRIPT_PATH);
    manifest.setProperty("entryLabel", "start");
    manifest.setProperty(StoryMapPaths.MANIFEST_KEY, STORY_MAP_PATH);
    manifest.setProperty("settingsFile", SETTINGS_PATH);
    manifest.setProperty("dialogueLayout", DIALOGUE_LAYOUT_PATH);
    if (includeMenuPack) {
      manifest.setProperty("menuTheme", MENU_THEME_PATH);
      manifest.setProperty("menuRegistry", MENU_REGISTRY_PATH);
      manifest.setProperty("menuDefaultLayout", MENU_LAYOUT_DEFAULT_PATH);
      manifest.setProperty("menuDefaultStyle", MENU_STYLE_DEFAULT_PATH);
    } else if (shouldStartBlankMenus()) {
      manifest.setProperty("menuRegistry", MENU_REGISTRY_PATH);
    }
    manifest.setProperty("feature.blankMenus", Boolean.toString(shouldStartBlankMenus()));
    manifest.setProperty("width", String.valueOf(width));
    manifest.setProperty("height", String.valueOf(height));
    manifest.setProperty("display.customResolution", Boolean.toString(chkCustomResolution != null && chkCustomResolution.isSelected()));
    manifest.setProperty("scaffold.template", resolveProjectTemplateFromSelections().name().toLowerCase(Locale.ROOT));
    manifest.setProperty("theme", cmbTheme == null || cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue());
    manifest.setProperty("runtime.ui", cmbRuntimeUi == null || cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue());
    manifest.setProperty("runtime.audio", cmbAudioBackend == null || cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue());
    manifest.setProperty("runtime.locale", cmbLocale == null || cmbLocale.getValue() == null ? "en" : cmbLocale.getValue());
    boolean starterStory = chkStarterStory != null && chkStarterStory.isSelected();
    boolean tutorialPack = chkTutorialPack != null && chkTutorialPack.isSelected();
    manifest.setProperty("feature.sampleContent", Boolean.toString(starterStory || tutorialPack));
    manifest.setProperty("feature.starterStory", Boolean.toString(starterStory));
    manifest.setProperty("feature.tutorialPack", Boolean.toString(tutorialPack));
    manifest.setProperty("feature.demoAssets", Boolean.toString(chkBundledDemoAssets != null && chkBundledDemoAssets.isSelected()));
    manifest.setProperty("feature.titleScreen", Boolean.toString(chkTitleScreen.isSelected() && includeMenuPack));
    manifest.setProperty("feature.menuProfiles", Boolean.toString(includeMenuPack));
    manifest.setProperty("feature.saveSystem", Boolean.toString(includeSave));
    manifest.setProperty("feature.settingsMenu", Boolean.toString(includeSettings));
    manifest.setProperty("feature.historyBacklog", Boolean.toString(chkHistoryBacklog.isSelected()));
    manifest.setProperty("vcs.git.enabled", Boolean.toString(gitEnabled));
    manifest.setProperty("vcs.git.initialCommit", Boolean.toString(gitInitialCommit));
    manifest.setProperty("createdBy", "jvn-editor-wizard");

    if (!txtDescription.getText().isBlank()) {
      manifest.setProperty("description", txtDescription.getText().trim());
    }

    try (FileOutputStream fos = new FileOutputStream(new File(dir, "jvn.project"))) {
      manifest.store(fos, "JVN Visual Novel Project");
    }
  }

  private void createCharactersScript(File dir, boolean includeDemoAssets, boolean useLayeredLavenderDemo) throws Exception {
    StringBuilder sb = new StringBuilder();
    sb.append("# Shared character declarations (import from story files)\n");
    sb.append("# Usage: @include ").append(CHARACTERS_INCLUDE_PATH).append("\n\n");
    sb.append("@character lavender \"Lavender\"\n");
    sb.append("@character narrator \"Narrator\"\n\n");

    if (includeDemoAssets && useLayeredLavenderDemo) {
      sb.append("@charlayer lavender base assets/demo/characters/lavender/base/lavender_test_sprite_base.png\n");
      sb.append("@charlayer lavender eyes_neutral assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_neutral.png\n");
      sb.append("@charlayer lavender eyes_half_closed assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_half_closed.png\n");
      sb.append("@charlayer lavender eyes_angry assets/demo/characters/lavender/eyes/lavender_test_sprite_eyes_angry.png\n");
      sb.append("@charlayer lavender mouth_neutral assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_neutral.png\n");
      sb.append("@charlayer lavender mouth_smile assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_smile.png\n");
      sb.append("@charlayer lavender mouth_happy assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_happy.png\n");
      sb.append("@charlayer lavender mouth_o assets/demo/characters/lavender/mouth/lavender_test_sprite_mouth_o.png\n\n");
      sb.append("@charpreset lavender neutral $base | $eyes_neutral | $mouth_neutral\n");
      sb.append("@charpreset lavender idle $base | $eyes_neutral | $mouth_neutral\n");
      sb.append("@charpreset lavender talking $base | $eyes_half_closed | $mouth_smile\n");
      sb.append("@charpreset lavender happy $base | $eyes_neutral | $mouth_happy\n");
      sb.append("@charpreset lavender emphasis $base | $eyes_angry | $mouth_o\n");
    } else {
      String spritePath = includeDemoAssets
          ? "assets/demo/characters/lavender/lavender_test_sprite.png"
          : "assets/characters/sprites/lavender.png";
      sb.append("# Replace sprite paths below with your project sprites if needed.\n");
      sb.append("@charimg lavender neutral ").append(spritePath).append("\n");
      sb.append("@charimg lavender idle ").append(spritePath).append("\n");
      sb.append("@charimg lavender talking ").append(spritePath).append("\n");
      sb.append("@charimg lavender happy ").append(spritePath).append("\n");
      sb.append("@charimg lavender emphasis ").append(spritePath).append("\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, CHARACTERS_SCRIPT_PATH))) {
      fw.write(sb.toString());
    }
  }

  private void createStoryScripts(
      File dir,
      String name,
      boolean includeStarterStory,
      boolean includeTutorialPack,
      boolean includeDemoAssets,
      boolean useLayeredLavenderDemo
  ) throws Exception {
    if (includeTutorialPack) {
      createTutorialHubScript(dir, name, includeDemoAssets);
      createTutorialTopicScripts(dir, name, includeDemoAssets, useLayeredLavenderDemo);
    } else {
      createBlankTutorialHubScript(dir, name, includeDemoAssets, useLayeredLavenderDemo);
    }

    if (includeStarterStory) {
      createSampleArcEntryAndBranchScripts(dir, name, includeDemoAssets);
    } else {
      createBlankArcEntryAndBranchScripts(dir, name, includeDemoAssets, useLayeredLavenderDemo);
    }
  }

  private void createBlankTutorialHubScript(
      File dir,
      String name,
      boolean includeDemoAssets,
      boolean useLayeredLavenderDemo
  ) throws Exception {
    java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    tokens.put("PROJECT_NAME", name);
    tokens.put("SCENARIO_PREFIX", sanitizeName(name).toLowerCase(Locale.ROOT));
    tokens.put("CHARACTERS_INCLUDE", CHARACTERS_INCLUDE_PATH);
    tokens.put("LAVENDER_EXPR", includeDemoAssets && useLayeredLavenderDemo ? "idle" : "neutral");
    tokens.put("TUTORIAL_TARGET", ARC_TUTORIAL_HUB);
    tokens.put("BRANCH_TARGET", ARC_BRANCH_DEMO);
    tokens.put("EPILOGUE_TARGET", ARC_EPILOGUE);
    tokens.put("STORY_TUTORIAL_SCRIPT_PATH", STORY_TUTORIAL_SCRIPT_PATH);
    tokens.put("STORY_BRANCH_SCRIPT_PATH", STORY_BRANCH_SCRIPT_PATH);
    tokens.put("STORY_EPILOGUE_SCRIPT_PATH", STORY_EPILOGUE_SCRIPT_PATH);
    writeScaffoldTemplateScript(
        dir,
        STORY_TUTORIAL_SCRIPT_PATH,
        "scripts/story/tutorial_hub_blank.vns",
        tokens
    );
  }

  private void createTutorialHubScript(File dir, String name, boolean includeDemoAssets) throws Exception {
    String scenarioPrefix = sanitizeName(name).toLowerCase(Locale.ROOT);
    String backgroundDecl = includeDemoAssets
        ? "@background white_stage assets/demo/backgrounds/game.png\n"
            + "@background white_stage_alt assets/demo/backgrounds/game.png\n\n"
        : "";
    String backgroundStart = includeDemoAssets ? "[bg white_stage]\n[transition fade 450]\n" : "";

    java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    tokens.put("PROJECT_NAME", name);
    tokens.put("SCENARIO_PREFIX", scenarioPrefix);
    tokens.put("CHARACTERS_INCLUDE", CHARACTERS_INCLUDE_PATH);
    tokens.put("BG_DECL", backgroundDecl);
    tokens.put("BG_START", backgroundStart);
    tokens.put("DIALOGUE_TARGET", ARC_T01_DIALOGUE);
    tokens.put("NARRATION_TARGET", ARC_T02_NARRATION);
    tokens.put("EXPRESSIONS_TARGET", ARC_T03_EXPRESSIONS);
    tokens.put("IMAGES_TARGET", ARC_T04_IMAGES);
    tokens.put("TRANSITIONS_TARGET", ARC_T05_TRANSITIONS);
    tokens.put("AUDIO_TARGET", ARC_T06_AUDIO);
    tokens.put("VARIABLES_TARGET", ARC_T07_VARIABLES);
    tokens.put("MOVEMENT_TARGET", ARC_T08_MOVEMENT);
    tokens.put("PUPPETEER_TARGET", ARC_T09_PUPPETEER);
    tokens.put("MENUS_TARGET", ARC_T10_MENUS);
    tokens.put("SUBROUTINES_TARGET", ARC_T11_SUBROUTINES);
    tokens.put("BEST_PRACTICES_TARGET", ARC_T12_BEST_PRACTICES);
    tokens.put("CAMERA_TARGET", ARC_T13_CAMERA);
    tokens.put("LOCALIZATION_TARGET", ARC_T14_LOCALIZATION);
    tokens.put("UI_LAYOUT_TARGET", ARC_T15_UI_LAYOUT);
    tokens.put("TESTING_RELEASE_TARGET", ARC_T16_TESTING_RELEASE);
    tokens.put("VNS_JES_TARGET", ARC_T17_VNS_JES);

    writeScaffoldTemplateScript(
        dir,
        STORY_TUTORIAL_SCRIPT_PATH,
        "scripts/story/tutorial_hub.vns",
        tokens
    );
  }

  private void createTutorialTopicScripts(
      File dir,
      String name,
      boolean includeDemoAssets,
      boolean useLayeredLavenderDemo
  ) throws Exception {
    String scenarioPrefix = sanitizeName(name).toLowerCase(Locale.ROOT);
    String hubTarget = ARC_TUTORIAL_HUB;
    String tutorialBgDecl = includeDemoAssets
        ? "@background white_stage assets/demo/backgrounds/game.png\n"
            + "@background white_stage_alt assets/demo/backgrounds/game.png\n\n"
        : "";
    String tutorialBgStart = includeDemoAssets ? "[bg white_stage]\n[transition fade 350]\n" : "";
    String tutorialBgCrossfade = includeDemoAssets ? "[transition crossfade 600 white_stage_alt]\n" : "";
    String tutorialBgmStart = includeDemoAssets ? "[bgm \"assets/demo/audio/03 - Definitely Our Town.mp3\"]\n" : "";
    String tutorialBgmFade = includeDemoAssets ? "[bgm_fadeout 1200]\n[wait 1300]\n" : "";
    String expressionHint = useLayeredLavenderDemo
        ? "Lavender: This project uses layered presets (@charlayer + @charpreset), so expressions are composited from parts."
        : "Lavender: This project uses simple @charimg declarations. You can switch to layered presets later.";

    java.util.Map<String, String> baseTokens = new java.util.LinkedHashMap<>();
    baseTokens.put("SCENARIO_PREFIX", scenarioPrefix);
    baseTokens.put("CHARACTERS_INCLUDE", CHARACTERS_INCLUDE_PATH);
    baseTokens.put("BG_DECL", tutorialBgDecl);
    baseTokens.put("BG_START", tutorialBgStart);
    baseTokens.put("HUB_TARGET", hubTarget);
    baseTokens.put("CHARACTERS_SCRIPT_PATH", CHARACTERS_SCRIPT_PATH);
    baseTokens.put("BG_CROSSFADE", tutorialBgCrossfade);
    baseTokens.put("BGM_START", tutorialBgmStart);
    baseTokens.put("BGM_FADE", tutorialBgmFade);
    baseTokens.put("EXPRESSION_HINT", expressionHint);
    baseTokens.put("JES_SCENE_PATH", JES_STUDIO_SCENE_PATH);

    for (TutorialCatalog.Lesson lesson : TutorialCatalog.LESSONS) {
      writeScaffoldTemplateScript(dir, lesson.path(), lesson.path(), baseTokens);
    }
    for (String path : List.of("config/facets/tutorial_card.facet", "config/phone/phone.properties",
        "scripts/tutorial/README.md")) {
      Files.createDirectories(dir.toPath().resolve(path).getParent());
      writeScaffoldTemplateScript(dir, path, path, baseTokens);
    }
  }

  private void createJesStudioScene(File dir, boolean includeDemoAssets) throws Exception {
    java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    tokens.put(
        "JES_BACKGROUND_ENTITY",
        includeDemoAssets
            ? "  entity \"background\" {\n"
                + "    component Sprite2D {\n"
                + "      image: \"assets/demo/backgrounds/game.png\"\n"
                + "      x: 0 y: 0 w: 1920 h: 1080\n"
                + "    }\n"
                + "  }\n\n"
            : "  entity \"background\" {\n"
                + "    component Panel2D { x: 0 y: 0 w: 1920 h: 1080 fill: rgb(1, 1, 1, 1) }\n"
                + "  }\n\n");
    writeScaffoldTemplateScript(
        dir,
        JES_STUDIO_SCENE_PATH,
        "scripts/scenes/studio_tour.jes",
        tokens
    );
  }

  private void writeScaffoldScript(File dir, String relativePath, String content) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, relativePath))) {
      fw.write(content);
    }
  }

  private void writeScaffoldTemplateScript(
      File dir,
      String relativePath,
      String templateRelativePath,
      java.util.Map<String, String> tokens
  ) throws Exception {
    String template = loadScaffoldTemplate(templateRelativePath);
    String rendered = applyTemplateTokens(template, tokens);
    writeScaffoldScript(dir, relativePath, rendered);
  }

  private String loadScaffoldTemplate(String templateRelativePath) throws Exception {
    String normalized = templateRelativePath == null ? "" : templateRelativePath.replace('\\', '/');
    if (normalized.startsWith("/")) normalized = normalized.substring(1);
    String resourcePath = "com/jvn/editor/templates/new-project/" + normalized;
    try (java.io.InputStream in = Thread.currentThread().getContextClassLoader().getResourceAsStream(resourcePath)) {
      if (in == null) {
        throw new IllegalStateException("Missing scaffold template resource: " + resourcePath);
      }
      return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
    }
  }

  private String applyTemplateTokens(String template, java.util.Map<String, String> tokens) {
    if (template == null || template.isEmpty() || tokens == null || tokens.isEmpty()) return template;
    String rendered = template;
    for (var entry : tokens.entrySet()) {
      String key = entry.getKey() == null ? "" : entry.getKey();
      String value = entry.getValue() == null ? "" : entry.getValue();
      rendered = rendered.replace("{{" + key + "}}", value);
    }
    return rendered;
  }

  private void createBlankArcEntryAndBranchScripts(
      File dir,
      String name,
      boolean includeDemoAssets,
      boolean useLayeredLavenderDemo
  ) throws Exception {
    String lavenderExpr = includeDemoAssets && useLayeredLavenderDemo ? "idle" : "neutral";
    String scenarioPrefix = sanitizeName(name).toLowerCase(Locale.ROOT);
    java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    tokens.put("PROJECT_NAME", name);
    tokens.put("SCENARIO_PREFIX", scenarioPrefix);
    tokens.put("CHARACTERS_INCLUDE", CHARACTERS_INCLUDE_PATH);
    tokens.put("LAVENDER_EXPR", lavenderExpr);
    tokens.put("TUTORIAL_TARGET", ARC_TUTORIAL_HUB);
    tokens.put("BRANCH_TARGET", ARC_BRANCH_DEMO);
    tokens.put("EPILOGUE_TARGET", ARC_EPILOGUE);
    tokens.put("STORY_TUTORIAL_SCRIPT_PATH", STORY_TUTORIAL_SCRIPT_PATH);
    tokens.put("STORY_BRANCH_SCRIPT_PATH", STORY_BRANCH_SCRIPT_PATH);
    tokens.put("STORY_EPILOGUE_SCRIPT_PATH", STORY_EPILOGUE_SCRIPT_PATH);

    writeScaffoldTemplateScript(dir, ENTRY_SCRIPT_PATH, "scripts/story/prologue_blank.vns", tokens);
    if (!new File(dir, STORY_TUTORIAL_SCRIPT_PATH).isFile())
      writeScaffoldTemplateScript(dir, STORY_TUTORIAL_SCRIPT_PATH, "scripts/story/tutorial_hub_blank.vns", tokens);
    writeScaffoldTemplateScript(dir, STORY_BRANCH_SCRIPT_PATH, "scripts/story/branch_demo_blank.vns", tokens);
    writeScaffoldTemplateScript(dir, STORY_EPILOGUE_SCRIPT_PATH, "scripts/story/epilogue_blank.vns", tokens);
  }

  private void createSampleArcEntryAndBranchScripts(File dir, String name, boolean includeDemoAssets) throws Exception {
    String scenarioId = sanitizeName(name).toLowerCase(Locale.ROOT);
    String tutorialTarget = ARC_TUTORIAL_HUB;
    String branchTarget = ARC_BRANCH_DEMO;
    String epilogueTarget = ARC_EPILOGUE;
    String lavenderExpr = "idle";
    String backgroundDecl = includeDemoAssets ? "@background white_stage assets/demo/backgrounds/game.png\n\n" : "";
    String backgroundStart = includeDemoAssets ? "[bg white_stage]\n" : "";
    String backgroundTransition = includeDemoAssets ? "[transition fade 400]\n" : "";
    java.util.Map<String, String> tokens = new java.util.LinkedHashMap<>();
    tokens.put("PROJECT_NAME", name);
    tokens.put("SCENARIO_PREFIX", scenarioId);
    tokens.put("CHARACTERS_INCLUDE", CHARACTERS_INCLUDE_PATH);
    tokens.put("LAVENDER_EXPR", lavenderExpr);
    tokens.put("TUTORIAL_TARGET", tutorialTarget);
    tokens.put("BRANCH_TARGET", branchTarget);
    tokens.put("EPILOGUE_TARGET", epilogueTarget);
    tokens.put("STORY_BRANCH_SCRIPT_PATH", STORY_BRANCH_SCRIPT_PATH);
    tokens.put("STORY_EPILOGUE_SCRIPT_PATH", STORY_EPILOGUE_SCRIPT_PATH);
    tokens.put("STORY_MAP_PATH", STORY_MAP_PATH);
    tokens.put("BG_DECL", backgroundDecl);
    tokens.put("BG_START", backgroundStart);
    tokens.put("BG_TRANSITION", backgroundTransition);
    tokens.put("JES_SCENE_PATH", JES_STUDIO_SCENE_PATH);

    writeScaffoldTemplateScript(dir, ENTRY_SCRIPT_PATH, "scripts/story/prologue_sample.vns", tokens);
    writeScaffoldTemplateScript(dir, STORY_BRANCH_SCRIPT_PATH, "scripts/story/branch_demo_sample.vns", tokens);
    writeScaffoldTemplateScript(dir, STORY_EPILOGUE_SCRIPT_PATH, "scripts/story/epilogue_sample.vns", tokens);
  }

  private void createStoryMap(File dir, String displayName, boolean includeTutorialPack) throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, STORY_MAP_PATH))) {
      fw.write("# Story Map for " + displayName + "\n");
      fw.write("# Author: " + txtAuthor.getText().trim() + "\n");
      fw.write("# Starter arc workflow: prologue entry + route split + epilogue merge.\n\n");

      fw.write("arc \"" + ARC_PROLOGUE + "\" script \"" + ENTRY_SCRIPT_PATH + "\" entry \"start\" cluster \"Main\" priority 10 color \"#84c7ff\" tags \"entry,main\" at 40,120\n");
      fw.write("arc \"" + ARC_TUTORIAL_HUB + "\" script \"" + STORY_TUTORIAL_SCRIPT_PATH + "\" entry \"start\" cluster \"Main\" priority 8 color \"#93ddaa\" tags \"tutorial,main\" at 380,40\n");
      fw.write("arc \"" + ARC_BRANCH_DEMO + "\" script \"" + STORY_BRANCH_SCRIPT_PATH + "\" entry \"start\" cluster \"Routes\" priority 7 color \"#f3b27a\" tags \"branch,route\" at 380,220\n");
      fw.write("arc \"" + ARC_EPILOGUE + "\" script \"" + STORY_EPILOGUE_SCRIPT_PATH + "\" entry \"start\" cluster \"Main\" priority 9 color \"#d6a8ee\" tags \"ending,main\" at 760,120\n\n");

      if (includeTutorialPack) {
        int index = 0;
        for (TutorialCatalog.Lesson lesson : TutorialCatalog.LESSONS) {
          int x = 760 + (index % 5) * 240, y = -180 + (index / 5) * 200;
          fw.write("arc " + lesson.arc() + " script \"" + lesson.path()
              + "\" entry \"start\" cluster \"Tutorial\" priority 6 color \"#b5a0d6\" tags \"tutorial\" at " + x + "," + y + "\n");
          index++;
        }
      }

      fw.write("link " + ARC_PROLOGUE + ":route_tutorial -> " + ARC_TUTORIAL_HUB + ":start\n");
      fw.write("link " + ARC_PROLOGUE + ":route_branch -> " + ARC_BRANCH_DEMO + ":start\n");
      fw.write("link " + ARC_BRANCH_DEMO + ":end -> " + ARC_EPILOGUE + ":start\n");

      if (includeTutorialPack) {
        for (TutorialCatalog.Lesson lesson : TutorialCatalog.LESSONS) {
          fw.write("link " + ARC_TUTORIAL_HUB + ":" + lesson.hubLabel() + " -> " + lesson.arc() + ":start\n");
        }
      }
    }
  }

  private void createSettings(File dir) throws Exception {
    try (FileOutputStream fos = new FileOutputStream(new File(dir, SETTINGS_PATH))) {
      Properties sp = new Properties();
      int textSpeed = spTextSpeed == null ? 35 : spTextSpeed.getValue();
      int autoDelay = spAutoDelay == null ? 2000 : spAutoDelay.getValue();
      double bgm = spBgmVolume == null ? 0.7 : spBgmVolume.getValue();
      double sfx = spSfxVolume == null ? 0.8 : spSfxVolume.getValue();
      double voice = spVoiceVolume == null ? 1.0 : spVoiceVolume.getValue();
      boolean skipUnread = chkSkipUnreadDefault != null && chkSkipUnreadDefault.isSelected();
      boolean skipAfterChoices = chkSkipAfterChoicesDefault != null && chkSkipAfterChoicesDefault.isSelected();
      int physicsStep = spPhysicsFixedStep == null ? 0 : spPhysicsFixedStep.getValue();
      int physicsSubsteps = spPhysicsMaxSubsteps == null ? 4 : spPhysicsMaxSubsteps.getValue();
      double physicsFriction = spPhysicsFriction == null ? 0.2 : spPhysicsFriction.getValue();
      String inputProfile = txtInputProfilePath == null || txtInputProfilePath.getText().isBlank()
          ? System.getProperty("user.home") + "/.jvn/input-bindings.properties"
          : txtInputProfilePath.getText().trim();

      // Legacy project settings keys used by the current settings editor.
      sp.setProperty("textSpeed", Integer.toString(textSpeed));
      sp.setProperty("bgm", Double.toString(bgm));
      sp.setProperty("sfx", Double.toString(sfx));
      sp.setProperty("voice", Double.toString(voice));
      sp.setProperty("autoPlayDelay", Integer.toString(autoDelay));
      sp.setProperty("skipUnread", Boolean.toString(skipUnread));
      sp.setProperty("skipAfterChoices", Boolean.toString(skipAfterChoices));
      sp.setProperty("clickRevealBeforeAdvance", Boolean.toString(true));

      // Runtime settings keys used by VnSettingsStore.
      sp.setProperty("text_speed", Integer.toString(textSpeed));
      sp.setProperty("bgm_volume", Double.toString(bgm));
      sp.setProperty("sfx_volume", Double.toString(sfx));
      sp.setProperty("voice_volume", Double.toString(voice));
      sp.setProperty("auto_play_delay", Integer.toString(autoDelay));
      sp.setProperty("skip_unread_text", Boolean.toString(skipUnread));
      sp.setProperty("skip_after_choices", Boolean.toString(skipAfterChoices));
      sp.setProperty("click_reveal_before_advance", Boolean.toString(true));
      sp.setProperty("physics_fixed_step_ms", Integer.toString(physicsStep));
      sp.setProperty("physics_max_substeps", Integer.toString(physicsSubsteps));
      sp.setProperty("physics_default_friction", Double.toString(physicsFriction));
      sp.setProperty("input_profile_path", inputProfile);

      // Display resolution (scaled for screen DPI).
      int[] scaledRes = getScaledResolution();
      sp.setProperty("display_width", Integer.toString(scaledRes[0]));
      sp.setProperty("display_height", Integer.toString(scaledRes[1]));

      // Project module hints.
      sp.setProperty("historyBacklogEnabled", Boolean.toString(chkHistoryBacklog.isSelected()));
      sp.setProperty("saveProfilesEnabled", Boolean.toString(chkSaveSystem.isSelected()));
      sp.setProperty("settingsProfileEnabled", Boolean.toString(chkSettingsMenu.isSelected()));
      sp.store(fos, "VN Settings - Edit in Settings panel");
    }
  }

  private void createDialogueLayout(File dir) throws Exception {
    int[] res = getScaledResolution();
    double scale = res[1] / 1080.0;
    String template = "Monochrome Sketch".equals(cmbTheme.getValue())
        ? LayoutDslTemplates.minimalMonochromeDialogueLayoutTemplate()
        : LayoutDslTemplates.defaultDialogueLayoutTemplate();
    if (Math.abs(scale - 1.0) > 0.01) {
      template = scaleDialogueLayoutPixels(template, scale, res[0], res[1]);
    }
    try (FileWriter fw = new FileWriter(new File(dir, DIALOGUE_LAYOUT_PATH))) {
      fw.write(template.replace("\n", System.lineSeparator()));
    }
  }

  private static String scaleDialogueLayoutPixels(String template, double scale, int width, int height) {
    String result = template;
    result = "# Target resolution: " + width + "x" + height + "\n" + result;
    // Scale pixel-valued layout keys (not fractional 0..1 keys).
    result = scaleIntKey(result, "textBoxPadding", scale);
    result = scaleIntKey(result, "nameBoxXOffset", scale);
    result = scaleIntKey(result, "nameBoxYOffset", scale);
    result = scaleIntKey(result, "nameBoxWidth", scale);
    result = scaleIntKey(result, "nameBoxHeight", scale);
    result = scaleIntKey(result, "nameTextXOffset", scale);
    result = scaleIntKey(result, "nameTextBaselineOffset", scale);
    result = scaleIntKey(result, "dialogueTextHorizontalPadding", scale);
    result = scaleIntKey(result, "dialogueTextTopPadding", scale);
    result = scaleIntKey(result, "dialogueTextRightPadding", scale);
    result = scaleIntKey(result, "dialogueTextBottomPadding", scale);
    result = scaleIntKey(result, "choiceHeight", scale);
    result = scaleIntKey(result, "choiceGap", scale);
    result = scaleIntKey(result, "choiceTextXPadding", scale);
    result = scaleIntKey(result, "nameTextFontSize", scale);
    result = scaleIntKey(result, "dialogueTextFontSize", scale);
    result = scaleIntKey(result, "choiceFontSize", scale);
    result = scaleIntKey(result, "choiceCornerRadius", scale);
    return result;
  }

  private static String scaleIntKey(String text, String key, double scale) {
    String pattern = key + "=";
    int idx = text.indexOf(pattern);
    if (idx < 0) return text;
    int valStart = idx + pattern.length();
    int valEnd = valStart;
    boolean negative = valEnd < text.length() && text.charAt(valEnd) == '-';
    if (negative) valEnd++;
    while (valEnd < text.length() && Character.isDigit(text.charAt(valEnd))) valEnd++;
    if (valEnd == valStart || (negative && valEnd == valStart + 1)) return text;
    try {
      int original = Integer.parseInt(text.substring(valStart, valEnd));
      int scaled = (int) Math.round(original * scale);
      return text.substring(0, valStart) + scaled + text.substring(valEnd);
    } catch (NumberFormatException ignored) {
// reason: malformed numeric text input; caller uses fallback value
      return text;
    }
  }

  private void createLocaleStub(File dir) throws Exception {
    String locale = cmbLocale == null || cmbLocale.getValue() == null ? "en" : cmbLocale.getValue();
    boolean hasSample = (chkStarterStory != null && chkStarterStory.isSelected())
        || (chkTutorialPack != null && chkTutorialPack.isSelected());
    boolean hasMenus = shouldCreateMenuPack();
    String fileName = "config/locales/" + locale + ".properties";
    try (FileWriter fw = new FileWriter(new File(dir, fileName))) {
      fw.write("# Locale strings (" + locale + ")\n");
      fw.write("# The runtime resolves text keys via VnTextFormatter.\n");
      fw.write("# Supported placeholders: {name}, {0}, {1}, etc.\n");
      fw.write("#\n");
      fw.write("# --- Menu labels (used in .menu screen titleText / hintsText) ---\n");
      if (hasMenus) {
        fw.write("menu.main.title=Main Menu\n");
        fw.write("menu.main.hints=Arrow keys to navigate, Enter to select\n");
        fw.write("menu.settings.title=Settings\n");
      } else {
        fw.write("# menu.main.title=Main Menu\n");
        fw.write("# menu.main.hints=Arrow keys to navigate, Enter to select\n");
        fw.write("# menu.settings.title=Settings\n");
      }
      fw.write("#\n");
      fw.write("# --- Settings item labels ({value} expands to current setting) ---\n");
      fw.write("# settings.bgm=BGM Volume: {value}\n");
      fw.write("# settings.sfx=SFX Volume: {value}\n");
      fw.write("# settings.fullscreen=Fullscreen: {value}\n");
      fw.write("#\n");
      fw.write("# --- Dialogue / story text ---\n");
      if (hasSample) {
        fw.write("greeting=Hello, {name}!\n");
        fw.write("farewell=Goodbye, {name}. Until we meet again.\n");
      } else {
        fw.write("# greeting=Hello, {name}!\n");
        fw.write("# farewell=Goodbye, {name}. Until we meet again.\n");
      }
    }
  }

  private void createMenuTheme(File dir, String name) throws Exception {
    String theme = cmbTheme.getValue();
    Properties tp = new Properties();

    switch (theme) {
      case "Monochrome Sketch" -> {
        tp.setProperty("backgroundColor", "#FAFAFA");
        tp.setProperty("titleColor", "#111111");
        tp.setProperty("itemColor", "#333333");
        tp.setProperty("itemSelectedColor", "#000000");
        tp.setProperty("itemHoverColor", "#555555");
        tp.setProperty("hintColor", "#666666");
        tp.setProperty("accentColor", "#111111");
      }
      case "Light Clean" -> {
        tp.setProperty("backgroundColor", "#EFF3F9");
        tp.setProperty("titleColor", "#1A2844");
        tp.setProperty("itemColor", "#3A4D6E");
        tp.setProperty("itemSelectedColor", "#2264E0");
        tp.setProperty("itemHoverColor", "#4A6890");
        tp.setProperty("hintColor", "#6878A0");
        tp.setProperty("accentColor", "#2264E0");
      }
      case "Retro Game" -> {
        tp.setProperty("backgroundColor", "#080808");
        tp.setProperty("titleColor", "#60E848");
        tp.setProperty("itemColor", "#50C840");
        tp.setProperty("itemSelectedColor", "#F8D850");
        tp.setProperty("itemHoverColor", "#78E868");
        tp.setProperty("hintColor", "#78B868");
        tp.setProperty("accentColor", "#F8D850");
      }
      case "Nature Green" -> {
        tp.setProperty("backgroundColor", "#0E1E14");
        tp.setProperty("titleColor", "#C8F0D0");
        tp.setProperty("itemColor", "#98D0A4");
        tp.setProperty("itemSelectedColor", "#48D878");
        tp.setProperty("itemHoverColor", "#B0E8B8");
        tp.setProperty("hintColor", "#78B888");
        tp.setProperty("accentColor", "#48D878");
      }
      default -> {
        tp.setProperty("backgroundColor", "#060D1A");
        tp.setProperty("titleColor", "#EEF4FF");
        tp.setProperty("itemColor", "#C8D6EC");
        tp.setProperty("itemSelectedColor", "#FFDFA0");
        tp.setProperty("itemHoverColor", "#E4EEFF");
        tp.setProperty("hintColor", "#8898B8");
        tp.setProperty("accentColor", "#78B8F0");
      }
    }

    tp.setProperty("titleFontFamily", "SansSerif");
    tp.setProperty("titleFontWeight", "BOLD");
    tp.setProperty("titleFontSize", "48");
    tp.setProperty("itemFontFamily", "SansSerif");
    tp.setProperty("itemFontWeight", "SEMI_BOLD");
    tp.setProperty("itemFontSize", "26");
    tp.setProperty("hintFontFamily", "SansSerif");
    tp.setProperty("hintFontWeight", "NORMAL");
    tp.setProperty("hintFontSize", "15");
    tp.setProperty("titleY", "0.16");
    tp.setProperty("listYStart", "0.38");
    tp.setProperty("lineHeight", "62");
    tp.setProperty("itemPrefix", "");
    tp.setProperty("itemSelectedPrefix", "▸ ");
    tp.setProperty("hintsText", "Enter/Click: Select    Esc: Back");
    tp.setProperty("titleText", name);
    tp.setProperty("backgroundImage", DEFAULT_MENU_BG_ASSET_PATH);

    try (FileOutputStream fos = new FileOutputStream(new File(dir, MENU_THEME_PATH))) {
      tp.store(fos, "Menu Theme for " + name + " - " + theme);
    }
  }

  private void createMenuCustomizationScaffold(File dir, String name, boolean includeSave, boolean includeSettings)
      throws Exception {

    List<String> menus = new ArrayList<>();
    menus.add("main");
    menus.add("extras");
    menus.add("credits");
    menus.add("confirm_exit");
    if (includeSave) {
      menus.add("load");
      menus.add("save");
    }
    if (includeSettings) {
      menus.add("settings");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_REGISTRY_PATH))) {
      fw.write(LayoutDslTemplates.menuRegistryTemplate(
          "main", String.join(",", menus), "default,submenu,settings,slots", "default,submenu,settings,slot"));
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_DEFAULT_PATH))) {
      fw.write("Monochrome Sketch".equals(cmbTheme.getValue())
          ? LayoutDslTemplates.minimalMonochromeMenuLayoutTemplate()
          : LayoutDslTemplates.defaultMenuLayoutTemplate(null));
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_SUBMENU_PATH))) {
      fw.write(LayoutDslTemplates.submenuLayoutTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_SETTINGS_PATH))) {
      fw.write(LayoutDslTemplates.settingsLayoutTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_LAYOUT_SLOTS_PATH))) {
      fw.write(LayoutDslTemplates.slotsLayoutTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_DEFAULT_PATH))) {
      fw.write("Monochrome Sketch".equals(cmbTheme.getValue())
          ? LayoutDslTemplates.minimalMonochromeMenuStyleTemplate(DEFAULT_MENU_BG_ASSET_PATH)
          : LayoutDslTemplates.defaultMenuStyleFullTemplate(DEFAULT_MENU_BG_ASSET_PATH));
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_SUBMENU_PATH))) {
      fw.write(LayoutDslTemplates.submenuStyleTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_SETTINGS_PATH))) {
      fw.write(LayoutDslTemplates.settingsStyleTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_STYLE_SLOT_PATH))) {
      fw.write(LayoutDslTemplates.slotStyleTemplate());
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_MAIN_PATH))) {
      fw.write("# Main menu screen definition (redesigned)\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("#\n");
      fw.write("# Core keys:\n");
      fw.write("# titleText: menu title line.\n");
      fw.write("# hintsText: helper controls line shown near footer.\n");
      fw.write("# layout: layout id from config/menu/layouts/*.layout.\n");
      fw.write("# defaultItemStyle: style id from config/menu/styles/*.style.\n");
      fw.write("# wrapSelection: true loops keyboard selection from end->start.\n");
      fw.write("# items: ordered list of item ids rendered by this screen.\n");
      fw.write("#\n");
      fw.write("# Per-item keys:\n");
      fw.write("# item.<id>.label, item.<id>.action, item.<id>.target, item.<id>.style,\n");
      fw.write("# item.<id>.enabled, item.<id>.boundsX/Y/Width/Height (optional custom hit box).\n");
      fw.write("titleText=" + name + "\n");
      fw.write("hintsText=Enter/Click: Select    Esc: Back\n");
      fw.write("layout=default\n");
      fw.write("defaultItemStyle=default\n");
      fw.write("wrapSelection=true\n");

      List<String> items = new ArrayList<>();
      items.add("new_game");
      if (includeSave) items.add("continue");
      if (includeSettings) items.add("settings");
      items.add("extras");
      items.add("quit");
      fw.write("items=" + String.join(",", items) + "\n");

      fw.write("item.new_game.label=Start New Game\n");
      fw.write("item.new_game.action=new_game\n");
      if (includeSave) {
        fw.write("item.continue.label=Continue\n");
        fw.write("item.continue.action=load_menu\n");
      }
      if (includeSettings) {
        fw.write("item.settings.label=Settings\n");
        fw.write("item.settings.action=settings_menu\n");
      }
      fw.write("item.extras.label=Extras\n");
      fw.write("item.extras.action=open_menu\n");
      fw.write("item.extras.target=extras\n");
      fw.write("item.quit.label=Quit\n");
      fw.write("item.quit.action=open_menu\n");
      fw.write("item.quit.target=confirm_exit\n");

      double y = 0.34;
      for (String itemId : items) {
        writeCardBounds(fw, itemId, 0.28, y, 0.44, 0.072);
        y += 0.082;
      }
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_EXTRAS_PATH))) {
      fw.write("# Extras submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Uses submenu layout/style and forwards to nested screens.\n");
      fw.write("# Demonstrates disabled item rows and open_menu transitions.\n");
      fw.write("titleText=Extras\n");
      fw.write("hintsText=Enter/Click: Select    Esc: Back\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=music_room,credits,back\n");
      fw.write("item.music_room.label=Music Room (Soon)\n");
      fw.write("item.music_room.enabled=false\n");
      fw.write("item.music_room.action=noop\n");
      fw.write("item.credits.label=Credits\n");
      fw.write("item.credits.action=open_menu\n");
      fw.write("item.credits.target=credits\n");
      fw.write("item.back.label=Return to Main Menu\n");
      fw.write("item.back.action=main_menu\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_CREDITS_PATH))) {
      fw.write("# Credits submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Simple informational menu: mostly disabled text rows + one back action.\n");
      fw.write("titleText=Credits\n");
      fw.write("hintsText=Esc: Back\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=line_engine,line_editor,line_thanks,back\n");
      fw.write("item.line_engine.label=JVN Engine Team\n");
      fw.write("item.line_engine.enabled=false\n");
      fw.write("item.line_engine.action=noop\n");
      fw.write("item.line_editor.label=Runtime, Editor, and VNS by JVN contributors\n");
      fw.write("item.line_editor.enabled=false\n");
      fw.write("item.line_editor.action=noop\n");
      fw.write("item.line_thanks.label=Thanks for building with JVN.\n");
      fw.write("item.line_thanks.enabled=false\n");
      fw.write("item.line_thanks.action=noop\n");
      fw.write("item.back.label=Back\n");
      fw.write("item.back.style=slot\n");
      fw.write("item.back.action=open_menu\n");
      fw.write("item.back.target=extras\n");
    }

    try (FileWriter fw = new FileWriter(new File(dir, MENU_CONFIRM_EXIT_PATH))) {
      fw.write("# Quit confirmation submenu\n");
      fw.write("# Text-first workflow: edit item/action wiring here, save, run runtime.\n");
      fw.write("# Shows a disabled prompt row with two actionable choices.\n");
      fw.write("# item.<id>.style overrides row style when needed.\n");
      fw.write("titleText=Exit Game\n");
      fw.write("hintsText=Enter: Confirm    Esc: Cancel\n");
      fw.write("layout=submenu\n");
      fw.write("defaultItemStyle=submenu\n");
      fw.write("wrapSelection=true\n");
      fw.write("items=prompt,quit_yes,quit_no\n");
      fw.write("item.prompt.label=Leave this session?\n");
      fw.write("item.prompt.enabled=false\n");
      fw.write("item.prompt.action=noop\n");
      fw.write("item.quit_yes.label=Yes, Quit\n");
      fw.write("item.quit_yes.style=slot\n");
      fw.write("item.quit_yes.action=quit\n");
      fw.write("item.quit_no.label=No, Return\n");
      fw.write("item.quit_no.action=main_menu\n");
    }

    if (includeSave) {
      try (FileWriter fw = new FileWriter(new File(dir, MENU_LOAD_PATH))) {
        fw.write("# Load menu presentation profile (slot template)\n");
        fw.write("# Text-first workflow: edit slot profile keys, save, run runtime.\n");
        fw.write("# Slot keys:\n");
        fw.write("# item.<id>.slotPreviewEnabled=true enables embedded save thumbnail.\n");
        fw.write("# slotPreviewX/Y/Width/Height are normalized inside each row card.\n");
        fw.write("# Optional bgAsset/bgSelectedAsset/slotPreview*Asset tune row skinning.\n");
        fw.write("titleText=Load Save\n");
        fw.write("hintsText=Enter: Load    Esc: Back    Del: Delete    R: Rename\n");
        fw.write("layout=slots\n");
        fw.write("defaultItemStyle=slot\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=save_slot\n");
        fw.write("item.save_slot.style=slot\n");
        fw.write("item.save_slot.action=load_menu\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
        fw.write("item.save_slot.slotPreviewX=0.63\n");
        fw.write("item.save_slot.slotPreviewY=0.10\n");
        fw.write("item.save_slot.slotPreviewWidth=0.33\n");
        fw.write("item.save_slot.slotPreviewHeight=0.80\n");
        fw.write("# item.save_slot.bgAsset=config/menu/assets/buttons/slot.png\n");
        fw.write("# item.save_slot.bgSelectedAsset=config/menu/assets/buttons/slot_hover.png\n");
        fw.write("# item.save_slot.slotPreviewPlaceholderAsset=config/menu/assets/buttons/slot_empty.png\n");
        fw.write("# item.save_slot.slotPreviewFrameAsset=config/menu/assets/buttons/slot_frame.png\n");
      }

      try (FileWriter fw = new FileWriter(new File(dir, MENU_SAVE_PATH))) {
        fw.write("# Save menu presentation profile (new slot + slot template)\n");
        fw.write("# Text-first workflow: edit slot profile keys, save, run runtime.\n");
        fw.write("# Includes a dedicated new_slot row plus templated save_slot rows.\n");
        fw.write("# Slot preview keys share the same semantics as load.menu.\n");
        fw.write("titleText=Save Journey\n");
        fw.write("hintsText=Enter: Save    Esc: Back    Del: Delete    R: Rename\n");
        fw.write("layout=slots\n");
        fw.write("defaultItemStyle=slot\n");
        fw.write("wrapSelection=true\n");
        fw.write("items=new_slot,save_slot\n");
        fw.write("item.new_slot.label=Create New Save\n");
        fw.write("item.new_slot.style=submenu\n");
        fw.write("item.new_slot.action=save_menu\n");
        fw.write("item.new_slot.slotPreviewEnabled=true\n");
        fw.write("item.new_slot.slotPreviewX=0.63\n");
        fw.write("item.new_slot.slotPreviewY=0.10\n");
        fw.write("item.new_slot.slotPreviewWidth=0.33\n");
        fw.write("item.new_slot.slotPreviewHeight=0.80\n");
        fw.write("item.save_slot.style=slot\n");
        fw.write("item.save_slot.action=save_menu\n");
        fw.write("item.save_slot.slotPreviewEnabled=true\n");
        fw.write("item.save_slot.slotPreviewX=0.63\n");
        fw.write("item.save_slot.slotPreviewY=0.10\n");
        fw.write("item.save_slot.slotPreviewWidth=0.33\n");
        fw.write("item.save_slot.slotPreviewHeight=0.80\n");
        fw.write("# item.new_slot.bgAsset=config/menu/assets/buttons/new_slot.png\n");
        fw.write("# item.save_slot.bgAsset=config/menu/assets/buttons/slot.png\n");
        fw.write("# item.save_slot.bgSelectedAsset=config/menu/assets/buttons/slot_hover.png\n");
        fw.write("# item.save_slot.slotPreviewPlaceholderAsset=config/menu/assets/buttons/slot_empty.png\n");
        fw.write("# item.save_slot.slotPreviewFrameAsset=config/menu/assets/buttons/slot_frame.png\n");
      }
    }

    if (includeSettings) {
      try (FileWriter fw = new FileWriter(new File(dir, MENU_SETTINGS_PATH))) {
        fw.write(LayoutDslTemplates.defaultSettingsMenuTemplate());
      }
    }
  }

  private void writeCardBounds(FileWriter fw, String itemId, double x, double y, double width, double height)
      throws Exception {
    fw.write("item." + itemId + ".boundsX=" + String.format(Locale.US, "%.4f", x) + "\n");
    fw.write("item." + itemId + ".boundsY=" + String.format(Locale.US, "%.4f", y) + "\n");
    fw.write("item." + itemId + ".boundsWidth=" + String.format(Locale.US, "%.4f", width) + "\n");
    fw.write("item." + itemId + ".boundsHeight=" + String.format(Locale.US, "%.4f", height) + "\n");
  }

  private void createReadme(File dir,
                            String name,
                            boolean includeMenuPack,
                            boolean includeSave,
                            boolean includeSettings,
                            boolean includeDemoAssets,
                            boolean gitEnabled,
                            boolean gitInitialCommit)
      throws Exception {
    try (FileWriter fw = new FileWriter(new File(dir, "README.md"))) {
      fw.write("# " + name + "\n\n");
      fw.write("A visual novel project scaffolded by the JVN editor wizard.\n\n");
      if (!txtAuthor.getText().isBlank()) fw.write("**Author:** " + txtAuthor.getText().trim() + "\n\n");
      fw.write("**Template:** " + resolveProjectTemplateFromSelections().label() + "\n\n");
      fw.write("## Enabled Modules\n\n");
      fw.write("- Starter story flow: " + ((chkStarterStory != null && chkStarterStory.isSelected()) ? "yes" : "no") + "\n");
      fw.write("- Guided tutorial pack: " + ((chkTutorialPack != null && chkTutorialPack.isSelected()) ? "yes" : "no") + "\n");
      fw.write("- Bundled demo assets: " + (includeDemoAssets ? "yes (`assets/demo/...`)" : "no") + "\n");
      fw.write("- Menu profile pack: " + (includeMenuPack ? "yes" : "no") + "\n");
      fw.write("- Blank menus (custom): " + (shouldStartBlankMenus() ? "yes" : "no") + "\n");
      fw.write("- Save/load profiles: " + (includeSave ? "yes" : "no") + "\n");
      fw.write("- Settings profile: " + (includeSettings ? "yes" : "no") + "\n");
      fw.write("- History defaults: " + (chkHistoryBacklog.isSelected() ? "yes" : "no") + "\n\n");
      fw.write("- Git repository: " + (gitEnabled ? "yes" : "no") + "\n");
      fw.write("- Initial commit: " + (gitInitialCommit ? "yes" : "no") + "\n\n");

      fw.write("## Runtime Profile\n\n");
      int[] resolution = getScaledResolution();
      fw.write("- Resolution: " + resolution[0] + "x" + resolution[1] + " (" + formatAspectRatio(resolution[0], resolution[1]) + ")\n");
      fw.write("- Theme preset: " + (cmbTheme.getValue() == null ? "Dark Elegant" : cmbTheme.getValue()) + "\n");
      fw.write("- UI backend: " + (cmbRuntimeUi.getValue() == null ? "fx" : cmbRuntimeUi.getValue()) + "\n");
      fw.write("- Audio backend: " + (cmbAudioBackend.getValue() == null ? "auto" : cmbAudioBackend.getValue()) + "\n");
      fw.write("- Locale: " + (cmbLocale.getValue() == null ? "en" : cmbLocale.getValue()) + "\n");
      fw.write("- Text speed: " + (spTextSpeed == null ? 35 : spTextSpeed.getValue()) + "\n");
      fw.write("- Auto delay: " + (spAutoDelay == null ? 2000 : spAutoDelay.getValue()) + " ms\n");
      fw.write("- Volumes (bgm/sfx/voice): "
          + (spBgmVolume == null ? 0.7 : spBgmVolume.getValue()) + "/"
          + (spSfxVolume == null ? 0.8 : spSfxVolume.getValue()) + "/"
          + (spVoiceVolume == null ? 1.0 : spVoiceVolume.getValue()) + "\n\n");

      if (!txtDescription.getText().isBlank()) {
        fw.write("## Description\n\n");
        fw.write(txtDescription.getText().trim() + "\n\n");
      }

      fw.write("## Entry Points\n\n");
      fw.write("- Script: `" + ENTRY_SCRIPT_PATH + "`\n");
      fw.write("- Arc scripts: `" + STORY_TUTORIAL_SCRIPT_PATH + "`, `" + STORY_BRANCH_SCRIPT_PATH + "`, `" + STORY_EPILOGUE_SCRIPT_PATH + "`\n");
      if ((chkStarterStory != null && chkStarterStory.isSelected())
          || (chkTutorialPack != null && chkTutorialPack.isSelected())) {
        fw.write("- JES scene: `" + JES_STUDIO_SCENE_PATH + "`\n");
      }
      fw.write("- Shared character definitions: `" + CHARACTERS_SCRIPT_PATH + "` (included by all story scripts)\n");
      fw.write("- Story map: `" + STORY_MAP_PATH + "`\n");
      fw.write("- Settings: `" + SETTINGS_PATH + "`\n");
      fw.write("- Dialogue layout: `" + DIALOGUE_LAYOUT_PATH + "`\n\n");
      if (chkTutorialPack != null && chkTutorialPack.isSelected()) {
        fw.write("- Tutorial lessons: `scripts/tutorial/*.vns`\n\n");
      }
      if (includeMenuPack) {
        fw.write("- Menu registry: `" + MENU_REGISTRY_PATH + "`\n");
        fw.write("- Menu theme: `" + MENU_THEME_PATH + "`\n\n");
      }

      if (gitEnabled) {
        fw.write("## Version Control\n\n");
        fw.write("- Repo initialized with Git.\n");
        fw.write("- Default ignore rules added via `.gitignore`.\n\n");
      }

      fw.write("## Project Structure\n\n");
      fw.write("```\n");
      fw.write(buildStructurePreviewText(dir.getName()));
      fw.write("```\n\n");

      fw.write("## First Steps\n\n");
      int step = 1;
      fw.write(step++ + ". Open this folder in the JVN Editor.\n");
      fw.write(step++ + ". Edit `" + ENTRY_SCRIPT_PATH + "` and the connected arc scripts in `scripts/story/`.\n");
      fw.write(step++ + ". Keep shared character declarations in `" + CHARACTERS_SCRIPT_PATH + "` and import with `@include " + CHARACTERS_INCLUDE_PATH + "`.\n");
      fw.write(step++ + ". Open `" + STORY_MAP_PATH + "` and inspect/edit arc links.\n");
      fw.write(step++ + ". Edit `" + DIALOGUE_LAYOUT_PATH + "` in text first, then run runtime to validate.\n");
      if (chkTutorialPack != null && chkTutorialPack.isSelected()) {
        fw.write(step++ + ". Use `scripts/story/tutorial_hub.vns` as the launch point for the guided lesson scripts in `scripts/tutorial/`.\n");
      }
      if (includeMenuPack) {
        fw.write(step++ + ". Edit `config/menu/menus/*.menu`, `config/menu/layouts/*.layout`, and `config/menu/styles/*.style` in text first.\n");
        fw.write(step++ + ". Resolve Layout Studio diagnostics, then use Save and Run Runtime for authoritative rendering and navigation checks.\n");
      }
      if (shouldStartBlankMenus()) {
        fw.write(step++ + ". Create menu screens, layouts, and styles in `config/menu/` using the Layout Studio.\n");
        fw.write(step++ + ". Wire them in `config/menu/registry/menu.registry` to enable in-game save/load/settings.\n");
      }
      fw.write(step + ". Add content into `assets/` and run the project.\n");
    }
  }

  static String sanitizeName(String name) {
    if (name == null) return "";
    String s = name.trim().replaceAll("[^a-zA-Z0-9._-]", "_");
    s = s.replaceAll("_+", "_");
    s = s.replaceAll("^[._-]+", "");
    s = s.replaceAll("[._-]+$", "");
    return s;
  }

  private Spinner<Integer> createIntSpinner(int min, int max, int initial, int step) {
    Spinner<Integer> spinner = new Spinner<>(new SpinnerValueFactory.IntegerSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    spinner.setPrefWidth(160);
    addStyleClasses(spinner, "new-project-wizard-spinner");
    spinner.valueProperty().addListener((o, ov, nv) -> updateDerivedFields());
    return spinner;
  }

  private Spinner<Double> createDoubleSpinner(double min, double max, double initial, double step) {
    Spinner<Double> spinner = new Spinner<>(new SpinnerValueFactory.DoubleSpinnerValueFactory(min, max, initial, step));
    spinner.setEditable(true);
    spinner.setPrefWidth(160);
    addStyleClasses(spinner, "new-project-wizard-spinner");
    spinner.valueProperty().addListener((o, ov, nv) -> updateDerivedFields());
    return spinner;
  }

  private static void addStyleClasses(Styleable styleable, String... styleClasses) {
    if (styleable == null || styleClasses == null) return;
    for (String styleClass : styleClasses) {
      if (styleClass == null || styleClass.isBlank()) continue;
      if (!styleable.getStyleClass().contains(styleClass)) {
        styleable.getStyleClass().add(styleClass);
      }
    }
  }

  private static void styleField(javafx.scene.control.Control control) {
    addStyleClasses(control, "layout-launcher-field");
  }

  private static void stylePrimaryButton(Button button) {
    addStyleClasses(button, "welcome-action-button-primary", "new-project-wizard-action-button");
  }

  private static void styleSecondaryButton(Button button) {
    addStyleClasses(button, "layout-launcher-button", "new-project-wizard-action-button");
  }

  private static void setFieldState(javafx.scene.control.Control control, String styleClass) {
    if (control == null) return;
    control.getStyleClass().removeAll(STYLE_FIELD_VALID, STYLE_FIELD_ERROR);
    if (styleClass != null && !styleClass.isBlank()) {
      addStyleClasses(control, styleClass);
    }
  }

  private void setValidationMessage(boolean ready, String message) {
    lblValidation.setText(message);
    lblValidation.getStyleClass().removeAll(STYLE_VALIDATION_READY, STYLE_VALIDATION_ERROR);
    addStyleClasses(lblValidation, ready ? STYLE_VALIDATION_READY : STYLE_VALIDATION_ERROR);
    btnCreate.setDisable(!ready);
  }

  private static void tip(javafx.scene.control.Control control, String text) {
    Tooltip t = new Tooltip(text);
    t.setWrapText(true);
    t.setMaxWidth(320);
    control.setTooltip(t);
  }

  private void showError(String message) {
    EditorDialogs.error(
        this,
        "New Project",
        message,
        null,
        "Review the highlighted project setup fields.",
        "Confirm the project location exists and is writable.");
  }

  /**
   * Returns the created project directory, or null if cancelled.
   */
  public File getCreatedProjectDir() {
    return createdProjectDir;
  }

  /**
   * Show the wizard and return the created project directory.
   */
  public static File showAndWait(Stage owner) {
    NewProjectWizard wizard = new NewProjectWizard(owner);
    wizard.showAndWait();
    return wizard.getCreatedProjectDir();
  }
}

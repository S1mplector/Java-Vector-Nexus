package com.jvn.editor.ui;

import java.io.File;
import java.util.List;
import java.util.Locale;

import javafx.scene.layout.Region;

final class ProjectFileIcons {
  enum Kind {
    ROOT,
    FOLDER,
    SOURCE_FOLDER,
    CONFIG_FOLDER,
    EXPORT_FOLDER,
    SCRIPT_FOLDER,
    STORY_FOLDER,
    ASSET_FOLDER,
    AUDIO_FOLDER,
    LAYOUT_FOLDER,
    STYLE_FOLDER,
    DOCS_FOLDER,
    BUILD_FOLDER,
    SAVE_FOLDER,
    TEMPLATE_FOLDER,
    TEST_FOLDER,
    JAVA_FOLDER,
    UI_FOLDER,
    VIDEO_FOLDER,
    FONT_FOLDER,
    RESOURCE_FOLDER,
    PUBLIC_FOLDER,
    TOOLS_FOLDER,
    ARCHIVE_FOLDER,
    BACKUP_FOLDER,
    CI_FOLDER,
    COMPONENTS_FOLDER,
    CONTENT_FOLDER,
    CORE_FOLDER,
    COVERAGE_FOLDER,
    DEBUG_FOLDER,
    DOCKER_FOLDER,
    DOWNLOAD_FOLDER,
    EXAMPLES_FOLDER,
    FEATURES_FOLDER,
    FUNCTIONS_FOLDER,
    GIT_FOLDER,
    GITHUB_FOLDER,
    I18N_FOLDER,
    INPUT_FOLDER,
    INTERFACE_FOLDER,
    JSON_FOLDER,
    LIB_FOLDER,
    LOG_FOLDER,
    MESSAGES_FOLDER,
    MOCK_FOLDER,
    NODE_FOLDER,
    PACKAGES_FOLDER,
    PRIVATE_FOLDER,
    REPOSITORY_FOLDER,
    ROUTES_FOLDER,
    SASS_FOLDER,
    SHADER_FOLDER,
    SHARED_FOLDER,
    TEMP_FOLDER,
    TASKS_FOLDER,
    TYPESCRIPT_FOLDER,
    UPLOAD_FOLDER,
    VIEWS_FOLDER,
    SCRIPT,
    JAVA,
    KOTLIN,
    PYTHON,
    MARKDOWN,
    JSON,
    JSON_SCHEMA,
    XML,
    CSS,
    SASS,
    LESS,
    JAVASCRIPT,
    TYPESCRIPT,
    REACT,
    VUE,
    IMAGE,
    SVG_FILE,
    AUDIO,
    VIDEO,
    YAML,
    HTML,
    GRADLE,
    TOML,
    SETTINGS,
    ARCHIVE,
    DATABASE,
    FONT,
    CONSOLE,
    POWERSHELL,
    DOCKER,
    GIT,
    GITHUB_ACTIONS,
    GITLAB,
    TASKFILE,
    LOG,
    PDF,
    OFFICE,
    EXECUTABLE,
    DLL,
    NODE,
    NPM,
    LICENSE,
    CHANGELOG,
    AUTHORS,
    CREDITS,
    MERMAID,
    DRAWIO,
    ESLINT,
    PRETTIER,
    EDITORCONFIG,
    STORY,
    MENU,
    LAYOUT,
    STYLE,
    TIMELINE,
    DOCUMENT,
    NOTE
  }

  private ProjectFileIcons() {}

  static Kind kindFor(File file, File rootDir) {
    if (file == null) return Kind.DOCUMENT;
    boolean root = rootDir != null && file.getAbsoluteFile().equals(rootDir.getAbsoluteFile());
    return kindFor(file.getName(), file.isDirectory(), root);
  }

  static Kind kindFor(String name, boolean directory, boolean root) {
    if (root) return Kind.ROOT;
    if (name == null || name.isBlank()) return Kind.DOCUMENT;
    if (name.startsWith("(")) return Kind.NOTE;

    String lower = name.toLowerCase(Locale.ROOT);
    if (directory) return folderKind(lower);

    if (lower.equals("dockerfile") || lower.equals("docker-compose.yml") || lower.equals("docker-compose.yaml")) return Kind.DOCKER;
    if (lower.equals(".gitignore") || lower.equals(".gitattributes") || lower.equals(".gitmodules")) return Kind.GIT;
    if (lower.startsWith(".github")) return Kind.GITHUB_ACTIONS;
    if (lower.equals(".editorconfig")) return Kind.EDITORCONFIG;
    if (lower.startsWith(".eslint") || lower.equals("eslint.config.js") || lower.equals("eslint.config.mjs")) return Kind.ESLINT;
    if (lower.startsWith(".prettier") || lower.equals("prettier.config.js") || lower.equals("prettier.config.mjs")) return Kind.PRETTIER;
    if (lower.equals("package.json") || lower.equals("package-lock.json") || lower.equals("npm-shrinkwrap.json")) return Kind.NPM;
    if (lower.equals("taskfile.yml") || lower.equals("taskfile.yaml")) return Kind.TASKFILE;
    if (lower.equals("tsconfig.json") || lower.equals("tsconfig.base.json")) return Kind.TYPESCRIPT;
    if (lower.equals("jsconfig.json")) return Kind.JAVASCRIPT;
    if (lower.equals("jvn.project")
        || lower.equals("settings.gradle")
        || lower.equals("settings.gradle.kts")) return Kind.SETTINGS;
    if (lower.equals("build.gradle") || lower.equals("build.gradle.kts") || lower.equals("gradlew")) return Kind.GRADLE;
    if (lower.equals("readme") || lower.equals("readme.md") || lower.endsWith(".md")) return Kind.MARKDOWN;
    if (lower.equals("license") || lower.equals("license.md") || lower.startsWith("license.")) return Kind.LICENSE;
    if (lower.equals("changelog.md") || lower.equals("changelog") || lower.startsWith("changelog.")) return Kind.CHANGELOG;
    if (lower.equals("authors") || lower.equals("authors.md")) return Kind.AUTHORS;
    if (lower.equals("credits") || lower.equals("credits.md")) return Kind.CREDITS;
    if (lower.endsWith(".vns")) return Kind.STORY;
    if (lower.endsWith(".jes")) return Kind.SCRIPT;
    if (lower.endsWith(".menu") || lower.endsWith(".registry")) return Kind.MENU;
    if (lower.endsWith(".layout")) return Kind.LAYOUT;
    if (lower.endsWith(".style") || lower.endsWith(".theme")) return Kind.STYLE;
    if (lower.endsWith(".storymap") || lower.endsWith(".timeline")) return Kind.TIMELINE;
    if (lower.endsWith(".java")) return Kind.JAVA;
    if (lower.endsWith(".kt") || lower.endsWith(".kts")) return Kind.KOTLIN;
    if (lower.endsWith(".py") || lower.endsWith(".pyw")) return Kind.PYTHON;
    if (lower.endsWith(".schema.json")) return Kind.JSON_SCHEMA;
    if (lower.endsWith(".json") || lower.endsWith(".json5")) return Kind.JSON;
    if (lower.endsWith(".xml") || lower.endsWith(".fxml")) return Kind.XML;
    if (lower.endsWith(".css")) return Kind.CSS;
    if (lower.endsWith(".scss") || lower.endsWith(".sass")) return Kind.SASS;
    if (lower.endsWith(".less")) return Kind.LESS;
    if (lower.endsWith(".jsx")) return Kind.REACT;
    if (lower.endsWith(".js") || lower.endsWith(".mjs") || lower.endsWith(".cjs")) return Kind.JAVASCRIPT;
    if (lower.endsWith(".tsx")) return Kind.REACT;
    if (lower.endsWith(".ts")) return Kind.TYPESCRIPT;
    if (lower.endsWith(".vue")) return Kind.VUE;
    if (lower.endsWith(".svg")) return Kind.SVG_FILE;
    if (endsWithAny(lower, ".png", ".jpg", ".jpeg", ".gif", ".bmp", ".webp", ".ico", ".psd")) return Kind.IMAGE;
    if (endsWithAny(lower, ".wav", ".mp3", ".ogg", ".flac", ".m4a", ".aac", ".mid", ".midi")) return Kind.AUDIO;
    if (endsWithAny(lower, ".mp4", ".mov", ".webm", ".mkv", ".avi")) return Kind.VIDEO;
    if (lower.endsWith(".yaml") || lower.endsWith(".yml")) return Kind.YAML;
    if (lower.endsWith(".html") || lower.endsWith(".htm")) return Kind.HTML;
    if (lower.endsWith(".gradle")) return Kind.GRADLE;
    if (lower.endsWith(".toml")) return Kind.TOML;
    if (endsWithAny(lower, ".properties", ".ini", ".cfg", ".conf", ".prefs")) return Kind.SETTINGS;
    if (endsWithAny(lower, ".zip", ".jar", ".war", ".tar", ".gz", ".tgz", ".7z", ".rar")) return Kind.ARCHIVE;
    if (endsWithAny(lower, ".db", ".sqlite", ".sqlite3")) return Kind.DATABASE;
    if (endsWithAny(lower, ".ttf", ".otf", ".woff", ".woff2")) return Kind.FONT;
    if (lower.endsWith(".ps1")) return Kind.POWERSHELL;
    if (endsWithAny(lower, ".sh", ".bash", ".zsh", ".fish", ".bat", ".cmd")) return Kind.CONSOLE;
    if (lower.endsWith(".log")) return Kind.LOG;
    if (lower.endsWith(".pdf")) return Kind.PDF;
    if (endsWithAny(lower, ".doc", ".docx", ".rtf", ".ppt", ".pptx", ".xls", ".xlsx", ".csv")) return Kind.OFFICE;
    if (lower.endsWith(".exe") || lower.endsWith(".app")) return Kind.EXECUTABLE;
    if (lower.endsWith(".dll") || lower.endsWith(".so") || lower.endsWith(".dylib")) return Kind.DLL;
    if (lower.endsWith(".mmd") || lower.endsWith(".mermaid")) return Kind.MERMAID;
    if (lower.endsWith(".drawio") || lower.endsWith(".dio")) return Kind.DRAWIO;
    return Kind.DOCUMENT;
  }

  static Region iconFor(Kind kind) {
    return iconFor(kind, FreedesktopProjectIconPack.configuredSize());
  }

  static Region iconFor(Kind kind, double size) {
    Kind safeKind = kind != null ? kind : Kind.DOCUMENT;
    double effectiveSize = size > 0 ? size : FreedesktopProjectIconPack.configuredSize();
    List<String> semanticNames = configuredSystemIconNames(safeKind);
    return FreedesktopProjectIconPack.icon(semanticNames, effectiveSize)
        .or(() -> FreedesktopProjectIconPack.useBundledFallback()
            ? bundledIcon(safeKind, effectiveSize)
            : java.util.Optional.empty())
        .orElseGet(() -> fallbackIcon(safeKind, effectiveSize));
  }

  private static java.util.Optional<Region> bundledIcon(Kind kind, double size) {
    if (isFolderKind(kind)) {
      Region emblem = kind == Kind.FOLDER ? null
          : MaterialProjectIconPack.folderEmblem(iconName(kind), 14).orElse(null);
      return java.util.Optional.of(ShellFileArtwork.folder(size, emblem));
    }
    return switch (kind) {
      case DOCUMENT, NOTE -> java.util.Optional.of(ShellFileArtwork.document(size, null));
      case IMAGE, SVG_FILE, AUDIO, VIDEO, ARCHIVE, DATABASE, FONT, LOG, PDF, OFFICE,
          EXECUTABLE, DLL, LICENSE, CHANGELOG, AUTHORS, CREDITS, STORY, MENU, LAYOUT,
          TIMELINE, SETTINGS -> java.util.Optional.of(ShellFileArtwork.document(size,
              MaterialProjectIconPack.icon(iconName(kind), 14).orElse(null)));
      default -> MaterialProjectIconPack.icon(iconName(kind), size);
    };
  }

  private static List<String> configuredSystemIconNames(Kind kind) {
    if (isFolderKind(kind) && !FreedesktopProjectIconPack.folderVariantsEnabled()) {
      return List.of("folder");
    }
    if (!isFolderKind(kind) && !FreedesktopProjectIconPack.fileTypeVariantsEnabled()) {
      return List.of("text-x-generic");
    }
    return systemIconNames(kind);
  }

  private static boolean isFolderKind(Kind kind) {
    return kind == Kind.ROOT || kind == Kind.FOLDER || kind.name().endsWith("_FOLDER");
  }

  static List<String> systemIconNames(Kind kind) {
    Kind safeKind = kind != null ? kind : Kind.DOCUMENT;
    return switch (safeKind) {
      case ASSET_FOLDER -> List.of("folder-pictures", "folder");
      case AUDIO_FOLDER -> List.of("folder-music", "folder");
      case VIDEO_FOLDER -> List.of("folder-videos", "folder-video", "folder");
      case DOCS_FOLDER -> List.of("folder-documents", "folder");
      case DOWNLOAD_FOLDER, EXPORT_FOLDER -> List.of("folder-download", "folder");
      case TEMPLATE_FOLDER -> List.of("folder-templates", "folder");
      case PUBLIC_FOLDER -> List.of("folder-publicshare", "folder");
      case ROOT -> List.of("folder", "user-home");
      case FOLDER, SOURCE_FOLDER, CONFIG_FOLDER, SCRIPT_FOLDER, STORY_FOLDER, LAYOUT_FOLDER, STYLE_FOLDER,
          BUILD_FOLDER, SAVE_FOLDER, TEST_FOLDER, JAVA_FOLDER, UI_FOLDER, FONT_FOLDER, RESOURCE_FOLDER,
          TOOLS_FOLDER, ARCHIVE_FOLDER, BACKUP_FOLDER, CI_FOLDER, COMPONENTS_FOLDER, CONTENT_FOLDER,
          CORE_FOLDER, COVERAGE_FOLDER, DEBUG_FOLDER, DOCKER_FOLDER, EXAMPLES_FOLDER, FEATURES_FOLDER,
          FUNCTIONS_FOLDER, GIT_FOLDER, GITHUB_FOLDER, I18N_FOLDER, INPUT_FOLDER, INTERFACE_FOLDER,
          JSON_FOLDER, LIB_FOLDER, LOG_FOLDER, MESSAGES_FOLDER, MOCK_FOLDER, NODE_FOLDER, PACKAGES_FOLDER,
          PRIVATE_FOLDER, REPOSITORY_FOLDER, ROUTES_FOLDER, SASS_FOLDER, SHADER_FOLDER, SHARED_FOLDER,
          TEMP_FOLDER, TASKS_FOLDER, TYPESCRIPT_FOLDER, UPLOAD_FOLDER, VIEWS_FOLDER -> List.of("folder");
      case IMAGE, SVG_FILE, MERMAID, DRAWIO -> List.of("image-x-generic", "text-x-generic");
      case AUDIO -> List.of("audio-x-generic", "application-ogg", "text-x-generic");
      case VIDEO, TIMELINE -> List.of("video-x-generic", "text-x-generic");
      case SCRIPT, STORY, CONSOLE, POWERSHELL -> List.of("text-x-script", "text-x-generic");
      case JAVA -> List.of("text-x-java-source", "text-x-java", "text-x-source", "text-x-generic");
      case KOTLIN -> List.of("text-x-kotlin", "text-x-source", "text-x-generic");
      case PYTHON -> List.of("text-x-python", "text-x-script", "text-x-generic");
      case MARKDOWN -> List.of("text-x-readme", "text-markdown", "text-x-generic");
      case HTML -> List.of("text-html", "text-x-generic");
      case ARCHIVE -> List.of("package-x-generic", "application-x-archive", "text-x-generic");
      case DATABASE -> List.of("application-x-sqlite3", "application-x-generic", "text-x-generic");
      case FONT -> List.of("font-x-generic", "text-x-generic");
      case PDF -> List.of("application-pdf", "gnome-mime-application-pdf", "text-x-generic");
      case OFFICE -> List.of("x-office-document", "text-x-generic");
      case EXECUTABLE -> List.of("application-x-executable", "application-x-generic");
      case DLL -> List.of("application-x-sharedlib", "application-x-generic");
      case SETTINGS -> List.of("preferences-system", "application-x-generic", "text-x-generic");
      case NOTE -> List.of("dialog-warning", "text-x-generic");
      case XML, CSS, STYLE, SASS, LESS, JAVASCRIPT, TYPESCRIPT, REACT, VUE, YAML, GRADLE, TOML, MENU,
          LAYOUT, JSON, JSON_SCHEMA, DOCKER, GIT, GITHUB_ACTIONS, GITLAB, TASKFILE, LOG, NODE, NPM,
          LICENSE, CHANGELOG, AUTHORS, CREDITS, ESLINT, PRETTIER, EDITORCONFIG, DOCUMENT ->
          List.of("text-x-generic");
    };
  }

  private static Kind folderKind(String lower) {
    return switch (lower) {
      case "src", "source", "sources" -> Kind.SOURCE_FOLDER;
      case "config", "configs", "configuration", "settings" -> Kind.CONFIG_FOLDER;
      case "exports", "export" -> Kind.EXPORT_FOLDER;
      case "scripts", "script" -> Kind.SCRIPT_FOLDER;
      case "story", "stories", "storyboard", "storyboards", "storybook" -> Kind.STORY_FOLDER;
      case "assets", "asset", "images", "image", "img", "sprites", "textures", "backgrounds", "characters" -> Kind.ASSET_FOLDER;
      case "audio", "music", "sfx", "sound", "sounds", "voice", "voices" -> Kind.AUDIO_FOLDER;
      case "layout", "layouts" -> Kind.LAYOUT_FOLDER;
      case "style", "styles", "theme", "themes" -> Kind.STYLE_FOLDER;
      case "docs", "doc", "documentation" -> Kind.DOCS_FOLDER;
      case "build", "dist", "out", "target" -> Kind.BUILD_FOLDER;
      case "save", "saves", "data", "database", "db" -> Kind.SAVE_FOLDER;
      case "template", "templates" -> Kind.TEMPLATE_FOLDER;
      case "test", "tests" -> Kind.TEST_FOLDER;
      case "java", "kotlin", "jvm" -> Kind.JAVA_FOLDER;
      case "ui", "menu", "menus" -> Kind.UI_FOLDER;
      case "video", "videos", "movie", "movies" -> Kind.VIDEO_FOLDER;
      case "font", "fonts" -> Kind.FONT_FOLDER;
      case "resources", "resource", "res" -> Kind.RESOURCE_FOLDER;
      case "public", "static" -> Kind.PUBLIC_FOLDER;
      case "tools", "tool", "util", "utils", "utilities" -> Kind.TOOLS_FOLDER;
      case "archive", "archives", "zip", "zips" -> Kind.ARCHIVE_FOLDER;
      case "backup", "backups" -> Kind.BACKUP_FOLDER;
      case "ci", ".ci" -> Kind.CI_FOLDER;
      case "components", "component" -> Kind.COMPONENTS_FOLDER;
      case "content", "contents" -> Kind.CONTENT_FOLDER;
      case "core", "engine" -> Kind.CORE_FOLDER;
      case "coverage" -> Kind.COVERAGE_FOLDER;
      case "debug", "debugging" -> Kind.DEBUG_FOLDER;
      case "docker", ".docker" -> Kind.DOCKER_FOLDER;
      case "download", "downloads" -> Kind.DOWNLOAD_FOLDER;
      case "example", "examples", "sample", "samples" -> Kind.EXAMPLES_FOLDER;
      case "feature", "features" -> Kind.FEATURES_FOLDER;
      case "function", "functions" -> Kind.FUNCTIONS_FOLDER;
      case "git", ".git", ".githooks" -> Kind.GIT_FOLDER;
      case "github", ".github", "workflows" -> Kind.GITHUB_FOLDER;
      case "i18n", "l10n", "locale", "locales", "translations" -> Kind.I18N_FOLDER;
      case "input", "inputs" -> Kind.INPUT_FOLDER;
      case "interface", "interfaces" -> Kind.INTERFACE_FOLDER;
      case "json" -> Kind.JSON_FOLDER;
      case "lib", "libs", "library", "libraries" -> Kind.LIB_FOLDER;
      case "log", "logs" -> Kind.LOG_FOLDER;
      case "message", "messages" -> Kind.MESSAGES_FOLDER;
      case "mock", "mocks", "fixtures" -> Kind.MOCK_FOLDER;
      case "node", "node_modules" -> Kind.NODE_FOLDER;
      case "package", "packages" -> Kind.PACKAGES_FOLDER;
      case "private", "secrets" -> Kind.PRIVATE_FOLDER;
      case "repository", "repositories", "repo", "repos" -> Kind.REPOSITORY_FOLDER;
      case "route", "routes", "routing" -> Kind.ROUTES_FOLDER;
      case "sass", "scss" -> Kind.SASS_FOLDER;
      case "shader", "shaders", "glsl" -> Kind.SHADER_FOLDER;
      case "shared", "common" -> Kind.SHARED_FOLDER;
      case "tmp", "temp", "temporary" -> Kind.TEMP_FOLDER;
      case "task", "tasks" -> Kind.TASKS_FOLDER;
      case "typescript", "types" -> Kind.TYPESCRIPT_FOLDER;
      case "upload", "uploads" -> Kind.UPLOAD_FOLDER;
      case "view", "views" -> Kind.VIEWS_FOLDER;
      case "gradle" -> Kind.BUILD_FOLDER;
      default -> Kind.FOLDER;
    };
  }

  private static String iconName(Kind kind) {
    return switch (kind) {
      case ROOT -> "folder-project";
      case FOLDER -> "folder-other";
      case SOURCE_FOLDER -> "folder-src";
      case CONFIG_FOLDER -> "folder-config";
      case EXPORT_FOLDER -> "folder-export";
      case SCRIPT_FOLDER -> "folder-scripts";
      case STORY_FOLDER -> "folder-storybook";
      case ASSET_FOLDER -> "folder-images";
      case AUDIO_FOLDER -> "folder-audio";
      case LAYOUT_FOLDER -> "folder-layout";
      case STYLE_FOLDER -> "folder-theme";
      case DOCS_FOLDER -> "folder-docs";
      case BUILD_FOLDER -> "folder-dist";
      case SAVE_FOLDER -> "folder-database";
      case TEMPLATE_FOLDER -> "folder-template";
      case TEST_FOLDER -> "folder-test";
      case JAVA_FOLDER -> "folder-java";
      case UI_FOLDER -> "folder-ui";
      case VIDEO_FOLDER -> "folder-video";
      case FONT_FOLDER -> "folder-font";
      case RESOURCE_FOLDER -> "folder-resource";
      case PUBLIC_FOLDER -> "folder-public";
      case TOOLS_FOLDER -> "folder-tools";
      case ARCHIVE_FOLDER -> "folder-archive";
      case BACKUP_FOLDER -> "folder-backup";
      case CI_FOLDER -> "folder-ci";
      case COMPONENTS_FOLDER -> "folder-components";
      case CONTENT_FOLDER -> "folder-content";
      case CORE_FOLDER -> "folder-core";
      case COVERAGE_FOLDER -> "folder-coverage";
      case DEBUG_FOLDER -> "folder-debug";
      case DOCKER_FOLDER -> "folder-docker";
      case DOWNLOAD_FOLDER -> "folder-download";
      case EXAMPLES_FOLDER -> "folder-examples";
      case FEATURES_FOLDER -> "folder-features";
      case FUNCTIONS_FOLDER -> "folder-functions";
      case GIT_FOLDER -> "folder-git";
      case GITHUB_FOLDER -> "folder-github";
      case I18N_FOLDER -> "folder-i18n";
      case INPUT_FOLDER -> "folder-input";
      case INTERFACE_FOLDER -> "folder-interface";
      case JSON_FOLDER -> "folder-json";
      case LIB_FOLDER -> "folder-lib";
      case LOG_FOLDER -> "folder-log";
      case MESSAGES_FOLDER -> "folder-messages";
      case MOCK_FOLDER -> "folder-mock";
      case NODE_FOLDER -> "folder-node";
      case PACKAGES_FOLDER -> "folder-packages";
      case PRIVATE_FOLDER -> "folder-private";
      case REPOSITORY_FOLDER -> "folder-repository";
      case ROUTES_FOLDER -> "folder-routes";
      case SASS_FOLDER -> "folder-sass";
      case SHADER_FOLDER -> "folder-shader";
      case SHARED_FOLDER -> "folder-shared";
      case TEMP_FOLDER -> "folder-temp";
      case TASKS_FOLDER -> "folder-tasks";
      case TYPESCRIPT_FOLDER -> "folder-typescript";
      case UPLOAD_FOLDER -> "folder-upload";
      case VIEWS_FOLDER -> "folder-views";
      case SCRIPT -> "console";
      case JAVA -> "java";
      case KOTLIN -> "kotlin";
      case PYTHON -> "python";
      case MARKDOWN -> "markdown";
      case JSON -> "json";
      case JSON_SCHEMA -> "json_schema";
      case XML -> "xml";
      case CSS, STYLE -> "css";
      case SASS -> "sass";
      case LESS -> "less";
      case JAVASCRIPT -> "javascript";
      case TYPESCRIPT -> "typescript";
      case REACT -> "react";
      case VUE -> "vue";
      case IMAGE -> "image";
      case SVG_FILE -> "svg";
      case AUDIO -> "audio";
      case VIDEO -> "video";
      case YAML -> "yaml";
      case HTML -> "html";
      case GRADLE -> "gradle";
      case TOML -> "toml";
      case SETTINGS, MENU, LAYOUT -> "settings";
      case ARCHIVE -> "zip";
      case DATABASE -> "database";
      case FONT -> "font";
      case CONSOLE -> "console";
      case POWERSHELL -> "powershell";
      case DOCKER -> "docker";
      case GIT -> "git";
      case GITHUB_ACTIONS -> "github-actions-workflow";
      case GITLAB -> "gitlab";
      case TASKFILE -> "taskfile";
      case LOG -> "log";
      case PDF -> "pdf";
      case OFFICE -> "word";
      case EXECUTABLE -> "exe";
      case DLL -> "dll";
      case NODE -> "nodejs";
      case NPM -> "npm";
      case LICENSE -> "license";
      case CHANGELOG -> "changelog";
      case AUTHORS -> "authors";
      case CREDITS -> "credits";
      case MERMAID -> "mermaid";
      case DRAWIO -> "drawio";
      case ESLINT -> "eslint";
      case PRETTIER -> "prettier";
      case EDITORCONFIG -> "editorconfig";
      case STORY, TIMELINE -> "storybook";
      case NOTE, DOCUMENT -> "document";
    };
  }

  private static Region fallbackIcon(Kind kind, double size) {
    return switch (kind) {
      case ROOT -> CssIcon.folder("#d5b36a", size);
      case FOLDER, SOURCE_FOLDER, CONFIG_FOLDER, EXPORT_FOLDER, SCRIPT_FOLDER, STORY_FOLDER, ASSET_FOLDER,
          AUDIO_FOLDER, LAYOUT_FOLDER, STYLE_FOLDER, DOCS_FOLDER, BUILD_FOLDER, SAVE_FOLDER, TEMPLATE_FOLDER,
          TEST_FOLDER, JAVA_FOLDER, UI_FOLDER, VIDEO_FOLDER, FONT_FOLDER, RESOURCE_FOLDER, PUBLIC_FOLDER,
          TOOLS_FOLDER, ARCHIVE_FOLDER, BACKUP_FOLDER, CI_FOLDER, COMPONENTS_FOLDER, CONTENT_FOLDER, CORE_FOLDER,
          COVERAGE_FOLDER, DEBUG_FOLDER, DOCKER_FOLDER, DOWNLOAD_FOLDER, EXAMPLES_FOLDER, FEATURES_FOLDER,
          FUNCTIONS_FOLDER, GIT_FOLDER, GITHUB_FOLDER, I18N_FOLDER, INPUT_FOLDER, INTERFACE_FOLDER, JSON_FOLDER,
          LIB_FOLDER, LOG_FOLDER, MESSAGES_FOLDER, MOCK_FOLDER, NODE_FOLDER, PACKAGES_FOLDER, PRIVATE_FOLDER,
          REPOSITORY_FOLDER, ROUTES_FOLDER, SASS_FOLDER, SHADER_FOLDER, SHARED_FOLDER, TEMP_FOLDER, TASKS_FOLDER,
          TYPESCRIPT_FOLDER, UPLOAD_FOLDER, VIEWS_FOLDER -> CssIcon.folder("#cbb27b", size);
      case SCRIPT, STORY -> CssIcon.speech("#8bcf98");
      case MENU -> CssIcon.list("#dccba2");
      case LAYOUT -> CssIcon.grid("#8ec7dd");
      case STYLE, CSS, SASS, LESS -> CssIcon.palette("#d6b4ff");
      case TIMELINE, VIDEO -> CssIcon.play("#dd9a48");
      case NOTE -> CssIcon.warning("#efbf82");
      case IMAGE, SVG_FILE, MERMAID, DRAWIO -> CssIcon.expand("#26a69a");
      case AUDIO -> CssIcon.play("#ef5350");
      case JAVA, KOTLIN, PYTHON, MARKDOWN, JSON, JSON_SCHEMA, XML, JAVASCRIPT, TYPESCRIPT, REACT, VUE, YAML,
          HTML, GRADLE, TOML, SETTINGS, ARCHIVE, DATABASE, FONT, CONSOLE, POWERSHELL, DOCKER, GIT,
          GITHUB_ACTIONS, GITLAB, TASKFILE, LOG, PDF, OFFICE, EXECUTABLE, DLL, NODE, NPM, LICENSE, CHANGELOG,
          AUTHORS, CREDITS, ESLINT, PRETTIER, EDITORCONFIG, DOCUMENT -> CssIcon.document("#c6d1dc");
    };
  }

  private static boolean endsWithAny(String value, String... suffixes) {
    for (String suffix : suffixes) {
      if (value.endsWith(suffix)) return true;
    }
    return false;
  }
}

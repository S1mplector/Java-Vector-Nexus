package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScriptEditorWorkspaceModelTest {

  @TempDir
  Path tempRoot;

  @Test
  void indexCollectsScriptMetadataAndWorkspaceStats() throws Exception {
    Path scripts = Files.createDirectories(tempRoot.resolve("scripts"));
    Files.createDirectories(scripts.resolve("story"));
    Files.createDirectories(scripts.resolve("defs"));
    Files.createDirectories(tempRoot.resolve("config/menu/menus"));

    Files.writeString(scripts.resolve("story/prologue.vns"), """
        @include /defs/characters.vns
        @label start
        narrator: Hello
        @label branch
        narrator: Bye
        """);
    Files.writeString(scripts.resolve("defs/characters.vns"), """
        @label catalog
        narrator: Definitions
        """);
    Files.writeString(tempRoot.resolve("config/menu/menus/main.menu"), """
        title=Main
        item.start.text=Start
        """);

    ScriptEditorWorkspaceModel.WorkspaceSnapshot snapshot =
        ScriptEditorWorkspaceModel.index(tempRoot.toFile());

    assertTrue(snapshot.hasContentRoot());
    assertEquals(3, snapshot.scripts().size());
    assertEquals(3, snapshot.folderCount());
    assertEquals(3, snapshot.totalLabelCount());

    ScriptEditorWorkspaceModel.ScriptFileEntry prologue = snapshot.scripts().stream()
        .filter(entry -> entry.relativePath().equals("scripts/story/prologue.vns"))
        .findFirst()
        .orElseThrow();
    assertEquals(5, prologue.lineCount());
    assertEquals(2, prologue.labelCount());
    assertEquals(1, prologue.includeCount());
    assertIterableEquals(List.of("start", "branch"), prologue.labelNames());
    assertEquals("scripts/story/prologue.vns", prologue.projectRelativePath());
    assertEquals(FileEditorTab.Kind.VNS, prologue.kind());

    ScriptEditorWorkspaceModel.ScriptFileEntry menu = snapshot.scripts().stream()
        .filter(entry -> entry.relativePath().equals("config/menu/menus/main.menu"))
        .findFirst()
        .orElseThrow();
    assertEquals(FileEditorTab.Kind.MENU_SCREEN, menu.kind());
    assertEquals(0, menu.labelCount());
  }

  @Test
  void filterMatchesRelativePathsAndLabels() throws Exception {
    Path scripts = Files.createDirectories(tempRoot.resolve("scripts/story"));
    Files.writeString(scripts.resolve("chapter_one.vns"), """
        @label intro
        narrator: Start
        """);
    Files.writeString(scripts.resolve("chapter_two.vns"), """
        @label epilogue
        narrator: End
        """);

    List<ScriptEditorWorkspaceModel.ScriptFileEntry> entries =
        ScriptEditorWorkspaceModel.index(tempRoot.toFile()).scripts();

    assertEquals(
        List.of("chapter_one.vns"),
        ScriptEditorWorkspaceModel.filter(entries, "intro").stream()
            .map(ScriptEditorWorkspaceModel.ScriptFileEntry::displayName)
            .toList());
    assertEquals(
        List.of("chapter_two.vns"),
        ScriptEditorWorkspaceModel.filter(entries, "chapter_two").stream()
            .map(ScriptEditorWorkspaceModel.ScriptFileEntry::displayName)
            .toList());
  }

  @Test
  void normalizeRelativeScriptPathKeepsPathsInsideScriptsTree() {
    assertEquals("scripts/story/new_scene.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath(null));
    assertEquals("scripts/story/chapter_01.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("story/chapter_01"));
    assertEquals("scripts/story/chapter_02.vns", ScriptEditorWorkspaceModel.normalizeRelativeScriptPath("/scripts/story/chapter_02.vns"));
  }

  @Test
  void createScriptBuildsDirectoriesAndWritesDefaultTemplate() throws Exception {
    File created = ScriptEditorWorkspaceModel.createScript(tempRoot.toFile(), "story/act1/opening");
    assertNotNull(created);
    assertTrue(created.isFile());
    assertTrue(created.getPath().replace('\\', '/').endsWith("scripts/story/act1/opening.vns"));

    String text = Files.readString(created.toPath());
    assertTrue(text.contains("@label start"));
    assertTrue(text.contains("narrator: TODO"));

    File secondCall = ScriptEditorWorkspaceModel.createScript(tempRoot.toFile(), "story/act1/opening.vns");
    assertEquals(created.getAbsolutePath(), secondCall.getAbsolutePath());
    assertFalse(Files.readString(secondCall.toPath()).isBlank());
  }

  @Test
  void createTextFileSupportsNonScriptProjectFiles() throws Exception {
    File created = ScriptEditorWorkspaceModel.createTextFile(tempRoot.toFile(), "config/menu/menus/settings.menu");
    assertNotNull(created);
    assertTrue(created.isFile());
    assertTrue(created.getPath().replace('\\', '/').endsWith("config/menu/menus/settings.menu"));

    String text = Files.readString(created.toPath());
    assertTrue(text.contains("title=Settings"));
  }
  @Test
  void indexingAcceptsRelativeProjectRoots() throws Exception {
    Files.writeString(tempRoot.resolve("scene.vns"), "@label start\n");
    Path relative = Path.of("").toAbsolutePath().relativize(tempRoot);
    var snapshot = ScriptEditorWorkspaceModel.index(relative.toFile());
    assertEquals("scene.vns", snapshot.scripts().getFirst().projectRelativePath());
  }

  @Test
  void indexingDoesNotFollowDirectoryLinkCyclesOrExternalFolders() throws Exception {
    Path project = Files.createDirectory(tempRoot.resolve("project"));
    Files.writeString(project.resolve("scene.vns"), "@label start\n");
    Path outside = Files.createDirectory(tempRoot.resolve("outside"));
    Files.writeString(outside.resolve("private.vns"), "@label private\n");
    try {
      Files.createSymbolicLink(project.resolve("loop"), project);
      Files.createSymbolicLink(project.resolve("external"), outside);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
      org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + ex.getMessage());
    }
    var snapshot = assertTimeoutPreemptively(java.time.Duration.ofSeconds(3),
        () -> ScriptEditorWorkspaceModel.index(project.toFile()));
    assertEquals(1, snapshot.scripts().size());
  }

  @Test
  void creatingExistingFilePreservesContentsAndRejectsDirectories() throws Exception {
    Path existing = Files.writeString(tempRoot.resolve("scene.vns"), "important work");
    ScriptEditorWorkspaceModel.createTextFile(tempRoot.toFile(), "scene.vns");
    assertEquals("important work", Files.readString(existing));
    Files.createDirectory(tempRoot.resolve("folder.vns"));
    assertThrows(java.io.IOException.class,
        () -> ScriptEditorWorkspaceModel.createTextFile(tempRoot.toFile(), "folder.vns"));
  }

  @Test
  void creationCannotEscapeThroughParentTraversalOrDirectoryLinks() throws Exception {
    Path project = Files.createDirectory(tempRoot.resolve("project"));
    Path outside = Files.createDirectory(tempRoot.resolve("outside"));
    assertThrows(java.io.IOException.class,
        () -> ScriptEditorWorkspaceModel.createTextFile(project.toFile(), "../escape.vns"));
    try {
      Files.createSymbolicLink(project.resolve("linked"), outside);
    } catch (UnsupportedOperationException | java.nio.file.FileSystemException ex) {
      org.junit.jupiter.api.Assumptions.abort("Symbolic links unavailable: " + ex.getMessage());
    }
    assertThrows(java.io.IOException.class,
        () -> ScriptEditorWorkspaceModel.createTextFile(project.toFile(), "linked/new/escape.vns"));
    assertFalse(Files.exists(outside.resolve("new")));
  }

  @Test
  void renameRequiresASingleNameAndRetainsExtension() throws Exception {
    File original = Files.writeString(tempRoot.resolve("scene.vns"), "story").toFile();
    for (String name : List.of("../escape.vns", "..\\escape.vns", "/tmp/escape.vns", ".", "..")) {
      assertThrows(java.io.IOException.class, () -> ScriptEditorWorkspaceModel.renameTextFile(original, name));
    }
    File renamed = ScriptEditorWorkspaceModel.renameTextFile(original, "chapter");
    assertEquals("chapter.vns", renamed.getName());
    assertEquals("story", Files.readString(renamed.toPath()));
  }

}

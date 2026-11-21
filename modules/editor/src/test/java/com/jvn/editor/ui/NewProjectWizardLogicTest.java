package com.jvn.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NewProjectWizardLogicTest {

  @Test
  void sanitizeNameKeepsCrossPlatformSafeSlug() {
    assertEquals("My_Visual_Novel", NewProjectWizard.sanitizeName(" My Visual Novel! "));
    assertEquals("route.alpha-01", NewProjectWizard.sanitizeName("route.alpha-01"));
    assertEquals("", NewProjectWizard.sanitizeName("???"));
  }

  @Test
  void safeFolderNameAllowsOnlyExpectedCharacters() {
    assertTrue(NewProjectWizard.isSafeFolderName("my_vn-project.01"));
    assertFalse(NewProjectWizard.isSafeFolderName("my vn"));
    assertFalse(NewProjectWizard.isSafeFolderName("folder/name"));
    assertFalse(NewProjectWizard.isSafeFolderName(""));
    assertFalse(NewProjectWizard.isSafeFolderName("."));
    assertFalse(NewProjectWizard.isSafeFolderName(".."));
  }
}

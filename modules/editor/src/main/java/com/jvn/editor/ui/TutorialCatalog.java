package com.jvn.editor.ui;

import java.util.List;

/** One inventory for generated tutorial files, story-map nodes and route validation. */
final class TutorialCatalog {
  record Lesson(String arc, String path, String hubLabel) {}
  private TutorialCatalog() {}
  static final List<Lesson> LESSONS = List.of(
      new Lesson("T01_Dialogue", "scripts/tutorial/01_dialogue_basics.vns", "open_dialogue"),
      new Lesson("T02_Narration", "scripts/tutorial/02_narration_and_pacing.vns", "open_narration"),
      new Lesson("T03_Expressions", "scripts/tutorial/03_expressions_and_characters.vns", "open_expressions"),
      new Lesson("T04_Images", "scripts/tutorial/04_images_and_backgrounds.vns", "open_images"),
      new Lesson("T05_Transitions", "scripts/tutorial/05_transitions_and_effects.vns", "open_transitions"),
      new Lesson("T06_Audio", "scripts/tutorial/06_audio_and_music.vns", "open_audio"),
      new Lesson("T07_Variables", "scripts/tutorial/07_variables_and_conditions.vns", "open_variables"),
      new Lesson("T08_Movement", "scripts/tutorial/08_character_movement.vns", "open_movement"),
      new Lesson("T09_Puppeteer", "scripts/tutorial/09_puppeteer_timeline.vns", "open_puppeteer"),
      new Lesson("T10_Menus", "scripts/tutorial/10_choices_and_menus.vns", "open_menus"),
      new Lesson("T11_Subroutines", "scripts/tutorial/11_subroutines_and_flow.vns", "open_subroutines"),
      new Lesson("T12_BestPractices", "scripts/tutorial/12_best_practices.vns", "open_best_practices"),
      new Lesson("T13_Camera", "scripts/tutorial/13_camera_and_staging.vns", "open_camera"),
      new Lesson("T14_Localization", "scripts/tutorial/14_localization_and_textkeys.vns", "open_localization"),
      new Lesson("T15_UILayout", "scripts/tutorial/15_ui_layout_and_theme.vns", "open_ui_layout"),
      new Lesson("T16_TestingRelease", "scripts/tutorial/16_testing_and_release.vns", "open_testing_release"),
      new Lesson("T17_VnsJes", "scripts/tutorial/17_vns_jes_integration.vns", "open_vns_jes"),
      new Lesson("T18_Plugins", "scripts/tutorial/18_plugins_and_line_boil.vns", "open_plugins_and_line_boil"),
      new Lesson("T19_Saves", "scripts/tutorial/19_saves_rollback_persistence.vns", "open_saves_rollback_persistence"),
      new Lesson("T20_Presentation", "scripts/tutorial/20_dialogue_presentation.vns", "open_dialogue_presentation"),
      new Lesson("T21_Facets", "scripts/tutorial/21_reactive_ui_and_facets.vns", "open_reactive_ui_and_facets"),
      new Lesson("T22_Phone", "scripts/tutorial/22_phone_storytelling.vns", "open_phone_storytelling"),
      new Lesson("T23_Particles", "scripts/tutorial/23_particles_and_visualizers.vns", "open_particles_and_visualizers"),
      new Lesson("T24_Stage", "scripts/tutorial/24_layer_groups_and_lighting.vns", "open_layer_groups_and_lighting"),
      new Lesson("T25_JesTools", "scripts/tutorial/25_jes_entities_input_widgets.vns", "open_jes_entities_input_widgets"),
      new Lesson("T26_JesWorlds", "scripts/tutorial/26_jes_worlds_physics_rpg.vns", "open_jes_worlds_physics_rpg"),
      new Lesson("T27_Editor", "scripts/tutorial/27_editor_workflows.vns", "open_editor_workflows"),
      new Lesson("T28_Assets", "scripts/tutorial/28_assets_and_performance.vns", "open_assets_and_performance"),
      new Lesson("T29_Accessibility", "scripts/tutorial/29_accessibility_and_controls.vns", "open_accessibility_and_controls"),
      new Lesson("T30_Extensions", "scripts/tutorial/30_java_extensions_and_capstone.vns", "open_java_extensions_and_capstone"));
}

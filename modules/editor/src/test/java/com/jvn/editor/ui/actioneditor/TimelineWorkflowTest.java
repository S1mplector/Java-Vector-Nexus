package com.jvn.editor.ui.actioneditor;

import static org.junit.jupiter.api.Assertions.*;
import com.jvn.core.animation.Easing;
import com.jvn.core.animation.TimelineDataParser;
import org.junit.jupiter.api.Test;

class TimelineWorkflowTest {
    @Test void updatesOnlyLinkedBlockAfterLinesMove() {
        String block = "  timeline {\n    // a } in a comment\n    event \"quoted } brace\"\n  }";
        String source = "before\n" + block + "\nafter";
        TimelineSourceLink link = new TimelineSourceLink(source, 1);
        String moved = "new line\n" + source;
        assertEquals(moved.indexOf(block), link.locate(moved));
        var edit = link.prepare(moved, "timeline {\n  wait 100\n}");
        String updated = moved.substring(0, edit.start()) + edit.text() + moved.substring(edit.end());
        link.applied(edit);
        assertTrue(updated.startsWith("new line\nbefore\n  timeline"));
        assertTrue(updated.endsWith("\nafter"));
        assertDoesNotThrow(() -> link.prepare(updated, "timeline {}"));
    }

    @Test void refusesChangedRemovedAndDuplicateBlocks() {
        String source = "timeline {\n  wait 100\n}";
        TimelineSourceLink link = new TimelineSourceLink(source, 0);
        assertThrows(IllegalStateException.class, () -> link.prepare(source.replace("100", "200"), "timeline {}"));
        assertThrows(IllegalStateException.class, () -> link.prepare("", "timeline {}"));
        assertThrows(IllegalStateException.class, () -> link.prepare(source + "\n" + source, "timeline {}"));
        assertThrows(IllegalArgumentException.class, () -> new TimelineSourceLink("timeline {", 0));
    }

    @Test void preservesWindowsLineEndings() {
        TimelineSourceLink link = new TimelineSourceLink("timeline {\r\n}\r\nnext", 0);
        assertEquals("timeline {\r\n  wait 100\r\n}", link.prepare(
            "timeline {\r\n}\r\nnext", "timeline {\n  wait 100\n}").text());
    }

    @Test void editableBlockPreservesEditorKeysAndParsesAtRuntime() {
        AnimationProject project = new AnimationProject();
        project.setName("reusable");
        EntityTrack hero = project.getOrCreateTrack("hero");
        hero.addKeyframe(PropertyType.X, new Keyframe(0, 10, Easing.Type.LINEAR));
        hero.addKeyframe(PropertyType.X, new Keyframe(500, 80, Easing.Type.EASE_OUT_QUAD));
        for (boolean nested : new boolean[] {false, true}) {
            String code = CodeExporter.exportEditableInline(project, nested);
            assertTrue(code.indexOf("// @jvn-puppeteer-") > code.indexOf('{'));
            assertDoesNotThrow(() -> TimelineDataParser.parse("runtime", code));
            var snapshot = com.jvn.editor.ui.PuppeteerLauncherPanel.resolveSnapshot(code, 1);
            assertTrue(snapshot.hasInlineTimeline());
            AnimationProject restored = CodeImporter.importCode("reopened", "timeline {\n" + snapshot.inlineTimelineBody + "}");
            assertEquals(hero.getValueAt(PropertyType.X, 250),
                restored.getTrack("hero").getValueAt(PropertyType.X, 250), 0.001);
        }
    }
}

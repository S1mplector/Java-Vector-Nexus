package com.jvn.core.vn;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerTargetNamingTest {

    @Test
    void selectorSafeNameCollapsesUnsafeCharacters() {
        assertEquals("hero_arm-l", LayerTargetNaming.selectorSafeName("hero arm-l"));
        assertEquals("abc", LayerTargetNaming.selectorSafeName("__abc__"));
        assertEquals("", LayerTargetNaming.selectorSafeName(null));
        assertEquals("arm-l", LayerTargetNaming.selectorSafeName("arm-l"));
    }

    @Test
    void selectorNamesPreserveUnicodeHyphensAndSeparatorRules() {
        assertEquals("éclair_猫-2", LayerTargetNaming.selectorSafeName(" __éclair!!__猫-2__ "));
        assertEquals("a_b_c", LayerTargetNaming.selectorSafeName("a___b / + c"));
        assertEquals("-", LayerTargetNaming.selectorSafeName("__-__"));
        assertEquals("", LayerTargetNaming.selectorSafeName("_ + / 😀 __"));
    }

    @Test
    void layerTargetNamesBuildsExpressionAndBareCandidates() {
        List<String> names = LayerTargetNaming.layerTargetNames("hero", "happy", "arm_l");
        assertEquals(List.of("hero_happy_arm_l", "hero_arm_l"), names);
    }

    @Test
    void layerTargetNamesDefaultsBlankExpressionToNeutral() {
        List<String> names = LayerTargetNaming.layerTargetNames("hero", "", "arm_l");
        assertTrue(names.contains("hero_neutral_arm_l"));
        assertTrue(names.contains("hero_arm_l"));
    }

    @Test
    void groupTargetNamesBuildsGroupCandidates() {
        List<String> names = LayerTargetNaming.groupTargetNames("hero", "happy", "arms");
        assertEquals(List.of("hero_arms", "hero_happy_arms"), names);
    }

    @Test
    void declaredLayerTargetNamesCoversAllDeclaredExpressions() {
        VnCharacter character = VnCharacter.builder("hero")
            .addExpression("neutral", "neutral.png", List.of("arm_l"))
            .addExpression("happy", "happy.png", List.of("arm_l"))
            .addLayer("arm_l", "arm_l.png")
            .build();

        List<String> names = LayerTargetNaming.declaredLayerTargetNames(
            character, "hero", "sad", "arm_l");

        assertTrue(names.contains("hero_arm_l"));
        assertTrue(names.contains("hero_sad_arm_l"));
        assertTrue(names.contains("hero_neutral_arm_l"));
        assertTrue(names.contains("hero_happy_arm_l"));
    }

    @Test
    void declaredLayerTargetNamesReturnsEmptyForBlankCharacterOrLayer() {
        assertEquals(List.of(), LayerTargetNaming.declaredLayerTargetNames(null, "", "happy", "arm_l"));
        assertEquals(List.of(), LayerTargetNaming.declaredLayerTargetNames(null, "hero", "happy", ""));
    }
}

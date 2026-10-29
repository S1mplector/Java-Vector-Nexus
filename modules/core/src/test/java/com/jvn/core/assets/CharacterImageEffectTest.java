package com.jvn.core.assets;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
class CharacterImageEffectTest {
  @Test void settingsAreScopedAndDefaultClearsAnOverride() {
    var settings = CharacterImageEffect.from(Map.of("character.lavender.imageEffect", "pikupiku.lineboil",
        "character.lavender.imageEffect.fps", 8, "character.lavender.imageEffect.strength", "default",
        "character.other.imageEffect.seed", 123), "lavender");
    assertEquals("pikupiku.lineboil", settings.id()); assertEquals(Map.of("fps", 8.0), settings.parameters());
    assertSame(CharacterImageEffect.NONE, CharacterImageEffect.from(Map.of(), "lavender"));
    assertSame(CharacterImageEffect.NONE, CharacterImageEffect.from(Map.of("character.lavender.imageEffect", "none"), "lavender"));
    assertThrows(IllegalArgumentException.class, () -> CharacterImageEffect.from(Map.of("character.lavender.imageEffect", "pikupiku.lineboil",
        "character.lavender.imageEffect.fps", "NaN"), "lavender"));
  }
}

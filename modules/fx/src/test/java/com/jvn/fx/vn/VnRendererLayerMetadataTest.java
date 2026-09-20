package com.jvn.fx.vn;

import com.jvn.core.vn.VnCharacter;
import com.jvn.core.vn.VnCharacterSceneAccessor;
import javafx.scene.canvas.Canvas;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class VnRendererLayerMetadataTest {
  @Test void cachedTopologyStillResolvesNewMovingAndReplacedProxies() throws Exception {
    VnRenderer renderer = new VnRenderer(new Canvas(64, 64).getGraphicsContext2D());
    VnCharacter character = character("body.png");
    var accessor = new VnCharacterSceneAccessor();
    renderer.setTimelineAccessor(accessor);
    List<?> layers = layers(renderer, character, "hero");
    Object body = layers.getFirst();
    Method resolve = VnRenderer.class.getDeclaredMethod("resolveLayerTransforms", body.getClass(), Map.class);
    resolve.setAccessible(true);
    assertTrue(((List<?>) resolve.invoke(renderer, body, Map.of())).isEmpty());

    var proxy = accessor.findEntity("hero_body");
    proxy.setPosition(12, 34);
    List<?> transforms = (List<?>) resolve.invoke(renderer, body, Map.of());
    Method getProxy = transforms.getFirst().getClass().getDeclaredMethod("proxy");
    getProxy.setAccessible(true);
    assertSame(proxy, getProxy.invoke(transforms.getFirst()));
    assertSame(layers, layers(renderer, character, "hero"), "Warm frames reuse immutable topology");
    proxy.setPosition(56, 78);
    assertEquals(56, ((com.jvn.core.scene2d.Entity2D) getProxy.invoke(transforms.getFirst())).getX());

    accessor.clear();
    assertTrue(((List<?>) resolve.invoke(renderer, body, Map.of())).isEmpty());
    var replacement = accessor.findEntity("hero_body");
    var replacementTransforms = (List<?>) resolve.invoke(renderer, body, Map.of());
    assertSame(replacement, getProxy.invoke(replacementTransforms.getFirst()));
  }

  @Test void reloadedDefinitionsSlotsAndCacheClearsDoNotReuseStaleMetadata() throws Exception {
    VnRenderer renderer = new VnRenderer(new Canvas(64, 64).getGraphicsContext2D());
    VnCharacter original = character("old.png");
    List<?> first = layers(renderer, original, "hero");
    assertNotSame(first, layers(renderer, character("new.png"), "hero"));
    assertNotSame(first, layers(renderer, original, "other_slot"));
    renderer.clearCache();
    assertNotSame(first, layers(renderer, original, "hero"));
  }

  private static VnCharacter character(String body) {
    return VnCharacter.builder("hero").addLayer("body", body).addLayer("eyes", "eyes.png")
        .addExpression("neutral", body + "|eyes.png", List.of("body", "eyes")).build();
  }

  private static List<?> layers(VnRenderer renderer, VnCharacter character, String slot) throws Exception {
    Method method = VnRenderer.class.getDeclaredMethod("spriteLayers", VnCharacter.class, String.class, String.class, List.class);
    method.setAccessible(true);
    return (List<?>) method.invoke(renderer, character, "neutral", slot,
        VnRenderer.parseLayerPaths(character.getExpressionPath("neutral")));
  }
}

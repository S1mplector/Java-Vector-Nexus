package com.jvn.scripting.jes;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.jvn.core.assets.AssetCatalog;
import com.jvn.core.assets.AssetType;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.CharacterEntity2D;
import com.jvn.core.scene2d.Label2D;
import com.jvn.core.scene2d.Panel2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.Sprite2D;
import com.jvn.core.scene2d.SpriteSheet;
import com.jvn.core.scene2d.TileMap2D;
import com.jvn.scripting.jes.ast.JesAst;
import com.jvn.scripting.jes.runtime.Button2D;
import com.jvn.scripting.jes.runtime.Ai2D;
import com.jvn.scripting.jes.runtime.Equipment;
import com.jvn.scripting.jes.runtime.Inventory;
import com.jvn.scripting.jes.runtime.Item;
import com.jvn.scripting.jes.runtime.JesScene2D;
import com.jvn.scripting.jes.runtime.PhysicsBodyEntity2D;
import com.jvn.scripting.jes.runtime.Slider2D;
import com.jvn.scripting.jes.runtime.Stats;

public class JesLoader {
  public static JesScene2D load(InputStream in) throws Exception {
    List<JesToken> toks = JesTokenizer.tokenize(in);
    JesAst.Program prog = new JesParser(toks).parseProgram();
    if (prog.scenes.isEmpty()) throw new IllegalArgumentException("No scene defined");
    JesAst.SceneDecl s = prog.scenes.get(0);
    return buildScene(s);
  }

  public static JesScene2D load(String code) throws Exception {
    java.io.InputStream in = new java.io.ByteArrayInputStream(
      (code == null ? "" : code).getBytes(java.nio.charset.StandardCharsets.UTF_8)
    );
    return load(in);
  }

  /**
   * Load and merge multiple JES scripts into a single scene. Scenes are merged in order:
   * items/tilesets/maps/entities/bindings/timelines are appended.
   */
  public static JesScene2D loadMerged(List<InputStream> inputs) throws Exception {
    if (inputs == null || inputs.isEmpty()) throw new IllegalArgumentException("No JES inputs provided");
    JesAst.SceneDecl base = null;
    for (InputStream in : inputs) {
      if (in == null) continue;
      List<JesToken> toks = JesTokenizer.tokenize(in);
      JesAst.Program prog = new JesParser(toks).parseProgram();
      if (prog.scenes.isEmpty()) continue;
      JesAst.SceneDecl s = prog.scenes.get(0);
      if (base == null) {
        base = s;
      } else {
        mergeScenes(base, s);
      }
    }
    if (base == null) throw new IllegalArgumentException("No scene defined across merged inputs");
    return buildScene(base);
  }

  private static void mergeScenes(JesAst.SceneDecl base, JesAst.SceneDecl extra) {
    if (base == null || extra == null) return;
    base.items.addAll(extra.items);
    base.tilesets.addAll(extra.tilesets);
    base.maps.addAll(extra.maps);
    base.entities.addAll(extra.entities);
    base.bindings.addAll(extra.bindings);
    base.timeline.addAll(extra.timeline);
    // Keep base name; ignore extra name
  }

  private static JesScene2D buildScene(JesAst.SceneDecl s) {
    JesScene2D scene = new JesScene2D();

    // Build item database
    Map<String, Item> items = new HashMap<>();
    for (JesAst.ItemDecl it : s.items) {
      if (it == null || it.id == null) continue;
      if (items.containsKey(it.id)) continue;
      Item item = new Item();
      item.setId(it.id);
      item.getProps().putAll(it.props);
      items.put(it.id, item);
    }
    for (Item item : items.values()) {
      scene.registerItem(item);
    }

    // Build tileset lookup
    Map<String, JesAst.TilesetDecl> tilesets = new HashMap<>();
    for (JesAst.TilesetDecl t : s.tilesets) {
      if (t != null && t.name != null) tilesets.put(t.name, t);
    }

    // Build tilemap layers from all maps, and expose grid size from the first
    final double[] mapTileW = new double[]{1.0};
    final double[] mapTileH = new double[]{1.0};
    final boolean[] hasMapInfo = new boolean[]{false};
    if (!s.maps.isEmpty() && !tilesets.isEmpty()) {
      for (JesAst.MapDecl m : s.maps) {
        if (m == null) continue;
        String tilesetName = str(m.props, "tileset", null);
        JesAst.TilesetDecl ts = tilesets.get(tilesetName);
        if (ts == null) continue;
        String image = str(ts.props, "image", null);
        int tileW = (int) num(ts.props, "tileW", 16);
        int tileH = (int) num(ts.props, "tileH", 16);
        int cols = (int) num(ts.props, "cols", 8);
        SpriteSheet sheet = new SpriteSheet(image, tileW, tileH, cols);

        int mapCols = (int) num(m.props, "width", 10);
        int mapRows = (int) num(m.props, "height", 10);
        double drawTileW = num(m.props, "tileW", tileW);
        double drawTileH = num(m.props, "tileH", tileH);

        // Use the first valid map/tileset to define grid size for character placement and movement
        if (!hasMapInfo[0]) {
          mapTileW[0] = drawTileW;
          mapTileH[0] = drawTileH;
          hasMapInfo[0] = true;
        }

        for (JesAst.MapLayerDecl l : m.layers) {
          if (l == null) continue;
          TileMap2D tilemap = new TileMap2D(sheet, mapCols, mapRows, drawTileW, drawTileH);
          String dataPath = str(l.props, "data", null);
          loadLayerIntoTilemap(tilemap, dataPath);
          scene.add(tilemap);
          if (bool(l.props, "collision", false)) {
            tilemap.buildStaticColliders(scene.getWorld());
            scene.addCollisionTilemap(tilemap);
          }
          String triggerCall = str(l.props, "triggerCall", null);
          if (triggerCall == null || triggerCall.isBlank()) {
            triggerCall = str(l.props, "call", null);
          }
          if (triggerCall != null && !triggerCall.isBlank()) {
            scene.addTriggerLayer(m.name, tilemap, triggerCall, l.props);
          }
        }
      }
    }
    scene.setGridSize(mapTileW[0], mapTileH[0]);

    for (JesAst.InputBinding b : s.bindings) {
      scene.addBinding(b.key, b.action, b.props);
    }
    scene.setTimeline(s.timeline);
    for (JesAst.EntityDecl e : s.entities) {
      e.components.forEach(c -> {
        switch (c.type) {
          case "Panel2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 1);
            double h = num(c, "h", 1);
            Panel2D p = new Panel2D(w, h);
            Object fill = c.props.get("fill");
            if (fill instanceof double[] arr) p.setFill(arr[0], arr[1], arr[2], arr[3]);
            p.setPosition(x, y);
            scene.add(p);
            scene.registerEntity(e.name, p);
          }
          case "Sprite2D" -> {
            String image = str(c, "image", null);
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 64);
            double h = num(c, "h", 64);
            double alpha = num(c, "alpha", 1.0);
            double ox = num(c, "originX", 0.0);
            double oy = num(c, "originY", 0.0);
            Sprite2D sp;
            boolean hasRegion = has(c, "sx") || has(c, "sw");
            if (hasRegion) {
              double sx = num(c, "sx", 0);
              double sy = num(c, "sy", 0);
              double sw = num(c, "sw", w);
              double sh = num(c, "sh", h);
              double dw = num(c, "dw", w);
              double dh = num(c, "dh", h);
              sp = new Sprite2D(image, dw, dh).region(image, sx, sy, sw, sh, dw, dh);
            } else {
              sp = new Sprite2D(image, w, h);
            }
            sp.setPosition(x, y);
            sp.setAlpha(alpha);
            sp.setOrigin(ox, oy);
            scene.add(sp);
            scene.registerEntity(e.name, sp);
          }
          case "Label2D" -> {
            String text = str(c, "text", "");
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double size = num(c, "size", 16);
            boolean bold = bool(c, "bold", false);
            Label2D lbl = new Label2D(text);
            lbl.setPosition(x, y);
            Object fill = c.props.get("color");
            if (fill instanceof double[] arr) lbl.setColor(arr[0], arr[1], arr[2], arr[3]);
            lbl.setFont("SansSerif", size, bold);
            String align = str(c, "align", "LEFT");
            try { lbl.setAlign(Label2D.Align.valueOf(align.toUpperCase())); } catch (Exception ignored) { // reason: unrecognized value from script; silently skipped
            }
            scene.add(lbl);
            scene.registerEntity(e.name, lbl);
          }
          case "ParticleEmitter2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double emissionRate = num(c, "emissionRate", 10);
            double minLife = num(c, "minLife", 1.0);
            double maxLife = num(c, "maxLife", 3.0);
            double minSize = num(c, "minSize", 2.0);
            double maxSize = num(c, "maxSize", 8.0);
            double endSizeScale = num(c, "endSizeScale", 0.1);
            double minSpeed = num(c, "minSpeed", 50);
            double maxSpeed = num(c, "maxSpeed", 150);
            double minAngle = num(c, "minAngle", 0);
            double maxAngle = num(c, "maxAngle", 360);
            double gravityY = num(c, "gravityY", 100);
            String texture = str(c, "texture", null);
            boolean additive = bool(c, "additive", true);
            ParticleEmitter2D emitter = new ParticleEmitter2D();
            emitter.setPosition(x, y);
            emitter.setEmissionRate(emissionRate);
            emitter.setLifeRange(minLife, maxLife);
            emitter.setSizeRange(minSize, maxSize, endSizeScale);
            emitter.setSpeedRange(minSpeed, maxSpeed);
            emitter.setAngleRange(minAngle, maxAngle);
            emitter.setGravity(gravityY);
            if (texture != null) emitter.setTexture(texture);
            emitter.setAdditive(additive);
            Object startColor = c.props.get("startColor"); if (startColor instanceof double[] arr1) emitter.setStartColor(arr1[0], arr1[1], arr1[2], arr1[3]);
            Object endColor = c.props.get("endColor"); if (endColor instanceof double[] arr2) emitter.setEndColor(arr2[0], arr2[1], arr2[2], arr2[3]);
            scene.add(emitter);
            scene.registerEntity(e.name, emitter);
          }
          case "PhysicsBody2D" -> {
            String shape = str(c, "shape", "circle").toLowerCase();
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double mass = num(c, "mass", 1);
            double restitution = num(c, "restitution", 0.2);
            boolean stat = bool(c, "static", false);
            boolean sensor = bool(c, "sensor", false);
            String onTrigger = str(c, "onTrigger", null);
            RigidBody2D body;
            if (shape.equals("box")) {
              double w = num(c, "w", 1);
              double h = num(c, "h", 1);
              body = RigidBody2D.box(x, y, w, h);
            } else {
              double r = num(c, "r", 0.5);
              body = RigidBody2D.circle(x, y, r);
            }
            body.setMass(mass);
            body.setRestitution(restitution);
            body.setStatic(stat);
            body.setSensor(sensor);
            double vx = num(c, "vx", 0); double vy = num(c, "vy", 0); body.setVelocity(vx, vy);
            scene.getWorld().addBody(body);
            PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
            Object fill = c.props.get("color"); if (fill instanceof double[] arr) vis.setColor(arr[0], arr[1], arr[2], arr[3]);
            scene.add(vis);
            scene.registerEntity(e.name, vis);
            scene.registerPhysicsEntity(e.name, body, onTrigger);
          }
          case "Character2D" -> {
            String image = str(c, "spriteSheet", null);
            int frameW = (int) num(c, "frameW", 16);
            int frameH = (int) num(c, "frameH", 16);
            int cols = (int) num(c, "cols", 8);
            double drawW = num(c, "drawW", frameW);
            double drawH = num(c, "drawH", frameH);
            SpriteSheet sheet = new SpriteSheet(image, frameW, frameH, cols);
            CharacterEntity2D ch = new CharacterEntity2D(sheet, drawW, drawH);

            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            if (hasMapInfo[0] && (has(c, "startTileX") || has(c, "startTileY"))) {
              int tx = (int) num(c, "startTileX", 0);
              int ty = (int) num(c, "startTileY", 0);
              x = tx * mapTileW[0];
              y = ty * mapTileH[0];
            }
            ch.setPosition(x, y);

            double speed = num(c, "speed", 80.0);
            ch.setSpeed(speed);

            double ox = num(c, "originX", 0.5);
            double oy = num(c, "originY", 1.0);
            ch.setOrigin(ox, oy);

            String animSpec = str(c, "animations", null);
            ch.setAnimations(CharacterEntity2D.parseAnimations(animSpec));
            String startAnim = str(c, "startAnim", null);
            if (startAnim != null) ch.setCurrentAnimation(startAnim);

            String dialogueId = str(c, "dialogueId", null);
            if (dialogueId != null && !dialogueId.isBlank()) ch.setDialogueId(dialogueId);

            double z = num(c, "z", 0.0);
            ch.setZ(z);

            scene.add(ch);
            scene.registerEntity(e.name, ch);
            boolean controllable = bool(c, "controllable", false);
            if (controllable) scene.setPlayerName(e.name);
          }
          case "Stats" -> {
            Stats stats = new Stats();
            double maxHp = num(c, "maxHp", 0);
            double hp = num(c, "hp", maxHp);
            double maxMp = num(c, "maxMp", 0);
            double mp = num(c, "mp", maxMp);
            double atk = num(c, "atk", 0);
            double def = num(c, "def", 0);
            double speed = num(c, "speed", 0);
            String deathCall = str(c, "onDeathCall", null);
            boolean removeOnDeath = bool(c, "removeOnDeath", false);
            stats.setMaxHp(maxHp);
            stats.setHp(hp);
            stats.setMaxMp(maxMp);
            stats.setMp(mp);
            stats.setAtk(atk);
            stats.setDef(def);
            stats.setSpeed(speed);
            stats.setDeathCall(deathCall);
            stats.setRemoveOnDeath(removeOnDeath);
            scene.setStats(e.name, stats);
          }
          case "Inventory" -> {
            Inventory inv = new Inventory();
            int slots = (int) num(c, "slots", 0);
            inv.setSlots(slots);
            String itemsStr = str(c, "items", null);
            if (itemsStr != null) {
              String[] parts = itemsStr.split(",");
              for (String part : parts) {
                String token = part.trim();
                if (token.isEmpty()) continue;
                String id = token;
                int count = 1;
                int star = token.indexOf('*');
                if (star >= 0) {
                  id = token.substring(0, star).trim();
                  String cntStr = token.substring(star + 1).trim();
                  try { count = Integer.parseInt(cntStr); } catch (NumberFormatException ignored) { // reason: unrecognized value from script; silently skipped
                  }
                  if (count <= 0) count = 1;
                }
                if (id.isEmpty()) continue;
                Item def = items.get(id);
                int maxStack = def == null ? 0 : (int) num(def.getProps(), "maxStack", 0);
                inv.addBounded(id, count, maxStack);
              }
            }
            scene.setInventory(e.name, inv);
          }
          case "Equipment" -> {
            Equipment eq = new Equipment();
            for (Map.Entry<String,Object> entry : c.props.entrySet()) {
              String slot = entry.getKey();
              Object v = entry.getValue();
              if (!(v instanceof String id)) continue;
              if (slot == null || slot.isBlank()) continue;
              if (id == null || id.isBlank()) continue;
              eq.set(slot, id);
            }
            scene.setEquipment(e.name, eq);
          }
          case "Ai2D" -> {
            Ai2D ai = new Ai2D();
            String type = str(c, "type", "chase");
            String target = str(c, "target", null);
            double aggroRange = num(c, "aggroRange", 0);
            double attackRange = num(c, "attackRange", 0);
            double attackIntervalMs = num(c, "attackIntervalMs", 1000.0);
            double attackAmount = num(c, "attackAmount", 0);
            double moveSpeed = num(c, "moveSpeed", 0);
            double patrolRadius = num(c, "patrolRadius", 0);
            double patrolIntervalMs = num(c, "patrolIntervalMs", 1500.0);
            boolean requiresLos = bool(c, "requiresLineOfSight", false);
            double guardRadius = num(c, "guardRadius", 0);
            double fleeDistance = num(c, "fleeDistance", 0);
            ai.setType(type);
            ai.setTarget(target);
            ai.setAggroRange(aggroRange);
            ai.setAttackRange(attackRange);
            ai.setAttackIntervalMs(attackIntervalMs);
            ai.setAttackAmount(attackAmount);
            ai.setMoveSpeed(moveSpeed);
            ai.setPatrolRadius(patrolRadius);
            ai.setPatrolIntervalMs(patrolIntervalMs);
            ai.setRequiresLineOfSight(requiresLos);
            ai.setGuardRadius(guardRadius);
            ai.setFleeDistance(fleeDistance);
            scene.setAi(e.name, ai);
          }
          case "Button2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 100);
            double h = num(c, "h", 32);
            Button2D btn = new Button2D(w, h);
            btn.setPosition(x, y);
            btn.setText(str(c, "text", ""));
            btn.setCall(str(c, "call", null));
            Object normal = c.props.get("normal");
            Object hover = c.props.get("hover");
            Object pressed = c.props.get("pressed");
            Object textColor = c.props.get("textColor");
            btn.setColors(toColor(normal), toColor(hover), toColor(pressed), toColor(textColor));
            double fs = num(c, "fontSize", 14);
            btn.setFontSize(fs);
            scene.add(btn);
            scene.registerEntity(e.name, btn);
            scene.addButton(btn);
          }
          case "Slider2D" -> {
            double x = num(c, "x", 0);
            double y = num(c, "y", 0);
            double w = num(c, "w", 120);
            double h = num(c, "h", 20);
            Slider2D sl = new Slider2D(w, h);
            sl.setPosition(x, y);
            sl.setRange(num(c, "min", 0), num(c, "max", 1));
            sl.setValue(num(c, "value", 0));
            sl.setCall(str(c, "call", null));
            Object track = c.props.get("trackColor");
            Object fill = c.props.get("fillColor");
            Object knob = c.props.get("knobColor");
            sl.setColors(toColor(track), toColor(fill), toColor(knob));
            scene.add(sl);
            scene.registerEntity(e.name, sl);
            scene.addSlider(sl);
          }
          default -> {}
        }
      });
    }
    return scene;
  }

  private static double num(JesAst.ComponentDecl c, String key, double def) {
    Object v = c.props.get(key);
    return v instanceof Number n ? n.doubleValue() : def;
  }
  private static String str(JesAst.ComponentDecl c, String key, String def) {
    Object v = c.props.get(key);
    return v instanceof String s ? s : def;
  }
  private static boolean bool(JesAst.ComponentDecl c, String key, boolean def) {
    Object v = c.props.get(key);
    return v instanceof Boolean b ? b : def;
  }
  private static boolean has(JesAst.ComponentDecl c, String key) { return c.props.containsKey(key); }

  private static double num(Map<String,Object> props, String key, double def) {
    Object v = props.get(key);
    return v instanceof Number n ? n.doubleValue() : def;
  }

  private static String str(Map<String,Object> props, String key, String def) {
    Object v = props.get(key);
    return v instanceof String s ? s : def;
  }

  private static boolean bool(Map<String,Object> props, String key, boolean def) {
    Object v = props.get(key);
    return v instanceof Boolean b ? b : def;
  }
  private static double[] toColor(Object o) {
    if (o instanceof double[] arr && arr.length >= 4) return arr;
    return null;
  }

  private static void loadLayerIntoTilemap(TileMap2D tm, String path) {
    if (tm == null || path == null || path.isBlank()) return;
    AssetCatalog assets = new AssetCatalog();
    // Use the active project manager first; retain direct classpath paths for
    // older bundled JES maps.
    try (InputStream in = assets.exists(AssetType.OTHER, path)
        ? assets.open(AssetType.OTHER, path)
        : JesLoader.class.getClassLoader().getResourceAsStream(path)) {
      if (in == null) return;
      try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String line;
        int y = 0;
        int rows = tm.getRows();
        int cols = tm.getCols();
        while ((line = br.readLine()) != null && y < rows) {
          String[] parts = line.split(",");
          for (int x = 0; x < parts.length && x < cols; x++) {
            String sVal = parts[x].trim();
            if (sVal.isEmpty()) continue;
            try {
              int idx = Integer.parseInt(sVal);
              tm.setTile(x, y, idx);
            } catch (NumberFormatException ignored) { // reason: unrecognized value from script; silently skipped
            }
          }
          y++;
        }
      }
    } catch (Exception ignored) {
      // reason: closing already-closed resource
    }
  }
}

package com.jvn.scripting.jes.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import com.jvn.core.animation.Easing;
import com.jvn.core.animation.EasingSpec;
import com.jvn.core.input.Input;
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;
import com.jvn.core.scene2d.Blitter2D;
import com.jvn.core.scene2d.CharacterEntity2D;
import com.jvn.core.scene2d.Entity2D;
import com.jvn.core.scene2d.ParticleEmitter2D;
import com.jvn.core.scene2d.Scene2DBase;
import com.jvn.core.scene2d.TileMap2D;
import com.jvn.scripting.jes.ast.JesAst;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JesScene2D extends Scene2DBase {
  private static final Logger log = LoggerFactory.getLogger(JesScene2D.class);
  private final PhysicsWorld2D world = new PhysicsWorld2D();
  private boolean debug = false;
  private PhysicsDebugOverlay2D debugOverlay;
  private final List<Binding> bindings = new ArrayList<>();
  private final Map<String, Entity2D> named = new HashMap<>();
  private final Map<String, Entity2D> aliases = new HashMap<>();
  private final Map<String, Stats> statsByEntity = new HashMap<>();
  private final Map<String, Item> items = new HashMap<>();
  private final Map<String, Inventory> inventories = new HashMap<>();
  private final Map<String, Equipment> equipmentByEntity = new HashMap<>();
  private final Map<String, Ai2D> aiByEntity = new HashMap<>();
  private final Map<RigidBody2D, PhysicsInfo> physicsInfos = new HashMap<>();
  private final Map<String, RigidBody2D> bodyByName = new HashMap<>();
  private final Map<String, Double> scriptVars = new HashMap<>();
  private final Map<String, Consumer<Map<String,Object>>> callHandlers = new HashMap<>();
  private final Map<String, double[]> spawnPositions = new HashMap<>();
  private boolean paused = false;

  private List<JesAst.TimelineAction> timeline = new ArrayList<>();
  private int tlIndex = 0;
  private double tlElapsedMs = 0;
  private final Map<Integer, ActionRuntime> actionState = new HashMap<>();
  private BiConsumer<String, Map<String,Object>> actionHandler;

  private String playerName;
  private double gridW = 16.0;
  private double gridH = 16.0;
  private String playerFacing = "down";
  private final List<TileMap2D> collisionTilemaps = new ArrayList<>();
  private boolean continuousMovementEnabled = false;
  private final List<Button2D> buttons = new ArrayList<>();
  private final List<Slider2D> sliders = new ArrayList<>();
  private String cameraFollowTarget;
  private double cameraFollowLerp = 0.2;
  private double cameraDeadZoneW = 0;
  private double cameraDeadZoneH = 0;
  private final List<RunningAsyncAction> asyncActions = new ArrayList<>();
  private double heroVx;
  private double heroVy;
  private double heroAccel = 400.0;
  private double heroDrag = 8.0;
  private double cameraOffsetX;
  private double cameraOffsetY;
  private final java.util.Set<String> triggeredEvents = new java.util.HashSet<>();
  private final Map<String,Integer> labelIndex = new java.util.HashMap<>();
  private static class TriggerLayer {
    final TileMap2D tilemap;
    final String call;
    final Map<String,Object> props;
    final String mapName;
    TriggerLayer(String mapName, TileMap2D tilemap, String call, Map<String,Object> props) {
      this.mapName = mapName;
      this.tilemap = tilemap;
      this.call = call;
      this.props = props;
    }
  }
  private final List<TriggerLayer> triggerLayers = new ArrayList<>();

  public static class Binding {
    public final String key;
    public final String action;
    public final Map<String,Object> props;
    public Binding(String key, String action, Map<String,Object> props) {
      this.key = key; this.action = action; this.props = props == null ? new HashMap<>() : props;
    }
  }
  private static class ActionRuntime {
    boolean started;
    double sx, sy, sRot, ssx, ssy;
    double sValue;
    EasingSpec easingSpec = EasingSpec.of(Easing.Type.LINEAR);
    Easing.Interpolation interpolation = Easing.Interpolation.TWEEN;
    double sAlpha;
    double sZoom;
    double elapsed;
  }
  private static class RunningAsyncAction {
    final JesAst.TimelineAction action;
    final ActionRuntime state;
    double elapsedMs;
    RunningAsyncAction(JesAst.TimelineAction action, ActionRuntime state) {
      this.action = action;
      this.state = state;
    }
  }
  private static class LoopRuntime extends ActionRuntime {
    int remaining;
    String untilEvent;
    int childIndex;
    final Map<Integer, ActionRuntime> childStates = new HashMap<>();
  }
  private static class PhysicsInfo {
    final String name;
    final String onTrigger;
    PhysicsInfo(String name, String onTrigger) { this.name = name; this.onTrigger = onTrigger; }
  }

  public PhysicsWorld2D getWorld() { return world; }
  public void addCollisionTilemap(TileMap2D tm) { if (tm != null) collisionTilemaps.add(tm); }
  public void addTriggerLayer(TileMap2D tm, String call, Map<String,Object> props) {
    addTriggerLayer(null, tm, call, props);
  }
  public void addTriggerLayer(String mapName, TileMap2D tm, String call, Map<String,Object> props) {
    if (tm == null || call == null || call.isBlank()) return;
    Map<String,Object> copy = (props == null) ? new HashMap<>() : new HashMap<>(props);
    triggerLayers.add(new TriggerLayer(mapName, tm, call, copy));
  }
  public void setDebug(boolean d) { this.debug = d; }
  public void addBinding(String key, String action, Map<String,Object> props) { bindings.add(new Binding(key, action, props)); }
  public void registerEntity(String name, Entity2D e) {
    if (name != null && !name.isBlank() && e != null
        && !named.containsKey(name) && !aliases.containsKey(name)) {
      named.put(name, e);
      spawnPositions.put(name, new double[]{ e.getX(), e.getY() });
    }
  }
  public void registerEntityAlias(String name, Entity2D e) {
    if (name != null && !name.isBlank() && e != null
        && !named.containsKey(name) && !aliases.containsKey(name)) {
      aliases.put(name, e);
      spawnPositions.put(name, new double[]{ e.getX(), e.getY() });
    }
  }
  public void registerPhysicsEntity(String name, RigidBody2D body, String onTrigger) {
    if (name == null || name.isBlank() || body == null) return;
    physicsInfos.put(body, new PhysicsInfo(name, onTrigger));
    bodyByName.put(name, body);
    world.setSensorListener(this::handleSensorTrigger);
  }

  private void handleSensorTrigger(RigidBody2D sensor, RigidBody2D other) {
    PhysicsInfo info = physicsInfos.get(sensor);
    PhysicsInfo otherInfo = physicsInfos.get(other);
    String call = info != null ? info.onTrigger : null;
    Map<String,Object> props = new HashMap<>();
    if (info != null) props.put("sensor", info.name);
    if (otherInfo != null) props.put("other", otherInfo.name);
    if (call != null && !call.isBlank()) {
      invokeCall(call, props);
    } else {
      invokeCall("sensorHit", props);
    }
  }
  public void setTimeline(List<JesAst.TimelineAction> tl) {
    this.timeline = tl == null ? new ArrayList<>() : new ArrayList<>(tl);
    this.tlIndex = 0;
    this.tlElapsedMs = 0;
    this.actionState.clear();
    this.asyncActions.clear();
    this.labelIndex.clear();
    indexLabels(this.timeline);
  }
  private void indexLabels(List<JesAst.TimelineAction> list) {
    if (list == null) return;
    for (int idx = 0; idx < list.size(); idx++) {
      JesAst.TimelineAction a = list.get(idx);
      if (a == null) continue;
      if ("label".equals(a.type) && a.target != null && !a.target.isBlank()) {
        labelIndex.putIfAbsent(a.target, idx);
      }
    }
  }
  public java.util.Set<String> names() { return named.keySet(); }
  public Entity2D find(String name) {
    Entity2D entity = named.get(name);
    return entity != null ? entity : aliases.get(name);
  }
  public Map<String, Entity2D> exportNamed() { return java.util.Collections.unmodifiableMap(new java.util.HashMap<>(named)); }
  public java.util.List<Binding> exportBindings() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(bindings)); }
  public java.util.List<JesAst.TimelineAction> exportTimeline() { return java.util.Collections.unmodifiableList(new java.util.ArrayList<>(timeline)); }
  public Stats getStats(String name) { return name == null ? null : statsByEntity.get(name); }
  public void setStats(String name, Stats stats) { if (name != null && stats != null) statsByEntity.put(name, stats); }
  public Item getItem(String id) { return id == null ? null : items.get(id); }
  public void registerItem(Item item) {
    if (item == null) return;
    String id = item.getId();
    if (id == null || id.isBlank()) return;
    if (!items.containsKey(id)) items.put(id, item);
  }
  public Inventory getInventory(String name) { return name == null ? null : inventories.get(name); }
  public void setInventory(String name, Inventory inv) { if (name != null && inv != null) inventories.put(name, inv); }
  public boolean addItemToInventory(String name, String itemId, int count) {
    if (name == null || name.isBlank()) return false;
    if (itemId == null || itemId.isBlank()) return false;
    if (count <= 0) return false;
    Inventory inv = inventories.get(name);
    if (inv == null) {
      inv = new Inventory();
      inventories.put(name, inv);
    }
    inv.add(itemId, count);
    return true;
  }
  public boolean giveItem(String name, String itemId, int count) {
    if (name == null || name.isBlank()) return false;
    if (itemId == null || itemId.isBlank()) return false;
    if (count <= 0) return false;
    Item item = items.get(itemId);
    if (item == null) return false;
    Inventory inv = inventories.get(name);
    if (inv == null) {
      inv = new Inventory();
      inventories.put(name, inv);
    }
    int maxStack = getMaxStackForItem(item);
    return inv.addBounded(itemId, count, maxStack);
  }
  public boolean takeItem(String name, String itemId, int count) {
    if (name == null || name.isBlank()) return false;
    if (itemId == null || itemId.isBlank()) return false;
    if (count <= 0) return false;
    Inventory inv = inventories.get(name);
    if (inv == null) return false;
    return inv.remove(itemId, count);
  }
  public Equipment getEquipment(String name) { return name == null ? null : equipmentByEntity.get(name); }
  public void setEquipment(String name, Equipment eq) {
    if (name == null || name.isBlank() || eq == null) return;
    equipmentByEntity.put(name, eq);
  }
  public void addButton(Button2D btn) { if (btn != null) buttons.add(btn); }
  public void addSlider(Slider2D s) { if (s != null) sliders.add(s); }
  public Ai2D getAi(String name) { return name == null ? null : aiByEntity.get(name); }
  public void setAi(String name, Ai2D ai) {
    if (name == null || name.isBlank() || ai == null) return;
    aiByEntity.put(name, ai);
  }
  public void registerCall(String name, Consumer<Map<String,Object>> handler) { if (name != null && !name.isBlank() && handler != null) callHandlers.put(name, handler); }
  public void setActionHandler(BiConsumer<String, Map<String,Object>> handler) { this.actionHandler = handler; }
  public void invokeCall(String name, Map<String,Object> props) {
    if (name == null || name.isBlank()) return;
    Map<String,Object> actualProps = (props == null) ? new HashMap<>() : new HashMap<>(props);
    // Built-in map warp helper: can be triggered directly from triggers using triggerCall: "warpMap"
    if ("warpMap".equals(name)) {
      warpMap(actualProps);
    } else if ("useItem".equals(name)) {
      useItem(actualProps);
    } else if ("giveItem".equals(name)) {
      handleGiveItem(actualProps);
    } else if ("takeItem".equals(name)) {
      handleTakeItem(actualProps);
    } else if ("equipItem".equals(name)) {
      handleEquipItem(actualProps);
    } else if ("unequipItem".equals(name)) {
      handleUnequipItem(actualProps);
    } else if ("attack".equals(name)) {
      handleAttack(actualProps);
    } else if ("pocketBall".equals(name)) {
      handlePocket(actualProps);
    } else if ("resetBalls".equals(name)) {
      resetToSpawn();
    } else if ("setLabelText".equals(name)) {
      setLabelText(actualProps);
    } else if ("removeEntity".equals(name)) {
      String target = toStr(actualProps.get("target"), null);
      if (target != null) removeEntity(target);
    }
    if (name != null) {
      triggeredEvents.add(name);
    }
    Consumer<Map<String,Object>> h = callHandlers.get(name);
    if (h != null) {
      try { h.accept(actualProps); } catch (Exception ignored) {
        // reason: user-registered callback must not crash the animation loop
        log.warn("JesScene2D: call handler '{}' threw an exception", name, ignored);
      }
    } else if (actionHandler != null) {
      try { actionHandler.accept(name, actualProps); } catch (Exception ignored) {
        // reason: user-registered callback must not crash the animation loop
        log.warn("JesScene2D: action handler '{}' threw an exception", name, ignored);
      }
    }
  }
  public void setPlayerName(String name) { this.playerName = name; }
  public void setGridSize(double w, double h) { this.gridW = w; this.gridH = h; }
  public void setPlayerFacing(String facing) { if (facing != null && !facing.isBlank()) this.playerFacing = facing; }
  public String getPlayerFacing() { return playerFacing; }
  public void setContinuousMovementEnabled(boolean enabled) { this.continuousMovementEnabled = enabled; }
  public void setCameraFollow(String target, double lerp) {
    this.cameraFollowTarget = target;
    if (lerp > 0) this.cameraFollowLerp = lerp;
  }
  public void setCameraDeadZone(double w, double h) { this.cameraDeadZoneW = Math.max(0, w); this.cameraDeadZoneH = Math.max(0, h); }
  public void setPaused(boolean paused) { this.paused = paused; }
  public boolean isPaused() { return paused; }
  public boolean rename(String oldName, String newName) {
    if (oldName == null || newName == null || newName.isBlank() || oldName.equals(newName)) return false;
    if (!named.containsKey(oldName) || named.containsKey(newName)) return false;
    Entity2D e = named.remove(oldName);
    if (e == null) return false;
    named.put(newName, e);
    return true;
  }
  public boolean removeEntity(String name) {
    if (name == null) return false;
    Entity2D e = named.remove(name);
    if (e != null) {
      remove(e);
      RigidBody2D body = bodyByName.remove(name);
      if (body != null) {
        physicsInfos.remove(body);
        world.removeBody(body);
      }
      return true;
    }
    return false;
  }

  @Override
  public void update(long deltaMs) {
    if (paused) return;
    super.update(deltaMs);
    world.step(deltaMs);

    Input in = getInput();
    if (in != null) {
      for (Binding b : bindings) {
        if (in.wasKeyPressed(b.key)) handleAction(b);
      }
      updateContinuousMovement(in, deltaMs);
      handleButtons(in);
      handleSliders(in);
    }

    updateAi(deltaMs);
    updateTimeline(deltaMs);
    updateAsyncActions(deltaMs);
    updateCameraFollow(deltaMs);
  }

  @Override
  public void render(Blitter2D b, double width, double height) {
    super.render(b, width, height);
    // Optional physics debug could be drawn here if needed
  }

  private void updateAsyncActions(long deltaMs) {
    if (asyncActions.isEmpty()) return;
    java.util.Iterator<RunningAsyncAction> it = asyncActions.iterator();
    while (it.hasNext()) {
      RunningAsyncAction ra = it.next();
      if (ra == null || ra.action == null) { it.remove(); continue; }
      if (processAsyncAction(ra.action, ra.state, deltaMs)) {
        it.remove();
      }
    }
  }

  private boolean processAsyncAction(JesAst.TimelineAction a, ActionRuntime st, long deltaMs) {
    if (a == null) return true;
    String type = a.type == null ? "" : a.type;
    switch (type) {
      case "wait" -> {
        st.elapsed += deltaMs;
        double ms = toNum(a.props.get("ms"), 0);
        return st.elapsed >= ms;
      }
      case "waitForCall" -> {
        String ev = toStr(a.props.get("name"), null);
        if (ev != null && triggeredEvents.remove(ev)) return true;
        return false;
      }
      case "call" -> {
        Consumer<Map<String,Object>> h = callHandlers.get(a.target);
        if (h != null) {
          try { h.accept(a.props == null ? java.util.Collections.emptyMap() : a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: timeline call handler '{}' threw an exception", a.target, ignored);
          }
        }
        return true;
      }
      case "event" -> {
        dispatchTimelineEvent(a);
        return true;
      }
      case "move", "walkToTile" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double tx;
        double ty;
        if ("walkToTile".equals(type) && gridW != 0 && gridH != 0 && (a.props.containsKey("tx") || a.props.containsKey("ty"))) {
          double txTile = toNum(a.props.get("tx"), e.getX() / gridW);
          double tyTile = toNum(a.props.get("ty"), e.getY() / gridH);
          tx = txTile * gridW;
          ty = tyTile * gridH;
        } else {
          tx = toNum(a.props.get("x"), e.getX());
          ty = toNum(a.props.get("y"), e.getY());
        }
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sx = e.getX();
          st.sy = e.getY();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        e.setPosition(st.sx + (tx - st.sx) * ep, st.sy + (ty - st.sy) * ep);
        return p >= 1.0;
      }
      case "depth" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double targetZ = toNum(a.props.get("z"), e.getZ());
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sValue = e.getZ();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        e.setZ(st.sValue + (targetZ - st.sValue) * ep);
        return p >= 1.0;
      }
      case "pivot" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double tox = toNum(a.props.get("ox"), getPivotX(e));
        double toy = toNum(a.props.get("oy"), getPivotY(e));
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sx = getPivotX(e);
          st.sy = getPivotY(e);
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        setPivot(e, st.sx + (tox - st.sx) * ep, st.sy + (toy - st.sy) * ep);
        return p >= 1.0;
      }
      case "rotate" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double tdeg = toNum(a.props.get("deg"), e.getRotationDeg());
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sRot = e.getRotationDeg();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        e.setRotationDeg(st.sRot + (tdeg - st.sRot) * ep);
        return p >= 1.0;
      }
      case "scale" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double tsx = toNum(a.props.get("sx"), e.getScaleX());
        double tsy = toNum(a.props.get("sy"), e.getScaleY());
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.ssx = e.getScaleX();
          st.ssy = e.getScaleY();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        e.setScale(st.ssx + (tsx - st.ssx) * ep, st.ssy + (tsy - st.ssy) * ep);
        return p >= 1.0;
      }
      case "fade" -> {
        Entity2D e = named.get(a.target);
        if (e == null) return true;
        double targetAlpha = toNum(a.props.get("alpha"), getAlpha(e));
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sAlpha = getAlpha(e);
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        double aVal = st.sAlpha + (targetAlpha - st.sAlpha) * ep;
        setAlpha(e, aVal);
        return p >= 1.0;
      }
      case "brightness", "exposure" -> {
        return processBrightnessAction(a, st, deltaMs);
      }
      case "visible" -> {
        Entity2D e = named.get(a.target);
        if (e != null) {
          boolean vis = toBool(a.props.get("value"), true);
          e.setVisible(vis);
        }
        return true;
      }
      case "property" -> {
        return processPropertyAction(a, st, deltaMs);
      }
      case "cameraMove" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) return true;
        double tx = toNum(a.props.get("x"), cam.getX());
        double ty = toNum(a.props.get("y"), cam.getY());
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        double nx = st.sx + (tx - st.sx) * ep;
        double ny = st.sy + (ty - st.sy) * ep;
        cam.setPosition(nx, ny);
        return p >= 1.0;
      }
      case "cameraZoom" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) return true;
        double tz = toNum(a.props.get("zoom"), cam.getZoom());
        double dur = toNum(a.props.get("dur"), 0);
        if (!st.started) {
          st.started = true;
          st.sZoom = cam.getZoom();
          configureTimelineEasing(st, a.props);
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double ep = applyTimelineEasing(st, p);
        double nz = st.sZoom + (tz - st.sZoom) * ep;
        cam.setZoom(nz);
        return p >= 1.0;
      }
      case "cameraShake" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) return true;
        double ampX = toNum(a.props.get("ampX"), 16);
        double ampY = toNum(a.props.get("ampY"), 16);
        double dur = toNum(a.props.get("dur"), 300);
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
        }
        st.elapsed += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
        double intensity = 1.0 - p;
        double ox = (Math.random() * 2.0 - 1.0) * ampX * intensity;
        double oy = (Math.random() * 2.0 - 1.0) * ampY * intensity;
        cam.setPosition(st.sx + ox, st.sy + oy);
        if (p >= 1.0) {
          cam.setPosition(st.sx, st.sy);
          return true;
        }
        return false;
      }
      case "damage" -> {
        String targetName = a.target;
        double amt = toNum(a.props.get("amount"), 0);
        String source = toStr(a.props.get("source"), null);
        applyDamage(targetName, amt, source);
        return true;
      }
      case "heal" -> {
        String targetName = a.target;
        double amt = toNum(a.props.get("amount"), 0);
        String source = toStr(a.props.get("source"), null);
        heal(targetName, amt, source);
        return true;
      }
      case "emitParticles" -> {
        Entity2D ent = named.get(a.target);
        if (ent instanceof ParticleEmitter2D pe) {
          int cnt = (int) toNum(a.props.get("count"), 10);
          if (cnt <= 0) cnt = 1;
          pe.burst(cnt);
        }
        return true;
      }
      case "playAudio" -> {
        if (actionHandler != null) {
          try { actionHandler.accept("playAudio", a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: playAudio action handler threw an exception", ignored);
          }
        }
        return true;
      }
      case "stopAudio" -> {
        if (actionHandler != null) {
          try { actionHandler.accept("stopAudio", a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: stopAudio action handler threw an exception", ignored);
          }
        }
        return true;
      }
      case "cameraFollow" -> {
        if (a.target != null) cameraFollowTarget = a.target;
        String tgt = toStr(a.props.get("target"), cameraFollowTarget);
        cameraFollowTarget = tgt;
        cameraFollowLerp = toNum(a.props.get("lerp"), cameraFollowLerp);
        cameraOffsetX = toNum(a.props.get("offsetX"), cameraOffsetX);
        cameraOffsetY = toNum(a.props.get("offsetY"), cameraOffsetY);
        cameraDeadZoneW = toNum(a.props.get("deadZoneW"), cameraDeadZoneW);
        cameraDeadZoneH = toNum(a.props.get("deadZoneH"), cameraDeadZoneH);
        return true;
      }
      case "setParallax" -> {
        Entity2D ent = named.get(a.target);
        if (ent != null) {
          double px = toNum(a.props.get("px"), ent.getParallaxX());
          double py = toNum(a.props.get("py"), ent.getParallaxY());
          ent.setParallax(px, py);
        }
        return true;
      }
      default -> { return true; }
    }
  }

  private void updateTimeline(long deltaMs) {
    if (timeline == null || tlIndex >= timeline.size()) return;
    JesAst.TimelineAction a = timeline.get(tlIndex);
    switch (a.type) {
      case "wait" -> {
        double ms = toNum(a.props.get("ms"), 0);
        tlElapsedMs += deltaMs;
        if (tlElapsedMs >= ms) { tlIndex++; tlElapsedMs = 0; }
      }
      case "waitForCall" -> {
        String ev = toStr(a.props.get("name"), null);
        if (ev != null && triggeredEvents.remove(ev)) {
          tlIndex++; tlElapsedMs = 0;
          if (deltaMs > 0) updateTimeline(deltaMs);
        }
      }
      case "call" -> {
        Consumer<Map<String,Object>> h = callHandlers.get(a.target);
        if (h != null) {
          try { h.accept(a.props == null ? java.util.Collections.emptyMap() : a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: sequential timeline call handler '{}' threw an exception", a.target, ignored);
          }
        }
        tlIndex++;
        tlElapsedMs = 0;
        if (deltaMs > 0) updateTimeline(deltaMs);
      }
      case "event" -> {
        dispatchTimelineEvent(a);
        tlIndex++;
        tlElapsedMs = 0;
        if (deltaMs > 0) updateTimeline(deltaMs);
      }
      case "move" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tx = toNum(a.props.get("x"), e.getX());
        double ty = toNum(a.props.get("y"), e.getY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.sx = e.getX(); 
          st.sy = e.getY();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        e.setPosition(st.sx + (tx - st.sx) * ep, st.sy + (ty - st.sy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "depth" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double targetZ = toNum(a.props.get("z"), e.getZ());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sValue = e.getZ();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        e.setZ(st.sValue + (targetZ - st.sValue) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "pivot" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tox = toNum(a.props.get("ox"), getPivotX(e));
        double toy = toNum(a.props.get("oy"), getPivotY(e));
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = getPivotX(e);
          st.sy = getPivotY(e);
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        setPivot(e, st.sx + (tox - st.sx) * ep, st.sy + (toy - st.sy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "walkToTile" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        if (gridW == 0 || gridH == 0) { tlIndex++; tlElapsedMs = 0; return; }
        double txTile = toNum(a.props.get("tx"), Double.NaN);
        double tyTile = toNum(a.props.get("ty"), Double.NaN);
        double tx;
        double ty;
        if (Double.isNaN(txTile) || Double.isNaN(tyTile)) {
          tx = toNum(a.props.get("x"), e.getX());
          ty = toNum(a.props.get("y"), e.getY());
        } else {
          tx = txTile * gridW;
          ty = tyTile * gridH;
        }
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = e.getX();
          st.sy = e.getY();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        e.setPosition(st.sx + (tx - st.sx) * ep, st.sy + (ty - st.sy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "rotate" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tdeg = toNum(a.props.get("deg"), e.getRotationDeg());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.sRot = e.getRotationDeg();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        e.setRotationDeg(st.sRot + (tdeg - st.sRot) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "scale" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tsx = toNum(a.props.get("sx"), e.getScaleX());
        double tsy = toNum(a.props.get("sy"), e.getScaleY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) { 
          st.started = true; 
          st.ssx = e.getScaleX(); 
          st.ssy = e.getScaleY();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        e.setScale(st.ssx + (tsx - st.ssx) * ep, st.ssy + (tsy - st.ssy) * ep);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "fade" -> {
        Entity2D e = named.get(a.target);
        if (e == null) { tlIndex++; tlElapsedMs = 0; return; }
        double targetAlpha = toNum(a.props.get("alpha"), getAlpha(e));
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sAlpha = getAlpha(e);
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        double aVal = st.sAlpha + (targetAlpha - st.sAlpha) * ep;
        setAlpha(e, aVal);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "brightness", "exposure" -> {
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        boolean done = processBrightnessAction(a, st, deltaMs);
        if (done) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "visible" -> {
        Entity2D e = named.get(a.target);
        if (e != null) {
          boolean vis = toBool(a.props.get("value"), true);
          e.setVisible(vis);
        }
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "property" -> {
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        boolean done = processPropertyAction(a, st, deltaMs);
        if (done) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "cameraMove" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tx = toNum(a.props.get("x"), cam.getX());
        double ty = toNum(a.props.get("y"), cam.getY());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        double nx = st.sx + (tx - st.sx) * ep;
        double ny = st.sy + (ty - st.sy) * ep;
        cam.setPosition(nx, ny);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "cameraZoom" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double tz = toNum(a.props.get("zoom"), cam.getZoom());
        double dur = toNum(a.props.get("dur"), 0);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sZoom = cam.getZoom();
          configureTimelineEasing(st, a.props);
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double ep = applyTimelineEasing(st, p);
        double nz = st.sZoom + (tz - st.sZoom) * ep;
        cam.setZoom(nz);
        if (p >= 1.0) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); }
      }
      case "cameraShake" -> {
        com.jvn.core.graphics.Camera2D cam = getCamera();
        if (cam == null) { tlIndex++; tlElapsedMs = 0; return; }
        double ampX = toNum(a.props.get("ampX"), 16);
        double ampY = toNum(a.props.get("ampY"), 16);
        double dur = toNum(a.props.get("dur"), 300);
        ActionRuntime st = actionState.computeIfAbsent(tlIndex, k -> new ActionRuntime());
        if (!st.started) {
          st.started = true;
          st.sx = cam.getX();
          st.sy = cam.getY();
        }
        tlElapsedMs += deltaMs;
        double p = (dur <= 0) ? 1.0 : Math.min(1.0, tlElapsedMs / dur);
        double intensity = 1.0 - p;
        double ox = (Math.random() * 2.0 - 1.0) * ampX * intensity;
        double oy = (Math.random() * 2.0 - 1.0) * ampY * intensity;
        cam.setPosition(st.sx + ox, st.sy + oy);
        if (p >= 1.0) {
          cam.setPosition(st.sx, st.sy);
          tlIndex++;
          tlElapsedMs = 0;
          actionState.remove(tlIndex-1);
        }
      }
      case "spawnCircle" -> {
        spawnCircle(a.props == null ? java.util.Collections.emptyMap() : a.props);
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "spawnBox" -> {
        spawnBox(a.props == null ? java.util.Collections.emptyMap() : a.props);
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "damage" -> {
        String targetName = a.target;
        double amt = toNum(a.props.get("amount"), 0);
        String source = toStr(a.props.get("source"), null);
        applyDamage(targetName, amt, source);
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "heal" -> {
        String targetName = a.target;
        double amt = toNum(a.props.get("amount"), 0);
        String source = toStr(a.props.get("source"), null);
        heal(targetName, amt, source);
        tlIndex++;
        tlElapsedMs = 0;
      }
      case "emitParticles" -> {
        Entity2D ent = named.get(a.target);
        if (ent instanceof ParticleEmitter2D pe) {
          int cnt = (int) toNum(a.props.get("count"), 10);
          if (cnt <= 0) cnt = 1;
          pe.burst(cnt);
        }
        tlIndex++; tlElapsedMs = 0;
      }
      case "playAudio" -> {
        if (actionHandler != null) {
          try { actionHandler.accept("playAudio", a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: playAudio sequential handler threw an exception", ignored);
          }
        }
        tlIndex++; tlElapsedMs = 0;
      }
      case "stopAudio" -> {
        if (actionHandler != null) {
          try { actionHandler.accept("stopAudio", a.props); } catch (Exception ignored) {
            // reason: user-registered callback must not crash the animation loop
            log.warn("JesScene2D: stopAudio sequential handler threw an exception", ignored);
          }
        }
        tlIndex++; tlElapsedMs = 0;
      }
      case "cameraFollow" -> {
        if (a.target != null) cameraFollowTarget = a.target;
        String tgt = toStr(a.props.get("target"), cameraFollowTarget);
        cameraFollowTarget = tgt;
        cameraFollowLerp = toNum(a.props.get("lerp"), cameraFollowLerp);
        cameraOffsetX = toNum(a.props.get("offsetX"), cameraOffsetX);
        cameraOffsetY = toNum(a.props.get("offsetY"), cameraOffsetY);
        tlIndex++; tlElapsedMs = 0;
      }
      case "setParallax" -> {
        Entity2D ent = named.get(a.target);
        if (ent != null) {
          double px = toNum(a.props.get("px"), ent.getParallaxX());
          double py = toNum(a.props.get("py"), ent.getParallaxY());
          ent.setParallax(px, py);
        }
        tlIndex++; tlElapsedMs = 0;
      }
      case "label" -> { tlIndex++; tlElapsedMs = 0; }
      case "jump" -> {
        String label = a.target;
        Integer idx = labelIndex.get(label);
        if (idx != null) {
          tlIndex = idx;
        } else {
          tlIndex++;
        }
        tlElapsedMs = 0;
      }
      case "parallel" -> {
        if (a.children != null && !a.children.isEmpty()) {
          for (JesAst.TimelineAction child : a.children) {
            if (child == null) continue;
            asyncActions.add(new RunningAsyncAction(child, new ActionRuntime()));
          }
        }
        tlIndex++; tlElapsedMs = 0;
      }
      case "loop" -> {
        LoopRuntime st = (LoopRuntime) actionState.computeIfAbsent(tlIndex, k -> {
          LoopRuntime lr = new LoopRuntime();
          lr.remaining = (int) Math.max(0, toNum(a.props.get("count"), 0));
          Object untilObj = a.props.get("until");
          if (untilObj instanceof String s) lr.untilEvent = s;
          return lr;
        });
        if (a.children == null || a.children.isEmpty()) { tlIndex++; tlElapsedMs = 0; actionState.remove(tlIndex-1); break; }
        boolean infinite = st.remaining <= 0 && st.untilEvent == null;
        while (true) {
          if (st.childIndex >= a.children.size()) {
            boolean finishedByEvent = st.untilEvent != null && triggeredEvents.remove(st.untilEvent);
            if (finishedByEvent) {
              actionState.remove(tlIndex);
              tlIndex++; tlElapsedMs = 0;
              break;
            }
            if (!infinite) {
              if (st.remaining > 0) st.remaining--;
              if (st.remaining <= 0) {
                actionState.remove(tlIndex);
                tlIndex++; tlElapsedMs = 0;
                break;
              }
            }
            st.childIndex = 0;
            st.childStates.clear();
            continue;
          }
          JesAst.TimelineAction child = a.children.get(st.childIndex);
          ActionRuntime cr = st.childStates.computeIfAbsent(st.childIndex, k -> new ActionRuntime());
          boolean done = processAsyncAction(child, cr, deltaMs);
          if (done) {
            st.childIndex++;
            continue;
          }
          break; // still running
        }
      }
      default -> { tlIndex++; tlElapsedMs = 0; }
    }
  }

  private void configureTimelineEasing(ActionRuntime state, Map<String,Object> props) {
    if (state == null) return;
    String easing = toStr(props != null ? props.get("easing") : null, "linear");
    state.easingSpec = EasingSpec.parseOrDefault(easing);
    String interpolation = toStr(props != null ? props.get("interp") : null, "tween");
    String normalized = interpolation == null ? "" : interpolation.trim().toLowerCase(java.util.Locale.ROOT).replace('-', '_');
    state.interpolation = switch (normalized) {
      case "hold", "step_end", "step_hold", "constant" -> Easing.Interpolation.HOLD;
      case "step", "step_start", "instant", "jump" -> Easing.Interpolation.STEP;
      default -> Easing.Interpolation.TWEEN;
    };
  }

  private double applyTimelineEasing(ActionRuntime state, double progress) {
    if (state == null) return Easing.apply(Easing.Type.LINEAR, progress);
    return Easing.applyInterpolation(state.easingSpec, state.interpolation, progress);
  }

  private boolean processPropertyAction(JesAst.TimelineAction a, ActionRuntime st, long deltaMs) {
    if (a == null || st == null) return true;
    String key = toStr(a.props.get("key"), null);
    if (key == null || key.isBlank()) return true;
    double targetValue = toNum(a.props.get("value"), Double.NaN);
    if (!Double.isFinite(targetValue)) return true;
    double dur = toNum(a.props.get("dur"), 0);
    String target = a.target;

    if (!st.started) {
      st.started = true;
      st.sValue = readTimelineCustomProperty(target, key);
      configureTimelineEasing(st, a.props);
    }
    st.elapsed += deltaMs;
    double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
    double ep = applyTimelineEasing(st, p);
    applyTimelineCustomProperty(target, key, st.sValue + (targetValue - st.sValue) * ep);
    return p >= 1.0;
  }

  private boolean processBrightnessAction(JesAst.TimelineAction a, ActionRuntime st, long deltaMs) {
    if (a == null || st == null) return true;
    Entity2D entity = named.get(a.target);
    if (entity == null) return true;
    double targetValue = toNum(
        firstPresent(a.props, "value", "brightness", "exposure"),
        entity.getBrightness());
    double dur = toNum(a.props.get("dur"), 0);
    if (!st.started) {
      st.started = true;
      st.sValue = entity.getBrightness();
      configureTimelineEasing(st, a.props);
    }
    st.elapsed += deltaMs;
    double p = (dur <= 0) ? 1.0 : Math.min(1.0, st.elapsed / dur);
    double ep = applyTimelineEasing(st, p);
    entity.setBrightness(st.sValue + (targetValue - st.sValue) * ep);
    return p >= 1.0;
  }

  private static Object firstPresent(Map<String,Object> values, String... keys) {
    if (values == null || keys == null) return null;
    for (String key : keys) {
      if (key != null && values.containsKey(key)) return values.get(key);
    }
    return null;
  }

  private double readTimelineCustomProperty(String target, String key) {
    if (key == null || key.isBlank()) return 0.0;
    if (target == null || target.isBlank() || "__camera__".equals(target)) {
      com.jvn.core.graphics.Camera2D cam = getCamera();
      return cam != null ? cam.readCustomProperty(key) : 0.0;
    }
    Entity2D entity = named.get(target);
    return entity != null ? entity.readCustomProperty(key) : 0.0;
  }

  private void applyTimelineCustomProperty(String target, String key, double value) {
    if (key == null || key.isBlank()) return;
    if (target == null || target.isBlank() || "__camera__".equals(target)) {
      com.jvn.core.graphics.Camera2D cam = getCamera();
      if (cam != null) cam.applyCustomProperty(key, value);
      return;
    }
    Entity2D entity = named.get(target);
    if (entity != null) entity.applyCustomProperty(key, value);
  }

  private void dispatchTimelineEvent(JesAst.TimelineAction action) {
    if (action == null) return;
    String eventType = action.target;
    if (eventType == null || eventType.isBlank()) return;
    Map<String,Object> props = action.props == null ? java.util.Collections.emptyMap() : action.props;
    String normalized = eventType.trim();
    if ("script_call".equalsIgnoreCase(normalized) || "scriptcall".equalsIgnoreCase(normalized)) {
      String handler = firstString(props, "handler", "call", "name", "target");
      if (handler != null && !handler.isBlank()) {
        invokeCall(handler, eventProps(props));
      }
      return;
    }
    invokeCall(normalized, eventProps(props));
  }

  private static Map<String,Object> eventProps(Map<String,Object> source) {
    if (source == null || source.isEmpty()) return java.util.Collections.emptyMap();
    Map<String,Object> props = new HashMap<>();
    for (Map.Entry<String,Object> entry : source.entrySet()) {
      if (entry == null || entry.getKey() == null) continue;
      String key = entry.getKey().trim();
      if (key.isEmpty() || isTimelineEventMetaKey(key)) continue;
      props.put(key, entry.getValue());
    }
    return props;
  }

  private static String firstString(Map<String,Object> props, String... keys) {
    if (props == null || keys == null) return null;
    for (String key : keys) {
      Object value = props.get(key);
      if (value instanceof String s && !s.isBlank()) return s;
    }
    return null;
  }

  private static boolean isTimelineEventMetaKey(String key) {
    String normalized = key == null ? "" : key.trim().toLowerCase(java.util.Locale.ROOT);
    return "handler".equals(normalized)
        || "call".equals(normalized)
        || "name".equals(normalized);
  }

  private void handleAction(Binding b) {
    boolean handled = switch (b.action) {
      case "toggleDebug" -> { toggleDebugOverlay(); yield true; }
      case "spawnCircle" -> { spawnCircle(b.props); yield true; }
      case "spawnBox" -> { spawnBox(b.props); yield true; }
      case "moveHero" -> { moveHero(b.props); yield true; }
      case "interact" -> { interact(); yield true; }
      case "resetBalls" -> { resetToSpawn(); yield true; }
      default -> false;
    };
    if (!handled && actionHandler != null) {
      try { actionHandler.accept(b.action, b.props); } catch (Exception ignored) {
        // reason: user-registered callback must not crash the animation loop
        log.warn("JesScene2D: input binding action handler '{}' threw an exception", b.action, ignored);
      }
    } else if (!handled && callHandlers.containsKey(b.action)) {
      invokeCall(b.action, b.props);
    }
  }

  private void updateContinuousMovement(Input in, long deltaMs) {
    if (!continuousMovementEnabled) return;
    if (playerName == null || playerName.isBlank()) return;
    Entity2D e = named.get(playerName);
    if (!(e instanceof CharacterEntity2D ch)) return;
    double dirX = 0;
    double dirY = 0;
    if (in.isKeyDown("W") || in.isKeyDown("UP")) dirY -= 1;
    if (in.isKeyDown("S") || in.isKeyDown("DOWN")) dirY += 1;
    if (in.isKeyDown("A") || in.isKeyDown("LEFT")) dirX -= 1;
    if (in.isKeyDown("D") || in.isKeyDown("RIGHT")) dirX += 1;
    double len = Math.hypot(dirX, dirY);
    if (len > 0) { dirX /= len; dirY /= len; }
    double dt = deltaMs / 1000.0;
    double maxSpeed = ch.getSpeed();
    Stats s = statsByEntity.get(playerName);
    if (s != null && s.getSpeed() > 0) maxSpeed = s.getSpeed();
    if (maxSpeed <= 0) maxSpeed = 80.0;
    heroVx += dirX * heroAccel * dt;
    heroVy += dirY * heroAccel * dt;
    // apply drag
    heroVx -= heroVx * heroDrag * dt;
    heroVy -= heroVy * heroDrag * dt;
    double vLen = Math.hypot(heroVx, heroVy);
    if (vLen > maxSpeed) {
      heroVx = (heroVx / vLen) * maxSpeed;
      heroVy = (heroVy / vLen) * maxSpeed;
    }
    double nx = e.getX() + heroVx * dt;
    double ny = e.getY() + heroVy * dt;
    if (isBlockedWorld(nx, ny)) {
      // try sliding along axes
      double nxX = e.getX() + heroVx * dt;
      double nyY = e.getY() + heroVy * dt;
      if (!isBlockedWorld(nxX, e.getY())) {
        e.setPosition(nxX, e.getY());
        heroVy = 0;
      } else if (!isBlockedWorld(e.getX(), nyY)) {
        e.setPosition(e.getX(), nyY);
        heroVx = 0;
      } else {
        heroVx = 0; heroVy = 0;
      }
      return;
    }
    e.setPosition(nx, ny);
    checkTriggersAt(nx, ny);
    if (Math.abs(heroVx) > 1e-3 || Math.abs(heroVy) > 1e-3) {
      playerFacing = Math.abs(heroVx) > Math.abs(heroVy)
          ? (heroVx > 0 ? "right" : "left")
          : (heroVy > 0 ? "down" : "up");
      ch.setCurrentAnimation(playerFacing);
    }
  }

  private void toggleDebugOverlay() {
    if (debugOverlay == null) {
      debugOverlay = new PhysicsDebugOverlay2D(this);
      add(debugOverlay);
    }
    debug = !debug;
    if (debugOverlay != null) debugOverlay.setVisible(debug);
  }

  private void spawnCircle(Map<String,Object> props) {
    Input in = getInput(); if (in == null) return;
    double x = toNum(props.get("x"), in.getMouseX());
    double y = toNum(props.get("y"), in.getMouseY());
    double r = toNum(props.get("r"), 10);
    double mass = toNum(props.get("mass"), 1);
    double rest = toNum(props.get("restitution"), 0.4);
    RigidBody2D body = RigidBody2D.circle(x, y, r);
    body.setMass(mass); body.setRestitution(rest);
    world.addBody(body);
    PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
    add(vis);
  }

  private void useItem(Map<String,Object> props) {
    String user = toStr(props.get("user"), null);
    String itemId = toStr(props.get("itemId"), null);
    String target = toStr(props.get("target"), user);
    if (user == null || user.isBlank()) return;
    if (itemId == null || itemId.isBlank()) return;
    if (target == null || target.isBlank()) target = user;

    Inventory inv = inventories.get(user);
    if (inv == null) return;
    if (!inv.remove(itemId, 1)) return;

    Item item = items.get(itemId);
    if (item == null) return;

    Map<String,Object> ip = item.getProps();

    double hpRestore = toNum(ip.get("hpRestore"), 0);
    if (hpRestore > 0) {
      heal(target, hpRestore, itemId);
    }

    Stats stats = statsByEntity.get(target);
    if (stats != null) {
      double mpRestore = toNum(ip.get("mpRestore"), 0);
      if (mpRestore > 0) {
        double currentMp = stats.getMp();
        double maxMp = stats.getMaxMp();
        double newMp = currentMp + mpRestore;
        if (maxMp > 0 && newMp > maxMp) newMp = maxMp;
        stats.setMp(newMp);
      }
    }

    Object onUseCallObj = ip.get("onUseCall");
    if (onUseCallObj instanceof String onUseCall && !onUseCall.isBlank()) {
      Map<String,Object> callProps = new HashMap<>();
      callProps.put("user", user);
      callProps.put("target", target);
      callProps.put("itemId", itemId);
      invokeCall(onUseCall, callProps);
    }
  }

  private void handleEquipItem(Map<String,Object> props) {
    String user = toStr(props.get("user"), null);
    String slot = toStr(props.get("slot"), null);
    String itemId = toStr(props.get("itemId"), null);
    if (user == null || user.isBlank()) return;
    if (slot == null || slot.isBlank()) return;
    if (itemId == null || itemId.isBlank()) return;

    Item item = items.get(itemId);
    if (item == null) return;
    Object typeObj = item.getProps().get("type");
    if (typeObj instanceof String t && !"equipment".equalsIgnoreCase(t)) return;
    Object slotObj = item.getProps().get("equipSlot");
    if (slotObj instanceof String s && !s.isBlank()) {
      if (!s.equalsIgnoreCase(slot)) return;
    }

    Inventory inv = inventories.get(user);
    if (inv == null || !inv.remove(itemId, 1)) return;

    Equipment eq = equipmentByEntity.get(user);
    if (eq == null) {
      eq = new Equipment();
      equipmentByEntity.put(user, eq);
    }

    String previous = eq.get(slot);
    eq.set(slot, itemId);
    recomputeEquipmentBonuses(user);

    if (previous != null && !previous.isBlank()) {
      Item prevItem = items.get(previous);
      int maxStackPrev = getMaxStackForItem(prevItem);
      Inventory inv2 = inventories.get(user);
      if (inv2 == null) {
        inv2 = new Inventory();
        inventories.put(user, inv2);
      }
      inv2.addBounded(previous, 1, maxStackPrev);
    }
  }

  private void handleUnequipItem(Map<String,Object> props) {
    String user = toStr(props.get("user"), null);
    String slot = toStr(props.get("slot"), null);
    if (user == null || user.isBlank()) return;
    if (slot == null || slot.isBlank()) return;
    Equipment eq = equipmentByEntity.get(user);
    if (eq == null) return;
    String current = eq.get(slot);
    if (current == null || current.isBlank()) return;
    eq.set(slot, null);
    recomputeEquipmentBonuses(user);

    Item item = items.get(current);
    int maxStack = getMaxStackForItem(item);
    Inventory inv = inventories.get(user);
    if (inv == null) {
      inv = new Inventory();
      inventories.put(user, inv);
    }
    inv.addBounded(current, 1, maxStack);
  }

  private void recomputeEquipmentBonuses(String name) {
    if (name == null || name.isBlank()) return;
    Stats stats = statsByEntity.get(name);
    if (stats == null) return;
    Equipment eq = equipmentByEntity.get(name);
    stats.setAtkBonus(0.0);
    stats.setDefBonus(0.0);
    stats.setSpeedBonus(0.0);
    if (eq == null) return;
    for (String slot : eq.getSlots().keySet()) {
      String itemId = eq.get(slot);
      if (itemId == null || itemId.isBlank()) continue;
      Item item = items.get(itemId);
      if (item == null) continue;
      Map<String,Object> ip = item.getProps();
      double atkBonus = toNum(ip.get("atkBonus"), 0);
      double defBonus = toNum(ip.get("defBonus"), 0);
      double speedBonus = toNum(ip.get("speedBonus"), 0);
      if (atkBonus != 0) stats.setAtkBonus(stats.getAtkBonus() + atkBonus);
      if (defBonus != 0) stats.setDefBonus(stats.getDefBonus() + defBonus);
      if (speedBonus != 0) stats.setSpeedBonus(stats.getSpeedBonus() + speedBonus);
    }
  }

  private void handleGiveItem(Map<String,Object> props) {
    String target = toStr(props.get("target"), null);
    String itemId = toStr(props.get("itemId"), null);
    int count = (int) toNum(props.get("count"), 1);
    if (count <= 0) count = 1;
    giveItem(target, itemId, count);
  }

  private void handleTakeItem(Map<String,Object> props) {
    String target = toStr(props.get("target"), null);
    String itemId = toStr(props.get("itemId"), null);
    int count = (int) toNum(props.get("count"), 1);
    if (count <= 0) count = 1;
    takeItem(target, itemId, count);
  }

  private int getMaxStackForItem(Item item) {
    if (item == null) return 0;
    Object v = item.getProps().get("maxStack");
    if (v instanceof Number n) {
      int m = (int) n.doubleValue();
      return m <= 0 ? 0 : m;
    }
    return 0;
  }
  
  private boolean isBlockedWorld(double x, double y) {
    if (collisionTilemaps.isEmpty() || gridW == 0 || gridH == 0) return false;
    int tx = (int) Math.floor(x / gridW);
    int ty = (int) Math.floor(y / gridH);
    return isBlockedTile(tx, ty);
  }

  private boolean isBlockedTile(int tx, int ty) {
    if (collisionTilemaps.isEmpty()) return false;
    for (TileMap2D tm : collisionTilemaps) {
      if (tx < 0 || ty < 0 || tx >= tm.getCols() || ty >= tm.getRows()) {
        return true;
      }
      if (tm.getTile(tx, ty) >= 0) return true;
    }
    return false;
  }

  public boolean isWorldBlocked(double x, double y) { return isBlockedWorld(x, y); }
  public boolean isTileBlocked(int tx, int ty) { return isBlockedTile(tx, ty); }
  public com.jvn.core.physics.PhysicsWorld2D.RaycastHit raycast(double x1, double y1, double x2, double y2) {
    return world == null ? null : world.raycast(x1, y1, x2, y2);
  }
  private boolean hasLineOfSight(double x1, double y1, double x2, double y2) {
    double dx = x2 - x1;
    double dy = y2 - y1;
    double dist = Math.hypot(dx, dy);
    if (dist == 0) return true;
    double step = Math.max(1.0, Math.min(gridW, gridH) * 0.5);
    int samples = (int) Math.ceil(dist / step);
    double nx = dx / samples;
    double ny = dy / samples;
    double cx = x1;
    double cy = y1;
    for (int i = 0; i < samples; i++) {
      cx += nx; cy += ny;
      if (isBlockedWorld(cx, cy)) return false;
    }
    return true;
  }

  public List<int[]> findPathTiles(double sx, double sy, double tx, double ty, int maxNodes) {
    List<int[]> empty = new ArrayList<>();
    if (gridW <= 0 || gridH <= 0) return empty;
    int startX = (int) Math.floor(sx / gridW);
    int startY = (int) Math.floor(sy / gridH);
    int goalX = (int) Math.floor(tx / gridW);
    int goalY = (int) Math.floor(ty / gridH);
    if (startX == goalX && startY == goalY) return empty;
    record Node(int x, int y, int g, int f, Node parent) {}
    java.util.PriorityQueue<Node> open = new java.util.PriorityQueue<>(java.util.Comparator.comparingInt(n -> n.f));
    java.util.Set<String> closed = new java.util.HashSet<>();
    open.add(new Node(startX, startY, 0, Math.abs(goalX - startX) + Math.abs(goalY - startY), null));
    int explored = 0;
    while (!open.isEmpty() && explored < maxNodes) {
      Node n = open.poll(); explored++;
      if (n.x == goalX && n.y == goalY) {
        List<int[]> path = new ArrayList<>();
        Node cur = n.parent;
        while (cur != null && !(cur.x == startX && cur.y == startY)) {
          path.add(0, new int[]{cur.x, cur.y});
          cur = cur.parent;
        }
        return path;
      }
      String key = n.x + "," + n.y;
      if (!closed.add(key)) continue;
      int[][] dirs = new int[][]{{1,0},{-1,0},{0,1},{0,-1}};
      for (int[] d : dirs) {
        int nx = n.x + d[0];
        int ny = n.y + d[1];
        if (isBlockedTile(nx, ny)) continue;
        int g = n.g + 1;
        int h = Math.abs(goalX - nx) + Math.abs(goalY - ny);
        open.add(new Node(nx, ny, g, g + h, n));
      }
    }
    return empty;
  }

  private void checkTriggersAt(double x, double y) {
    if (triggerLayers.isEmpty() || gridW == 0 || gridH == 0) return;
    int tx = (int) Math.floor(x / gridW);
    int ty = (int) Math.floor(y / gridH);
    for (TriggerLayer tl : triggerLayers) {
      TileMap2D tm = tl.tilemap;
      if (tm == null) continue;
      int tile = tm.getTile(tx, ty);
      if (tile < 0) continue;
      Map<String,Object> props = new HashMap<>(tl.props);
      props.put("tileX", (double) tx);
      props.put("tileY", (double) ty);
      props.put("tile", (double) tile);
      if (tl.mapName != null && !tl.mapName.isBlank()) {
        props.put("map", tl.mapName);
      }
      invokeCall(tl.call, props);
    }
  }

  private void moveHero(Map<String,Object> props) {
    if (playerName == null || playerName.isBlank()) return;
    Entity2D e = named.get(playerName);
    if (e == null) return;
    String dir = toStr(props.get("dir"), "down");
    double dx = 0;
    double dy = 0;
    String dLower = dir == null ? "" : dir.toLowerCase();
    switch (dLower) {
      case "up" -> dy = -gridH;
      case "down" -> dy = gridH;
      case "left" -> dx = -gridW;
      case "right" -> dx = gridW;
      default -> {}
    }
    if (dx == 0 && dy == 0) return;
    if (!dLower.isEmpty()) playerFacing = dLower;
    double nx = e.getX() + dx;
    double ny = e.getY() + dy;
    if (isBlockedWorld(nx, ny)) return;
    e.setPosition(nx, ny);
    checkTriggersAt(nx, ny);
    if (e instanceof CharacterEntity2D ch) {
      String animName = switch (dLower) {
        case "up" -> "up";
        case "down" -> "down";
        case "left" -> "left";
        case "right" -> "right";
        default -> null;
      };
      if (animName != null) ch.setCurrentAnimation(animName);
    }
  }

  private void interact() {
    if (playerName == null || playerName.isBlank()) return;
    if (gridW == 0 || gridH == 0) return;
    Entity2D hero = named.get(playerName);
    if (hero == null) return;
    int heroTx = (int) Math.floor(hero.getX() / gridW);
    int heroTy = (int) Math.floor(hero.getY() / gridH);
    int tx = heroTx;
    int ty = heroTy;
    String d = playerFacing == null ? "down" : playerFacing;
    switch (d) {
      case "up" -> ty--;
      case "down" -> ty++;
      case "left" -> tx--;
      case "right" -> tx++;
      default -> {}
    }
    String npcName = null;
    CharacterEntity2D npcEntity = null;
    for (Map.Entry<String, Entity2D> entry : named.entrySet()) {
      String name = entry.getKey();
      if (name == null || name.equals(playerName)) continue;
      Entity2D ent = entry.getValue();
      if (!(ent instanceof CharacterEntity2D)) continue;
      int ex = (int) Math.floor(ent.getX() / gridW);
      int ey = (int) Math.floor(ent.getY() / gridH);
      if (ex == tx && ey == ty) {
        npcName = name;
        npcEntity = (CharacterEntity2D) ent;
        break;
      }
    }
    if (npcName != null) {
      Map<String,Object> props = new HashMap<>();
      props.put("npc", npcName);
      if (npcEntity != null) {
        String did = npcEntity.getDialogueId();
        if (did != null && !did.isBlank()) props.put("dialogueId", did);
      }
      props.put("facing", d);
      props.put("heroX", hero.getX());
      props.put("heroY", hero.getY());
      invokeCall("interactNpc", props);
    }
  }

  private void handleButtons(Input in) {
    if (buttons.isEmpty() || in == null) return;
    double mx = in.getMouseX();
    double my = in.getMouseY();
    boolean mouseDown = in.isMouseDown(0);
    boolean mousePressed = in.wasMousePressed(0);
    for (Button2D btn : buttons) {
      if (btn == null || !btn.isVisible()) continue;
      double bx = btn.getX();
      double by = btn.getY();
      double bw = btn.getWidth();
      double bh = btn.getHeight();
      boolean hit = mx >= bx && mx <= bx + bw && my >= by && my <= by + bh;
      btn.setHovered(hit);
      btn.setDown(hit && mouseDown);
      if (hit && mousePressed) {
        String call = btn.getCall();
        if (call != null && !call.isBlank()) {
          Map<String,Object> props = new HashMap<>(btn.getProps());
          props.put("x", mx); props.put("y", my);
          props.put("value", 1.0);
          invokeCall(call, props);
        }
      }
    }
  }

  private void handleSliders(Input in) {
    if (sliders.isEmpty() || in == null) return;
    double mx = in.getMouseX();
    double my = in.getMouseY();
    boolean mouseDown = in.isMouseDown(0);
    boolean mousePressed = in.wasMousePressed(0);
    for (Slider2D sl : sliders) {
      if (sl == null || !sl.isVisible()) continue;
      double sx = sl.getX();
      double sy = sl.getY();
      double sw = sl.getWidth();
      double sh = sl.getHeight();
      boolean hit = mx >= sx && mx <= sx + sw && my >= sy && my <= sy + sh;
      if (mousePressed && hit) sl.setDragging(true);
      if (!mouseDown) sl.setDragging(false);
      if (sl.isDragging() && mouseDown) {
        double old = sl.getValue();
        sl.setFromPixel(mx - sx);
        if (Math.abs(sl.getValue() - old) > 1e-6) {
          String call = sl.getCall();
          if (call != null && !call.isBlank()) {
            Map<String,Object> props = new HashMap<>(sl.getProps());
            props.put("value", sl.getValue());
            invokeCall(call, props);
          }
        }
      }
    }
  }

  private void updateAi(long deltaMs) {
    if (aiByEntity.isEmpty()) return;
    double dt = deltaMs / 1000.0;
    if (dt <= 0) return;
    for (Map.Entry<String,Ai2D> entry : aiByEntity.entrySet()) {
      String name = entry.getKey();
      Ai2D ai = entry.getValue();
      if (ai == null) continue;
      Entity2D e = named.get(name);
      if (e == null) continue;
      String type = ai.getType();
      if (type == null) type = "";
      String tLower = type.toLowerCase();
      if ("chase".equals(tLower) || "chasehero".equals(tLower) || "chase_and_attack".equals(tLower)) {
        updateAiChaseAndAttack(name, e, ai, dt, deltaMs);
      } else if ("patrol".equals(tLower) || "patrol_chase".equals(tLower)) {
        updateAiPatrol(name, e, ai, dt, deltaMs, "patrol_chase".equals(tLower));
      } else if ("guard".equals(tLower) || tLower.contains("flee")) {
        updateAiChaseAndAttack(name, e, ai, dt, deltaMs);
      }
    }
  }

  private void updateCameraFollow(long deltaMs) {
    if (cameraFollowTarget == null || cameraFollowTarget.isBlank()) return;
    com.jvn.core.graphics.Camera2D cam = getCamera();
    if (cam == null) return;
    Entity2D t = named.get(cameraFollowTarget);
    if (t == null) return;
    double lerp = cameraFollowLerp;
    double p = Math.max(0.0, Math.min(1.0, lerp));
    double targetX = t.getX() + cameraOffsetX;
    double targetY = t.getY() + cameraOffsetY;
    double nx = cam.getX();
    double ny = cam.getY();
    if (cameraDeadZoneW > 0) {
      double left = cam.getX() - cameraDeadZoneW * 0.5;
      double right = cam.getX() + cameraDeadZoneW * 0.5;
      if (targetX < left) nx = targetX + cameraDeadZoneW * 0.5;
      else if (targetX > right) nx = targetX - cameraDeadZoneW * 0.5;
    } else {
      nx = cam.getX() + (targetX - cam.getX()) * p;
    }
    if (cameraDeadZoneH > 0) {
      double top = cam.getY() - cameraDeadZoneH * 0.5;
      double bottom = cam.getY() + cameraDeadZoneH * 0.5;
      if (targetY < top) ny = targetY + cameraDeadZoneH * 0.5;
      else if (targetY > bottom) ny = targetY - cameraDeadZoneH * 0.5;
    } else {
      ny = cam.getY() + (targetY - cam.getY()) * p;
    }
    cam.setPosition(nx, ny);
  }

  private void updateAiChaseAndAttack(String name, Entity2D e, Ai2D ai, double dt, long deltaMs) {
    String targetName = ai.getTarget();
    if (targetName == null || targetName.isBlank()) {
      targetName = playerName;
    }
    if (targetName == null || targetName.isBlank()) return;
    Entity2D target = named.get(targetName);
    if (target == null) return;

    double ex = e.getX();
    double ey = e.getY();
    double tx = target.getX();
    double ty = target.getY();
    double dx = tx - ex;
    double dy = ty - ey;
    double dist = Math.hypot(dx, dy);

    double aggroRange = ai.getAggroRange();
    if (aggroRange > 0 && dist > aggroRange) return;
    if (ai.isRequiresLineOfSight() && !hasLineOfSight(ex, ey, tx, ty)) return;

    double guardRadius = ai.getGuardRadius();
    if (guardRadius > 0) {
      double[] spawn = spawnPositions.get(name);
      if (spawn != null && spawn.length >= 2) {
        double sx = spawn[0];
        double sy = spawn[1];
        double distFromSpawn = Math.hypot(ex - sx, ey - sy);
        if (distFromSpawn > guardRadius && dist > guardRadius) {
          // Return to guard center
          dx = sx - ex; dy = sy - ey; dist = Math.hypot(dx, dy);
          tx = sx; ty = sy;
        }
      }
    }

    double fleeDistance = ai.getFleeDistance();
    boolean shouldFlee = fleeDistance > 0 && dist < fleeDistance;

    double attackRange = ai.getAttackRange();
    if (attackRange <= 0) attackRange = gridW;

    double cooldown = Math.max(0.0, ai.getAttackCooldownMs() - deltaMs);
    ai.setAttackCooldownMs(cooldown);

    if (!shouldFlee && dist <= attackRange) {
      double interval = ai.getAttackIntervalMs();
      if (interval <= 0) interval = 1000.0;
      if (cooldown <= 0) {
        double amount = ai.getAttackAmount();
        if (amount <= 0) {
          Stats s = statsByEntity.get(name);
          if (s != null) amount = s.getAtk();
        }
        if (amount > 0) {
          applyDamage(targetName, amount, name);
        }
        ai.setAttackCooldownMs(interval);
      }
      return;
    }

    double speed = ai.getMoveSpeed();
    if (speed <= 0) {
      Stats s = statsByEntity.get(name);
      if (s != null) speed = s.getSpeed();
    }
    if (speed <= 0) {
      speed = 80.0;
    }

    if (dist <= 0) return;
    // Obstacle-aware smoothing
    double dirX = dx / dist;
    double dirY = dy / dist;
    double blend = 0.7;
    double lastX = ai.getLastDirX();
    double lastY = ai.getLastDirY();
    if (lastX != 0 || lastY != 0) {
      dirX = lastX * blend + dirX * (1 - blend);
      dirY = lastY * blend + dirY * (1 - blend);
      double len = Math.hypot(dirX, dirY);
      if (len > 0) { dirX /= len; dirY /= len; }
    }
    ai.setLastDir(dirX, dirY);

    if (shouldFlee) { dirX = -dirX; dirY = -dirY; }

    double maxStep = speed * dt;
    if (maxStep <= 0) return;
    double step = Math.min(maxStep, dist);
    double nx = ex + dirX * step;
    double ny = ey + dirY * step;
    if (isBlockedWorld(nx, ny)) {
      // attempt simple grid pathfinding fallback
      List<int[]> path = findPathTiles(ex, ey, tx, ty, 512);
      if (!path.isEmpty()) {
        int[] next = path.get(0);
        double px = next[0] * gridW + gridW * 0.5;
        double py = next[1] * gridH + gridH * 0.5;
        double pdx = px - ex;
        double pdy = py - ey;
        double pdist = Math.hypot(pdx, pdy);
        if (pdist > 0) {
          double step2 = Math.min(maxStep, pdist);
          nx = ex + pdx * (step2 / pdist);
          ny = ey + pdy * (step2 / pdist);
        } else return;
        if (isBlockedWorld(nx, ny)) return;
      } else {
        return;
      }
    }
    e.setPosition(nx, ny);
  }

  private void updateAiPatrol(String name, Entity2D e, Ai2D ai, double dt, long deltaMs, boolean chaseOnAggro) {
    String targetName = chaseOnAggro ? (ai.getTarget() == null || ai.getTarget().isBlank() ? playerName : ai.getTarget()) : null;
    if (targetName != null) {
      Entity2D target = named.get(targetName);
      if (target != null) {
        double dx = target.getX() - e.getX();
        double dy = target.getY() - e.getY();
        double dist = Math.hypot(dx, dy);
        double aggro = ai.getAggroRange();
        if (aggro > 0 && dist <= aggro) {
          updateAiChaseAndAttack(name, e, ai, dt, deltaMs);
          return;
        }
      }
    }
    // Wander/patrol around spawn
    double[] spawn = spawnPositions.get(name);
    double sx = spawn != null && spawn.length >= 2 ? spawn[0] : e.getX();
    double sy = spawn != null && spawn.length >= 2 ? spawn[1] : e.getY();
    ai.setPatrolElapsed(ai.getPatrolElapsed() + deltaMs);
    if (!ai.hasPatrolGoal() || ai.getPatrolElapsed() >= ai.getPatrolIntervalMs()) {
      ai.setPatrolElapsed(0);
      double radius = ai.getPatrolRadius() > 0 ? ai.getPatrolRadius() : Math.min(gridW, gridH) * 3.0;
      double ang = Math.random() * Math.PI * 2;
      ai.setPatrolGoal(sx + Math.cos(ang) * radius, sy + Math.sin(ang) * radius);
    }
    if (ai.hasPatrolGoal()) {
      double gx = ai.getPatrolGoalX();
      double gy = ai.getPatrolGoalY();
      double dx = gx - e.getX();
      double dy = gy - e.getY();
      double dist = Math.hypot(dx, dy);
      double speed = ai.getMoveSpeed();
      if (speed <= 0) {
        Stats s = statsByEntity.get(name);
        if (s != null) speed = s.getSpeed();
      }
      if (speed <= 0) speed = 60.0;
      double step = speed * dt;
      if (dist <= step || dist == 0) {
        e.setPosition(gx, gy);
        ai.clearPatrolGoal();
      } else {
        double nx = e.getX() + dx * (step / dist);
        double ny = e.getY() + dy * (step / dist);
        if (!isBlockedWorld(nx, ny)) e.setPosition(nx, ny);
      }
    }
  }

  private void handleAttack(Map<String,Object> props) {
    String attacker = toStr(props.get("attacker"), null);
    String target = toStr(props.get("target"), null);
    if (attacker == null || attacker.isBlank()) return;
    if (target == null || target.isBlank()) return;
    double amount = toNum(props.get("amount"), Double.NaN);
    if (Double.isNaN(amount) || amount <= 0) {
      Stats s = statsByEntity.get(attacker);
      if (s != null) amount = s.getAtk();
    }
    if (amount <= 0) return;
    applyDamage(target, amount, attacker);
  }

  private void handlePocket(Map<String,Object> props) {
    String ball = toStr(props.get("other"), null);
    if (ball != null) {
      removeEntity(ball);
      // Track simple score
      incrementVar("score", 1);
      updateScoreLabel();
    }
  }

  private void resetToSpawn() {
    for (Map.Entry<String, Entity2D> entry : new java.util.HashMap<>(named).entrySet()) {
      String name = entry.getKey();
      Entity2D e = entry.getValue();
      double[] spawn = spawnPositions.get(name);
      if (spawn != null && spawn.length >= 2) {
        e.setPosition(spawn[0], spawn[1]);
      }
      if (bodyByName.containsKey(name)) {
        RigidBody2D b = bodyByName.get(name);
        if (spawn != null && spawn.length >= 2) b.setPosition(spawn[0], spawn[1]);
        b.setVelocity(0, 0);
      }
    }
    scriptVars.put("score", 0.0);
    updateScoreLabel();
  }

  private void setLabelText(Map<String,Object> props) {
    String target = toStr(props.get("target"), null);
    String text = toStr(props.get("text"), null);
    if (target == null || text == null) return;
    Entity2D e = named.get(target);
    if (e instanceof com.jvn.core.scene2d.Label2D lbl) {
      lbl.setText(text);
    }
  }

  private void incrementVar(String key, double amount) {
    if (key == null) return;
    double cur = scriptVars.getOrDefault(key, 0.0);
    scriptVars.put(key, cur + amount);
  }

  private void updateScoreLabel() {
    double score = scriptVars.getOrDefault("score", 0.0);
    setLabelText(java.util.Map.of("target", "score_label", "text", "Score: " + ((int) score)));
  }

  private void warpMap(Map<String,Object> props) {
    if (playerName == null || playerName.isBlank()) return;
    if (gridW == 0 || gridH == 0) return;
    Entity2D hero = named.get(playerName);
    if (hero == null) return;

    double wx;
    double wy;

    // Prefer explicit target tile coordinates
    if (props.containsKey("toTileX") || props.containsKey("toTileY")) {
      double tx = toNum(props.get("toTileX"), hero.getX() / gridW);
      double ty = toNum(props.get("toTileY"), hero.getY() / gridH);
      wx = tx * gridW;
      wy = ty * gridH;
    } else if (props.containsKey("toX") || props.containsKey("toY")) {
      wx = toNum(props.get("toX"), hero.getX());
      wy = toNum(props.get("toY"), hero.getY());
    } else if (props.containsKey("tileX") || props.containsKey("tileY")) {
      double tx = toNum(props.get("tileX"), hero.getX() / gridW);
      double ty = toNum(props.get("tileY"), hero.getY() / gridH);
      wx = tx * gridW;
      wy = ty * gridH;
    } else {
      return; // no usable coordinates
    }

    hero.setPosition(wx, wy);
  }

  private void spawnBox(Map<String,Object> props) {
    Input in = getInput(); if (in == null) return;
    double x = toNum(props.get("x"), in.getMouseX());
    double y = toNum(props.get("y"), in.getMouseY());
    double w = toNum(props.get("w"), 40);
    double h = toNum(props.get("h"), 40);
    double mass = toNum(props.get("mass"), 1);
    double rest = toNum(props.get("restitution"), 0.2);
    RigidBody2D body = RigidBody2D.box(x, y, w, h);
    body.setMass(mass); body.setRestitution(rest);
    world.addBody(body);
    PhysicsBodyEntity2D vis = new PhysicsBodyEntity2D(body);
    add(vis);
  }

  public void applyDamage(String name, double amount, String source) {
    if (name == null) return;
    if (amount <= 0) return;
    Stats stats = statsByEntity.get(name);
    if (stats == null) return;
    double currentHp = stats.getHp();
    double maxHp = stats.getMaxHp();
    double newHp = currentHp - amount;
    if (maxHp > 0 && newHp < 0) newHp = 0;
    stats.setHp(newHp);
    if (stats.isDead()) {
      String deathCall = stats.getDeathCall();
      if (deathCall != null && !deathCall.isBlank()) {
        Map<String,Object> props = new HashMap<>();
        props.put("entity", name);
        if (source != null && !source.isBlank()) {
          props.put("source", source);
        }
        invokeCall(deathCall, props);
      }
      if (stats.isRemoveOnDeath()) {
        removeEntity(name);
        statsByEntity.remove(name);
      }
    }
  }

  public void heal(String name, double amount, String source) {
    if (name == null) return;
    if (amount <= 0) return;
    Stats stats = statsByEntity.get(name);
    if (stats == null) return;
    double currentHp = stats.getHp();
    double maxHp = stats.getMaxHp();
    double newHp = currentHp + amount;
    if (maxHp > 0 && newHp > maxHp) newHp = maxHp;
    stats.setHp(newHp);
  }

  private static double toNum(Object o, double def) {
    return o instanceof Number n ? n.doubleValue() : def;
  }
  
  private static String toStr(Object o, String def) {
    return o instanceof String s ? s : def;
  }

  private static boolean toBool(Object o, boolean def) {
    return o instanceof Boolean b ? b : def;
  }

  private static double getAlpha(Entity2D e) {
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) return s.getAlpha();
    if (e instanceof com.jvn.core.scene2d.Label2D l) return l.getAlpha();
    if (e instanceof com.jvn.core.scene2d.Panel2D p) return p.getFillA();
    return 1.0;
  }

  private static void setAlpha(Entity2D e, double a) {
    double aa = Math.max(0.0, Math.min(1.0, a));
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) s.setAlpha(aa);
    else if (e instanceof com.jvn.core.scene2d.Label2D l) l.setColor(l.getColorR(), l.getColorG(), l.getColorB(), aa);
    else if (e instanceof com.jvn.core.scene2d.Panel2D p) p.setFill(p.getFillR(), p.getFillG(), p.getFillB(), aa);
  }

  private static double getPivotX(Entity2D e) {
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) return s.getOriginX();
    if (e instanceof com.jvn.core.scene2d.CharacterEntity2D c) return c.getOriginX();
    return 0.0;
  }

  private static double getPivotY(Entity2D e) {
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) return s.getOriginY();
    if (e instanceof com.jvn.core.scene2d.CharacterEntity2D c) return c.getOriginY();
    return 0.0;
  }

  private static void setPivot(Entity2D e, double ox, double oy) {
    if (e instanceof com.jvn.core.scene2d.Sprite2D s) s.setOrigin(ox, oy);
    else if (e instanceof com.jvn.core.scene2d.CharacterEntity2D c) c.setOrigin(ox, oy);
  }

  // --- State save/load helpers ---
  public JesSceneState saveState() {
    JesSceneState st = new JesSceneState();
    st.playerName = this.playerName;
    st.playerFacing = this.playerFacing;
    if (playerName != null) {
      Entity2D hero = named.get(playerName);
      if (hero != null) st.playerPosition = new double[]{ hero.getX(), hero.getY() };
    }
    for (Map.Entry<String, Entity2D> entry : named.entrySet()) {
      Entity2D e = entry.getValue();
      if (e == null) continue;
      st.entityPositions.put(entry.getKey(), new double[]{ e.getX(), e.getY() });
    }
    for (Map.Entry<String, Stats> entry : statsByEntity.entrySet()) {
      Stats s = entry.getValue();
      if (s == null) continue;
      JesSceneState.StatsSnapshot snap = new JesSceneState.StatsSnapshot();
      snap.maxHp = s.getMaxHp();
      snap.hp = s.getHp();
      snap.maxMp = s.getMaxMp();
      snap.mp = s.getMp();
      snap.atk = s.getBaseAtk();
      snap.def = s.getBaseDef();
      snap.speed = s.getBaseSpeed();
      snap.atkBonus = s.getAtkBonus();
      snap.defBonus = s.getDefBonus();
      snap.speedBonus = s.getSpeedBonus();
      snap.deathCall = s.getDeathCall();
      snap.removeOnDeath = s.isRemoveOnDeath();
      st.stats.put(entry.getKey(), snap);
    }
    for (Map.Entry<String, Inventory> entry : inventories.entrySet()) {
      Inventory inv = entry.getValue();
      if (inv == null) continue;
      st.inventories.put(entry.getKey(), new HashMap<>(inv.getItemCounts()));
      st.inventorySlots.put(entry.getKey(), inv.getSlots());
    }
    for (Map.Entry<String, Equipment> entry : equipmentByEntity.entrySet()) {
      Equipment eq = entry.getValue();
      if (eq == null) continue;
      st.equipment.put(entry.getKey(), new HashMap<>(eq.getSlots()));
    }
    return st;
  }

  public void loadState(JesSceneState state) {
    if (state == null) return;
    if (state.entityPositions != null) {
      for (Map.Entry<String,double[]> entry : state.entityPositions.entrySet()) {
        Entity2D e = named.get(entry.getKey());
        double[] pos = entry.getValue();
        if (e != null && pos != null && pos.length >= 2) {
          e.setPosition(pos[0], pos[1]);
        }
      }
    }
    if (state.stats != null) {
      for (Map.Entry<String, JesSceneState.StatsSnapshot> entry : state.stats.entrySet()) {
        JesSceneState.StatsSnapshot snap = entry.getValue();
        if (snap == null) continue;
        Stats s = statsByEntity.computeIfAbsent(entry.getKey(), k -> new Stats());
        s.setMaxHp(snap.maxHp);
        s.setHp(snap.hp);
        s.setMaxMp(snap.maxMp);
        s.setMp(snap.mp);
        s.setAtk(snap.atk);
        s.setDef(snap.def);
        s.setSpeed(snap.speed);
        s.setAtkBonus(snap.atkBonus);
        s.setDefBonus(snap.defBonus);
        s.setSpeedBonus(snap.speedBonus);
        s.setDeathCall(snap.deathCall);
        s.setRemoveOnDeath(snap.removeOnDeath);
      }
    }
    if (state.inventories != null) {
      for (Map.Entry<String, Map<String,Integer>> entry : state.inventories.entrySet()) {
        Map<String,Integer> counts = entry.getValue();
        if (counts == null) continue;
        Inventory inv = inventories.computeIfAbsent(entry.getKey(), k -> new Inventory());
        Integer slots = state.inventorySlots.get(entry.getKey());
        if (slots != null) inv.setSlots(slots);
        inv.getItemCounts().clear();
        inv.getItemCounts().putAll(counts);
      }
    }
    if (state.equipment != null) {
      for (Map.Entry<String, Map<String,String>> entry : state.equipment.entrySet()) {
        Map<String,String> slots = entry.getValue();
        if (slots == null) continue;
        Equipment eq = equipmentByEntity.computeIfAbsent(entry.getKey(), k -> new Equipment());
        eq.getSlots().clear();
        eq.getSlots().putAll(slots);
        // Preserve serialized stat bonuses from snapshot; recompute only if stats were not restored.
        if (state.stats == null || !state.stats.containsKey(entry.getKey())) {
          recomputeEquipmentBonuses(entry.getKey());
        }
      }
    }
    if (state.playerName != null && !state.playerName.isBlank()) {
      this.playerName = state.playerName;
    }
    if (state.playerFacing != null && !state.playerFacing.isBlank()) {
      this.playerFacing = state.playerFacing;
    }
    if (playerName != null && state.playerPosition != null && state.playerPosition.length >= 2) {
      Entity2D hero = named.get(playerName);
      if (hero != null) hero.setPosition(state.playerPosition[0], state.playerPosition[1]);
    }
  }
}

# Core Physics API

Use `com.jvn.core.physics.PhysicsWorld2D` for a scene with axis-aligned boxes, circles, gravity, sensors and collision response. JES authors can declare the same kinds of bodies in a scene file. See [JES physics](../../scripting/jes/systems/jes-physics.md) for that workflow.

## Create and step a world

```java
import com.jvn.core.physics.PhysicsWorld2D;
import com.jvn.core.physics.RigidBody2D;

class FallingBox {
  final PhysicsWorld2D world = new PhysicsWorld2D();
  final RigidBody2D box = RigidBody2D.box(40, 20, 16, 16);

  FallingBox() {
    world.setGravity(0, 600);
    world.setFixedTimeStepMs(1000.0 / 60, 4);
    RigidBody2D floor = RigidBody2D.box(0, 200, 320, 12);
    floor.setStatic(true);
    world.addBody(floor);
    world.addBody(box);
  }

  void update(double elapsedMs) {
    world.step(elapsedMs);
  }
}
```

A box takes its corner position, width and height. A circle takes its center and radius. `step` takes **milliseconds**. Passing `1.0 / 60` from a loop that measures seconds makes the simulation run roughly a thousand times too slowly. Convert the duration at the boundary.

`setFixedTimeStepMs(stepMs, maxSubSteps)` accumulates elapsed time and advances in fixed increments. `setMaxStepMs` limits the input delta after a long frame. The default maximum is 50 ms. Add static rectangular scenery with `addStaticRect` when you only need collision geometry. Use a static body when you need to identify that object from a world raycast.

## Bodies and contacts

| Operation | API |
| --- | --- |
| Move or inspect a body | `setPosition`, `getX`, `getY` |
| Change velocity | `setVelocity(vx, vy)`, `getVx`, `getVy` |
| Tune motion and response | `setMass`, `setFriction`, `setRestitution`, `setLinearDamping` |
| Mark a trigger | `setSensor(true)` and register `world.setSensorListener((sensor, other) -> …)` |
| Receive contact normals | Implement `PhysicsWorld2D.CollisionListener` and call `world.setCollisionListener(listener)` |
| Remove an object | `world.removeBody(body)` |

The collision listener has three methods. `onBodiesCollide` receives two bodies and the normal from the first toward the second. `onStaticCollide` receives a body, a static rectangle and a normal. `onBoundsCollide` receives a body and the side of the world boundary. The sensor listener reports overlap. If a trigger should award a collectible once, remember that it fired or remove the sensor after the first overlap.

## Raycast a segment

```java
PhysicsWorld2D.RaycastHit hit = world.raycast(48, 0, 48, 240);
if (hit != null) {
  double distance = hit.distance;
  RigidBody2D firstBody = hit.body;
}
```

The four arguments are two endpoints in world coordinates. The return value is the closest hit on a body along that segment. A miss or zero-length segment returns `null`. Read `x`, `y`, `nx` and `ny` for the contact point and normal. For a query against one rectangle, `Collision2D.raycastSegmentAABB` accepts coordinates or a `Segment2`.

## Verify a change

Step the falling-box example for a fixed number of frames. The box should fall, contact the floor and settle according to its restitution. Raycast once through the scene and once outside it. Finally make the floor a sensor and verify that the overlap callback fires as the box passes through. These checks distinguish timing, collision response and query errors.

[PhysicsWorld2D source](../../../modules/core/src/main/java/com/jvn/core/physics/PhysicsWorld2D.java) · [RigidBody2D source](../../../modules/core/src/main/java/com/jvn/core/physics/RigidBody2D.java) · [2D engine](../core/2d-engine.md)

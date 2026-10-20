# Testing Stature

Stature has three layers of tests: game-free JUnit tests, headless in-game gametests on both
loaders, and Fabric client gametests for the render path. The manual scenario matrix and the
screenshot baselines are M6's deliverable; this file records the automated harness M4 added and
how to run it.

## Prerequisites

- JDK 25.
- `gradle.properties` pins `org.gradle.java.home` to a Windows path. On another machine, override
  it in `~/.gradle/gradle.properties` rather than editing the tracked file.
- Windows: `.\gradlew.bat`. Unix: `./gradlew` (`git update-index --chmod=+x gradlew` if the
  executable bit was lost).

## Unit tests

```
.\gradlew.bat :api:test :common:test
```

`.\gradlew.bat build` runs the same tests and also runs the Fabric server gametests, because Loom
wires `check` to `runGameTest`. The tests are game-free: math, serialization, mixin contracts.
Anything that needs a level, a registry or a spawned entity belongs in a gametest.

## In-game gametests

The bodies live in
`common/src/main/java/dev/ellipog/stature/common/gametest/StatureGameTests.java` and are shared by
both loaders, so the two runs cannot drift apart. They ship in the jar (a few KB, the plan's Q13
recommendation) but are inert unless a loader registers them.

### Fabric

```
.\gradlew.bat :fabric:runGameTest
```

Run directory: `fabric/build/run/gameTest`. The run is headless and exits non-zero when a required
test fails. `fabric/build.gradle` sets `eula = true` (plan Q14). Loom 1.17.21 writes that accepted
EULA into the `clientGameTest` run directory; the build also writes `eula=true` into the headless
`gameTest` directory, because the game test server would otherwise write its own `eula=false`.
Both files are generated and ignored.

Fabric finds the tests through the `fabric-gametest` entrypoint in
`fabric/src/gametest/resources/fabric.mod.json`; the entrypoint class needs a public no-argument
constructor, because the loader instantiates it before reflecting over the `@GameTest` methods.

### NeoForge

```
.\gradlew.bat :neoforge:runGameTestServer
```

Run directory: `neoforge/run`. The run is headless and exits non-zero when a required test fails.
The `gameTestServer` run sets `-Dstature.gametest=true`, and only then does
`NeoForgeStatureGameTests` attach its `DeferredRegister` and its `RegisterGameTestsEvent`
listener. Every other run, and the shipped jar, registers nothing.

On a fresh checkout, create `neoforge/run/eula.txt` once with `eula=true` (the run directory
is gitignored). CI does the same, per plan Q14.

### Current coverage

| Test | Pins |
|---|---|
| `scaleDefaultsLeavePhysicsVanilla` | M1: at 0.0625x and 16x with no channel set, gravity, attack, health, follow range, friction, air drag and camera distance stay vanilla, while motion, jump, step and reach follow the scale |
| `partsOnlySurviveSave` | M2-1: an entity shaped only by `/stature part` keeps its factors through a `ValueOutput`/`ValueInput` round trip |
| `eyeInsideBoxForEveryPose` | M2-4: a frame eye override stays inside the box in every pose and is exact at standing |
| `framedThresholdsResolvedPerEntity` | M2-3, server half: a frame-less entity resolves nothing, a resolved answer wins, and dropping it returns the entity to the file values |
| `uniformScaleSurvivesSave` | M2: the uniform scale and the axes survive a save and load |
| `trampleRespectsMobGriefing` | M5: a 16x zombie breaks no leaves with `mob_griefing` false and a capped handful with it true |
| `trampleCappedPerScan` | M5: one trample scan destroys at most the default cap of 8 |
| `growInTunnelEndsInAir` | M5: growing 1x to 16x in a 3-high tunnel ends outside it, with at most one compression hit |
| `dangerousModelResetOnPlainSet` | M5: `100 --dangerous` then a plain `1` gives a 1x model, not a leftover 100x channel |
| `resetClearsEverything` | M5: `/stature reset` clears scale, shape, frame, channels and parts |
| `foreignScaleModifierNoDrift` | M5: a foreign `minecraft:scale` modifier multiplies on top and is not folded into Stature's base |

Each loader also runs vanilla's `minecraft:always_pass`, so a green run reports one more test than
this table. The count is the check: twelve on each loader means all eleven Stature bodies registered.

### Structures

The tests use `minecraft:empty` with padding and build their fixture in code: the M5 bodies clear
a volume first, because the gametest level is a real world with terrain and an uncleared spawn can
be displaced before the test measures anything. The leaf field is 13x13 and the tunnel is 5 blocks
long with a six-layer roof, both inside the padding the loader registrations give them. A shipped
`data/stature/structure/arena_32.nbt` is still the cleaner long-term answer; it is not needed while
these fixtures fit.

## Fabric client gametests

```
.\gradlew.bat :fabric:runClientGameTest
```

This starts a real client, so it needs a logged-in session locally and a virtual display
(`xvfb-run`) on headless CI. It is not part of `build` and is not run by the headless server
checks. Screenshots land under `fabric/build/run/clientGameTest/screenshots/`.
`StatureFabricClientGameTests` currently grows the joined player to 16x, opens the inventory and
takes `inventory_16x`; comparing it against a baseline waits for an approved screenshot (M6).

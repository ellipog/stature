# Testing Stature

Stature has three layers of tests: game-free JUnit tests, headless in-game gametests on both
loaders, and Fabric client gametests for the render path. The manual scenarios below are the part
no automated run can settle: the per-axis drawing, the feel of an ease, and two clients in the same
world. The last-verified column stays blank until a person has run the row.

## Prerequisites

- JDK 25.
- `gradle.properties` pins `org.gradle.java.home` to a Windows path. On another machine, override
  it in `~/.gradle/gradle.properties` rather than editing the tracked file.
- Windows: `.\gradlew.bat`. Unix: `./gradlew` (`git update-index --chmod=+x gradlew` if the
  executable bit was lost).
- The Fabric gametest run accepts the Minecraft EULA for its own run directory. The NeoForge
  gametest run needs `neoforge/run/eula.txt` with `eula=true` once on a fresh checkout. Both run
  directories are generated and ignored.

## Unit tests

```
.\gradlew.bat :api:test :common:test
```

The game-free suite is 177 tests: 43 in `:api` and 134 in `:common`, before the docs coverage
test is added. `.\gradlew.bat build` runs the same tests, and on Fabric it also runs the server
gametests because Loom wires `check` to `runGameTest`.

The tests are game-free: math, serialization, mixin contracts, and the markdown coverage check.
Anything that needs a level, a registry or a spawned entity belongs in a gametest.

## In-game gametests

The bodies live in
`common/src/main/java/dev/ellipog/stature/common/gametest/StatureGameTests.java` and are shared by
both loaders, so the two runs cannot drift apart. They ship in the jar (a few KB) but are inert
unless a loader registers them.

### Fabric

```
.\gradlew.bat :fabric:runGameTest
```

Run directory: `fabric/build/run/gameTest`. The run is headless and exits non-zero when a required
test fails. `fabric/build.gradle` sets `eula = true`, and the build writes the accepted EULA into
the headless run directory because Loom writes only the client run directory itself.

Fabric finds the tests through the `fabric-gametest` entrypoint in
`fabric/src/gametest/resources/fabric.mod.json`; the entrypoint class needs a public no-argument
constructor, because the loader instantiates it before reflecting over the `@GameTest` methods.

### NeoForge

```
.\gradlew.bat :neoforge:runGameTestServer
```

Run directory: `neoforge/run`. The run is headless and exits non-zero when a required test fails.
The `gameTestServer` run sets `-Dstature.gametest=true`, and only then does
`NeoForgeStatureGameTests` attach its `DeferredRegister` and its `RegisterGameTestsEvent` listener.
Every other run, and the shipped jar, registers nothing.

On a fresh checkout, create `neoforge/run/eula.txt` once with `eula=true` (the run directory is
gitignored).

A green run reports 12 tests on either loader: the 11 Stature bodies below plus vanilla's
`minecraft:always_pass`.

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

### Structures

The tests use `minecraft:empty` with padding and build their fixture in code: the M5 bodies clear
a volume first, because the gametest level is a real world with terrain and an uncleared spawn can
be displaced before the test measures anything. The leaf field is 13x13 and the tunnel is 5 blocks
long with a six-layer roof, both inside the padding the loader registrations give them. A shipped
`data/stature/structure/arena_32.nbt` is still the cleaner long-term answer; it is not needed while
these fixtures fit.

## Fabric client gametests and screenshots

```
.\gradlew.bat :fabric:runClientGameTest
```

This starts a real client, so it needs a logged-in session locally and a virtual display
(`xvfb-run`) on headless CI. It is not part of `build` and is not run by the headless server
checks. Screenshots land under `fabric/build/run/clientGameTest/screenshots/`.
`StatureFabricClientGameTests` currently grows the joined player to 16x, opens the inventory and
takes `inventory_16x`; comparing it against a baseline waits for an approved screenshot.

The manual screenshots for the scenarios below use the same shape: one F2 screenshot per row,
named `<loader>-<ID>.png` (for example `fabric-R1.png`). Keep them outside git or attach them to a
release; the repository holds the checklist, not the images.

## Manual scenario table

Every row is one screenshot or check, run on both loaders unless the row says otherwise. Fill the
last column only after a person has run the row, and name the date, the loader and the commit.

| ID | Setup | Look for | Last verified (date, loader, commit) |
|---|---|---|---|
| R1 | `/stature set @s 4 40 elastic`, F5 | smooth growth, no 20 Hz stepping (record a 3 s clip) | |
| R2 | `/stature size @s 0.5 1.2 2.0`, F5 front and side | model matches the F3+B hitbox on every axis | |
| R3 | R2, then `E` | paperdoll at normal size, shaped | |
| R4 | `/stature set @s 16`, `E` | paperdoll at normal size | |
| R5 | `/stature part @s arm.right 1 2 1`, first person, holding a sword | longer arm, sword at the hand | |
| R6 | R5 with a frame `first_person_arms: false` | vanilla arm | |
| R7 | `/stature part @s head 2 2 2`, carved pumpkin on head, F5 | pumpkin follows the head | |
| R8 | elytra equipped plus `part @s torso 2 1 1` | record whether the elytra follows; document it | |
| R9 | `/summon zombie ~ ~ ~ {CustomName:"Bob",CustomNameVisible:1b}`, then `/stature set @e[type=zombie,limit=1,sort=nearest] 8` | name tag above the head, shadow scaled | |
| R10 | `/stature set @e[type=zombie,limit=1,sort=nearest] 100 --dangerous`, look up and away | no popping when the box leaves the frustum | |
| R11 | `/stature set @s 0.0625`, walk into a wall, look at it from 0.1 blocks | no clipping through the wall (near plane) | |
| R12 | `/stature set @s 0.0625`, sprint on stone then release | stops like vanilla, no ice sliding (M1) | |
| R13 | framed micro player (micro override) against stone, iron bars, bamboo on a **dedicated** server | climbs and passes, no rubber-banding (M2) | |
| R14 | framed player: swim, crawl under a trapdoor, sleep in a **straw bed** (new in 26.3), sit on a **cushion** (new in 26.3) | camera inside the box; seated offset plausible | |
| R15 | 16× player walking through a forest including Poplar leaves (26.3) | trample limited, no lag spike, claims respected | |
| R16 | two clients, 16× footsteps at 10 / 50 / 200 blocks | audible and shaking near, nothing far; shake scales with the Screen Effect slider | |
| R17 | relog, die and respawn, change dimension with parts-only and framed states | everything persists as designed | |

## M5 manual checks

These are the multiplayer acceptance checks M5 added. They are separate from R1-R17, and they use
the same last-verified column.

| ID | Setup | Look for | Last verified (date, loader, commit) |
|---|---|---|---|
| M5-1 | Two clients: A at 16×, B at 200 blocks. Walk A at 10, 50 and 200 blocks from B | B hears and feels the steps at 10 and 50, nothing at 200; the shake follows B's Screen Effect slider; B receives no footstep packet at 200 blocks | |
| M5-2 | NeoForge dedicated server, 20 zombies at 16× walking for 60 s, then the same pen at 1× | average MSPT within ±5% of the 1× run (see the performance procedure) | |
| M5-3 | Non-op player at 16× walking through leaves and crops inside spawn protection, then inside a claim | nothing breaks in the protected area; a claim mod's cancellation is respected; a non-op cannot trample | |
| M5-4 | Grow 1× to 16× inside a 3-high tunnel, then inside a sealed pocket | the tunnel ends in open air with at most one compression hit and no death at full health; a truly encased giant takes compression once per second, and armour does not stop it | |
| M5-5 | `/stature set @s --dangerous 100`, then `/stature set @s 1` | the first reports the box at 16× with the model at 100×; the second gives a vanilla-size model, not a leftover 100× channel | |
| M5-6 | A vanilla client joins a Stature server: Fabric first, then NeoForge | Fabric refuses after about 10 s with "This server requires the Stature mod"; NeoForge refuses at login because its required channels are not registered. The server stays up and no wrong-size entity is drawn. A client with Stature still joins, and a handshake that cannot be sent logs a warning and lets the join through (fail-open) | |

## Peers matrix

| Client | Server | Expected |
|---|---|---|
| Stature, same loader | Stature | joins; size, channels, parts and thresholds sync |
| Stature | no Stature | joins; Stature sends nothing and the world stays vanilla-sized |
| no Stature | Fabric with Stature | refused after about 10 s (200 ticks); the server stays up |
| no Stature | NeoForge with Stature | refused during login by the required payload registration; the server stays up |

A Fabric client needs Fabric API either way. NeoForge has no companion API mod.

## Performance check procedure

The M5 acceptance target is no measurable tick-time regression from 20 macro zombies walking for
60 seconds. Use a NeoForge dedicated server and the same world for both runs.

1. Start the server with no other mods, a fixed view distance and simulation distance, clear
   weather, and the chunks around the pen force-loaded.
2. Build a fenced pen large enough for 20 zombies to walk, spawn them, and leave them at 1× for the
   baseline.
3. Let them walk for 60 seconds. Run `/debug start`, wait 60 seconds, then `/debug stop` and read
   the MSPT report. `spark` is an acceptable substitute: `/spark profiler start --timeout 60`.
4. Apply `/stature set @e[type=minecraft:zombie] 16` to the same pen and repeat the same 60-second
   run with the same player count and view distance.
5. Compare the average MSPT. The macro run passes when it is within ±5% of the baseline. Record the
   loader, commit, hardware, player count and both averages.

The cost being measured is the trample scan: every fourth tick, over the lower third of the box
plus the foot layer, capped at `trample_max_per_scan`. Leave the threshold flags at their defaults
for the comparison, and do not kill or replace the zombies between the two runs.

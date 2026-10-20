# Stature

Scale and anatomy for Fabric and NeoForge. A command or a datapack sets a living entity's uniform
scale, its per-axis box shape and its per-part silhouette; channels decide what else follows the
size, and the server owns every number the client draws. It is a lean replacement for Pehkui, built
for packs and for other mods to build on.

**Seen by tests, not yet by eyes:** the per-axis drawing path; [TESTING.md](TESTING.md) has the
manual checklist.

## Install

| | |
|---|---|
| Minecraft | 26.3, on Fabric or NeoForge |
| Java | 25 |
| Fabric | Fabric Loader 0.19.5+ with Fabric API 0.162.0+26.3 |
| NeoForge | 26.3.0.76+ |
| Armature | 1.1.0 or newer, required on both sides |
| Sides | client and server |

Put the matching Fabric or NeoForge Stature jar and the matching Armature jar in `mods/` on both
the client and the server. Stature will not load without Armature; the loader names the missing
dependency rather than failing later on a class it cannot find. Fabric also needs Fabric API;
NeoForge needs no companion API mod. Do not install Stature beside Pehkui or Pehkui Rebuilt; see
[compatibility](docs/reference/compatibility.md).

## Building

```cmd
.\gradlew.bat build
```

On Unix, `./gradlew build`. JDK 25 is required. The wrapper's daemon JVM is declared in
`gradle/gradle-daemon-jvm.properties` (Gradle's daemon JVM criteria), so an installed JDK 25 is
found rather than a machine path being committed.

Stature compiles against Armature 1.1.0 from Maven local first, then from
`https://maven.ellipog.dev`. Until that version is released, publish it once from the sibling
checkout:

```cmd
..\armature\gradlew.bat publishToMavenLocal
```

The build runs the game-free unit tests and the Fabric server gametests. The NeoForge gametest and
the Fabric client gametest are separate commands, documented in [TESTING.md](TESTING.md).

```cmd
.\gradlew.bat deployFabric
.\gradlew.bat deployNeoForge
```

copies the built jar into the test profiles named in `local.properties` (gitignored), removing the
previous copies first. Set `testModsDirFabric` and `testModsDirNeoForge` there, or pass them with
`-P`.

## Documentation

The manual lives in [docs/](docs/index.md), written for ellipog.dev.

| Page | What it covers |
|---|---|
| [Commands](docs/reference/commands.md) | Every `/stature` subcommand |
| [Frames](docs/scaling/frames.md) | The datapack frame format and its examples |
| [Channels](docs/scaling/channels.md) | The 48 built-in channels and override semantics |
| [Parts and regions](docs/scaling/parts-and-regions.md) | Rigs, part factors and hurt regions |
| [Thresholds](docs/scaling/thresholds.md) | The config file and the micro and macro mechanics |
| [Dangerous sizes](docs/reference/dangerous.md) | Sizes past the game's scale envelope |
| [Compatibility](docs/reference/compatibility.md) | Pehkui, claims, shaders and the client and server rules |
| [API](docs/api/index.md) | The supported facade for another mod |
| [API events](docs/api/events.md) | Change events and their thread and ordering guarantees |
| [Changelog](CHANGELOG.md) | What is on `master` so far |
| [Testing](TESTING.md) | The automated harness and the manual checklist |

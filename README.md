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
| Sides | client and server |

Put the matching Fabric or NeoForge jar in `mods/` on both the client and the server. Fabric needs
Fabric API; NeoForge needs no companion mod. Do not install Stature beside Pehkui or Pehkui
Rebuilt; see [compatibility](docs/compatibility.md).

## Building

```cmd
.\gradlew.bat build
```

On Unix, `./gradlew build`. JDK 25 is required. The wrapper's daemon JVM is declared in
`gradle/gradle-daemon-jvm.properties` (Gradle's daemon JVM criteria), so an installed JDK 25 is
found rather than a machine path being committed.

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
| [Commands](docs/commands.md) | Every `/stature` subcommand |
| [Frames](docs/frames.md) | The datapack frame format and its examples |
| [Channels](docs/channels.md) | The 43 built-in channels and override semantics |
| [Parts and regions](docs/parts-and-regions.md) | Rigs, part factors and hurt regions |
| [Thresholds](docs/thresholds.md) | The config file and the micro and macro mechanics |
| [Dangerous sizes](docs/dangerous.md) | Sizes past the game's scale envelope |
| [Compatibility](docs/compatibility.md) | Pehkui, claims, shaders and the client and server rules |
| [Changelog](CHANGELOG.md) | What is on `master` so far |
| [Testing](TESTING.md) | The automated harness and the manual checklist |

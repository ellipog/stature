# Stature

Scale and anatomy API for Fabric and NeoForge. The bottom of the Kindred / Kith / Stature stack,
and the only one of the three meant to be useful on its own: a modern, lean replacement for
Pehkui, for mod authors who want nothing to do with the other two.

Status: plan (`plan.md`, a local working document, deliberately not committed) plus Spike S2,
which passed on 26.3. The repo below is the scaffold; the frame work of plan §14.8 is next.

## Modules

    stature-api/       standalone public API for scale manipulation. Pure Java, no Minecraft
                       dependency: the only module a third-party mod compiles against.
    stature-common/    core matrix maths, lerping, attribute bridges. Game-free and headless
                       tested; recompiled into both loaders like armature/ and tenet/.
    stature-fabric/    Fabric mixins and entrypoints.
    stature-neoforge/  NeoForge capabilities and attachment hooks.

    spike-s2/          throwaway. Answered whether per-axis scaling is achievable (yes, twice
                       over) and is deleted once the frame work lands. Not part of the build.

Dependency direction, one way forever: `kindred-api → kith-api → stature-api`.

## Building

```cmd
gradlew build
```

Tests run as part of it: `gradlew build` includes `check` includes `test`.

```cmd
gradlew deployFabric
gradlew deployNeoForge
```

copies the built jars into the Modrinth App test profiles named in `gradle.properties`,
deleting the previous ones first.

## Toolchain

Minecraft 26.3, single target, no back-ports. Java 25. Fabric + NeoForge only. Same multiloader
shape as `armature/` and `tenet/`: Loom in no-remap mode (no mappings, 26.x is unobfuscated),
ModDevGradle with NeoForm 26.3, Parchment nowhere. Versions are pinned in `gradle.properties`;
`0.1.0` is a starting number for a repo with no releases.

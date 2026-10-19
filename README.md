# Stature

Scale and anatomy API for Fabric and NeoForge. The bottom of the Kindred / Kith / Stature stack,
and the only one of the three meant to be useful on its own: a modern, lean replacement for
Pehkui, for mod authors who want nothing to do with the other two.

## Frames

A frame is a named box a data pack declares, and the way a pack says what something should look
like without touching Java:

    data/<namespace>/stature/frames/<name>.json

```json
{
    "size": [ 0.55, 1.71 ],
    "scale": 0.95,
    "eye_height": 1.62,
    "easing": "cubic"
}
```

`size` is in blocks and is required: two numbers means "as deep as it is wide" (what vanilla already
is), three means a box that is deeper or shallower than it is wide. `scale`, `eye_height` and
`easing` are optional, and a field this version does not use yet is ignored with a note in the log
rather than refused, so a frame written for a later Stature still loads. Frames come from every pack
on the server and Stature ships none of its own, so a name is whatever the pack called it. The eye
height travels to clients with the shape, so cameras and nameplates agree everywhere.

A frame is absolute and the entity is not, so the conversion happens against the entity's own base
box: the same `0.55 x 1.71` frame on a player (0.6 x 1.8) and on a zombie (0.6 x 1.95) lands on
`0.55 x 1.71` both times. `scale` multiplies the frame, which is the plan's rule: a `1.2 x 3.6` frame
at `scale 2.0` is a `2.4 x 7.2` box.

A frame can also say where it belongs. Optional `entities` names exact entity types and
`entity_tags` names entity-type tags, with or without the leading `#`. The winner is explicit
choice first, then entity tag, then exact entity type, then vanilla; tags outrank exact types by
design. New entities are assigned on their first server tick, explicit commands and hand sizes are
never overridden, `/stature frame clear` is an explicit return to vanilla, and `/stature reload`
re-evaluates automatic frames without touching explicit choices. An explicit size also survives
death, and a frame whose file is gone is reported as no longer loaded rather than silently kept.
Your own size is re-sent to you
after a relog and then kept fresh: broadcasts and tracking cover every viewer except yourself,
because your own player entity never triggers tracking.

```cmd
/stature frame list                                  what the packs declared
/stature frame apply <targets> <frame> [ticks]        put one on an entity
/stature frame clear <targets>                        back to vanilla proportions
/stature reload                                      re-read the packs
```

Verified on a 26.3 dedicated server with a fixture pack: `slender` (0.55 x 1.71) on a zombie gives
`0.550 x 1.710 x 0.550`; `dwarf` (0.9 x 0.9 x 0.45) gives `0.900 x 0.900 x 0.450`, shallower than it
is wide; `titan` (1.2 x 3.6 at scale 2.0, elastic) overshoots to `2.125x` mid-ease and lands on
`2.400 x 7.200 x 2.400`; an unknown name is refused by name; `clear` restores `0.600 x 1.950 x
0.600`. Two deliberately broken fixtures are named and skipped without taking the good ones with
them. A frame survives a restart: the size and the frame's name are saved with the entity, and the
box is right the moment it loads.

Automatic assignment was measured too: a zombie took its exact-type frame, while a cow took its tag
frame even though an exact-type frame also claimed cows; an explicit `frame apply` overrode the tag;
`frame clear` stayed vanilla; and removing then restoring a declaration updated one automatic entity
on reload while explicit and manual entities were left alone.

## What works today

The scale backbone (§14.1) and the box's shape (§14.2). A size change eases over time instead of
snapping, the box follows it tick by tick, reach and step height travel with it, and an entity can
be narrower than it is tall or deeper than it is wide.

```cmd
/stature set  <targets> <scale>                  instantly
/stature set  <targets> <scale> <ticks>          eased, ease-out-cubic
/stature set  <targets> <scale> <ticks> <easing> linear, cubic or elastic
/stature size <targets> <width> <height> <depth> [ticks]   the box's proportions
/stature get  [<target>]                         the scale, the shape and the resulting box
```

The scale itself rides the game's own `scale` attribute, so pathfinding, collision and camera
height come with it; reach and step are written from *their own defaults* times the scale, never
from their current value, so repeated writes cannot compound. The shape cannot ride an attribute --
vanilla's dimensions are one width used for both X and Z -- so it lives on the entity and is sent to
clients over a packet of Stature's own, which is what keeps a client's box from disagreeing with the
server's.

Verified on a 26.3 dedicated server, on **both loaders**. The first three rows are identical on
Fabric and NeoForge; the fourth is one sample taken while the shape was still easing, so its exact
numbers depend on when it was read.

| Command | Reported box (X x Y x Z) |
|---|---|
| `size 1 1 1` | `0.600 x 1.950 x 0.600` (vanilla) |
| `size 0.5 1.2 2.0` | `0.300 x 2.340 x 1.200` |
| then `set 2.0` | `0.600 x 4.680 x 2.400` |
| `size 1 1 1 20` | `0.832 x 4.379 x 1.937` mid-ease, landing on `1.200 x 3.900 x 1.200` |

Physics: the box is rebuilt on the same tick as the write, rather than a tick later when vanilla's
own poll would notice. Depth is verified rather than assumed -- `0.300` wide against `1.200` deep
cannot be the width used twice.

**Seen by tests, not yet by eyes:** the per-axis *drawing* half. 26.3 renders a state rather than an
entity, so the shape is copied onto `LivingEntityRenderState` in `extractRenderState` and applied
where `submit` writes the matrix. The two client mixins are green on the compiler and traced against the
26.3 source line by line, and `RenderMixinContractTest` now pins every method and field their
descriptors name, so a Mojang rename fails the build instead of a client at startup. Eases are
blended per frame from the last two ticks, so the model glides at display rate while the hitbox
steps at tick rate, exactly vanilla's own split between interpolated looks and exact physics.
What no test can do is look at the picture: a dedicated server never loads these classes, so
`runClient` plus `/stature size @s 0.5 1.2 2.0` is still what settles whether the model follows
the box.

## What is next

- **The rest of a frame's fields.** `size`, `scale`, `eye_height`, `easing`, `entities` and
  `entity_tags` are read today; `channels` (§14.9), `parts` (§14.7), `regions` (§14.7) and `thresholds`
  (§14.3) are ignored with a note until the subsystems they name exist. Mods that size other mods'
  entities get their own precedence level through `FrameService.setIntegrationFrame`: below an
  operator's choice, above every pack declaration, re-asserted by the owner rather than
  re-evaluated, and named for what it is rather than for any one consumer.
- **The per-axis render half** needs a client run to be believed.
- **§14.9 channels** and **§14.7 per-part**, after that.

`plan.md` is the plan (a local working document, deliberately not committed). `spike-s2/` is the
throwaway that answered whether per-axis scaling is possible at all: it is, in two halves.

## Modules

    stature-api/       standalone public API for scale manipulation. Pure Java, no Minecraft
                       dependency: the curves and the envelope, and the only module a third-party
                       mod compiles against.
    stature-common/    the implementation: the scale service, the mixin, the command tree.
                       Game-free where it can be, headless tested, compiled into both loaders.
    stature-fabric/    Fabric entrypoint and command registration.
    stature-neoforge/  NeoForge entrypoint and command registration.

    spike-s2/          throwaway. Not part of the build; delete it once the frame work lands.

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

copies the built jars into the Modrinth App test profiles named in `gradle.properties`, deleting
the previous ones first.

## Toolchain

Minecraft 26.3, single target, no back-ports. Java 25. Fabric + NeoForge only. Same multiloader
shape as `armature/` and `tenet/`: Loom in no-remap mode (no mappings, 26.x is unobfuscated),
ModDevGradle with NeoForm 26.3, Parchment nowhere. Versions are pinned in `gradle.properties`;
`0.1.0` is a starting number for a repo with no releases.

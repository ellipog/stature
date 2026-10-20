# Changelog

All notable changes to Stature are recorded here. The format is [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and the project follows semantic versioning once it has a release.

## [Unreleased]

Stature has not been released yet. This section covers everything on `master` so far.

### Added

- The `/stature` command tree: `set`, `reset`, `size`, `frame apply`, `frame clear`,
  `frame list`, `channel list`, `channel <id> <value>`, `channel <id> reset`, `part list`,
  `part reset`, `part <name> <x> <y> <z>`, `part <name> reset`, `get` and `reload`.
- Datapack frames under `data/<namespace>/stature/frames/<name>.json`, with a size in blocks,
  an optional scale, eye height, easing, assignment by entity type or entity type tag, and a
  precedence rule that puts an explicit command choice above every pack declaration.
- Datapack rigs under `data/<namespace>/stature/rigs/<name>.json`, part groups with model paths
  and fractional boxes, a built-in humanoid rig, and parent rigs that extend it.
- Per-part silhouette factors, including first-person arms, saved with the entity and synced to
  viewers.
- Hurt regions on frames: a rig group, a damage multiplier and a tag, resolved from projectile
  impact points or a validated melee view ray.
- Forty-eight built-in channels for reach, step, motion, jump, flight, safe fall, combat, defence,
  health, AI ranges, physics constants, knockback, saturation, rendering, projectiles, pickup
  reach, riding offsets, explosions and drops.
- `config/stature.json` with micro and macro edges and flags for porous traversal, surface
  climbing, amplified knockback, trampling, seismic footsteps and doorway compression.
- Trampling that respects claims, spawn protection and mob griefing, with a per-scan cap and an
  option for drops.
- Seismic footsteps and nearby camera shake, with the shake following the vanilla Screen Effect
  slider.
- Dangerous sizes from 0.001 to 1000, beyond the game's 0.0625 to 16 scale envelope.
- Fabric and NeoForge builds from one shared implementation, with a Fabric handshake and
  NeoForge required payloads so a client without Stature is refused rather than shown wrong-size
  entities.
- A supported API facade in `dev.ellipog.stature.common.api`: `StatureApi` reads and changes sizes,
  shapes, channel overrides and integration frames, and `StatureEvents` reports scale, frame,
  channel and threshold-tier changes with a cause. The `stature-api` artifact stays pure Java value
  types; everything outside the facade package is internal.
- Five M10 channels, each with a config switch, strength and min/max clamps, per-entity overrides
  and frame support: `projectile_scale`, `item_pickup_range`, `riding_offset`, `explosion_scale`
  and `drops_scale`.
- Projectiles fired by a scaled entity inherit its size, hitbox, damage and launch speed. The size
  is saved with the projectile and the damage is applied once at the hurt funnel.
- Item and experience pickup scans use the scaled reach of `item_pickup_range`.
- Mounts can tune the scale handed to their rider attachment with `riding_offset`.
- Explosions caused by a living entity or by TNT it primed scale their radius with
  `explosion_scale`; unattributed explosions stay vanilla.
- Loot-table drops from a scaled entity multiply their stack counts with `drops_scale`. Only
  stackable items scale, counts never fall below vanilla, and equipment is untouched.
- The `stature:scale` entity-selector option, using vanilla range syntax such as
  `@e[stature:scale=0.5..2]`, on both loaders.
- Frames can set any declared channel through a `channels` object, and clear the overrides they
  introduced when replaced.
- A per-tick frame-file cache and a near-1x threshold skip. Both are behaviour-preserving: the
  cache is keyed by tick and invalidated on a frame change or registry reload, and the threshold
  guard still runs whenever the entity's own resolved edges put it in a tier.
- `/stature preset <targets> <preset> [<ticks>]`, `/stature preset --self <preset> [<ticks>]` and
  `/stature preset list`, with built-in size presets (`tiny`, `small`, `normal`, `big`, `giant`,
  `titan`) and shape presets (`tall`, `slim`, `wide`). Presets complete, an unknown name points at
  `preset list`, and applying one is an explicit size choice like `set` or `size`.
- Config-defined presets: pack makers can add their own, replace a built-in by using its name, or
  remove names with `presets_disabled`; `presets_enabled` switches the whole feature off. Bad
  entries are logged and skipped, and `/stature reload` picks up edits.
- Target-less self forms: `/stature set --self`, `/stature preset --self`, `/stature get` with no
  target, `/stature reset` and the target-less `/stature grow`/`shrink`. `--self` is used where an
  omitted target could be read as a player name. The forms ask for
  `self_command_permission_level` (default 2) and can be switched off with
  `self_shortcuts_enabled`.
- `/stature grow [<targets>] [<factor>]` and `/stature shrink [<targets>] [<factor>]`, defaulting to
  2x and 0.5x, clamped to the normal 0.0625 to 16 envelope and switchable with
  `relative_commands_enabled`. They reuse the existing bounds and dangerous-model cleanup.
- Friendlier result lines: preset, grow and shrink successes say what changed and when a value was
  held at an envelope edge.

### Changed

- Stature now requires Armature 1.1.0 or newer on both sides. The loader seam, networking and
  config file reader are Armature's; the scale, channels, frames, collision, attribute bridges,
  matrix maths and the public API stay Stature's.
- `config/stature.json` keeps its path, keys, defaults and semantics, but is now read through
  Armature's config reader. A malformed file is reported and left exactly as found, and bad values
  keep their previous ones.
- Payloads are declared once through `ArmatureNetwork`; the per-loader registration and the
  NeoForge client payload event are gone. Tracking and nearby sends still use the narrow scopes
  M5 introduced.
- Unset channels now follow the scale only where the game does not already scale the value:
  reach, step, motion, jump, flight and safe fall follow; combat, defence, health, AI ranges and
  physics constants stay vanilla unless overridden.
- `stature:name_tag_height` was renamed to `stature:below_name_distance`. Saved overrides under
  the old id still load.
- Size, channel, part and threshold packets go to the players tracking the entity plus the entity
  itself, instead of the whole dimension.
- Frame and rig data reload on vanilla `/reload` as well as `/stature reload`. The config file is
  re-read only by `/stature reload`.
- Dangerous sets track their model override so a later plain `set` clears it, while a hand-set
  model survives.
- Scale reads and writes use the base value of `minecraft:scale`, so another mod's modifier
  multiplies on top without being folded in.
- Permission level 2 now governs the commands that can change another entity, while the new
  self-only forms ask for `self_command_permission_level` (default 2). The default is unchanged;
  a pack can lower only the self forms.

### Removed

- The `stature:voice_pitch` channel. Voice mods compute pitch from height themselves. A world that
  saved the old id still loads: the unknown channel is skipped quietly.
- `StaturePlatform`, `StatureNetworking`, both loader network classes, the NeoForge client payload
  handler class, and the hand-written config reader. Their jobs are Armature's now.

### Fixed

- A parts-only save keeps its factors through a chunk unload, a restart and a dimension change.
- The first-person arms flag is re-derived from the frame on load instead of being trusted from a
  stale save.
- A frame's eye height stays inside the box in every pose, not only while standing.
- Eased sizes interpolate per frame instead of stepping at 20 Hz, and the inventory paperdoll
  stays at normal size.
- Held items follow the hand, first-person arms follow their part factors, and oversized models
  no longer pop out of view when their box leaves the frustum.
- Trample scans are throttled and capped, and a break is refused when the loader's permission
  seam says no.
- Growing into solid rock searches outward for air and hurts at most once per transition instead
  of once per eased tick.
- A malformed config packet is rejected whole, so the client cannot apply half a threshold set.
- The client starts with the part-pose mixin applied. Its shared helpers live in `PartPoseScope`
  instead of as non-private static methods on a mixin, which Mixin refuses. Found by running the
  Fabric client gametest.

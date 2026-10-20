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
- Forty-three built-in channels for reach, step, motion, jump, flight, safe fall, combat, defence,
  health, AI ranges, physics constants, knockback, saturation and rendering.
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

### Changed

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

### Removed

- The `stature:voice_pitch` channel. Voice mods compute pitch from height themselves. A world that
  saved the old id still loads: the unknown channel is skipped quietly.

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

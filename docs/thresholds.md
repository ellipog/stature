---
maturity: unreleased
---

# Thresholds

A [[threshold|threshold]] is one of the two scale edges that decide when the tier mechanics run. A
[[micro|micro]] entity is below the micro edge; a [[macro|macro]] entity is above the macro edge.
The server owns the edges and the mechanic flags, and sends the resolved answer to clients so
movement prediction agrees on both sides.

## The config file

```
config/stature.json
```

The server creates the file with its defaults on first start. It is re-read by
`/stature reload`, not by vanilla `/reload`, because it is not datapack data.

```json
{
  "micro_threshold": 0.25,
  "macro_threshold": 2.5,
  "porous_traversal": true,
  "surface_climbing": true,
  "amplified_knockback": true,
  "trampling": true,
  "trample_max_per_scan": 8,
  "trample_drops": true,
  "seismic_footsteps": true,
  "doorway_collision": true,
  "projectile_scale": true,
  "projectile_scale_strength": 1.0,
  "projectile_scale_min": 0.25,
  "projectile_scale_max": 4.0,
  "projectile_damage_strength": 1.0,
  "projectile_speed_strength": 0.5,
  "item_pickup_range": true,
  "item_pickup_range_strength": 1.0,
  "item_pickup_range_min": 0.25,
  "item_pickup_range_max": 4.0,
  "riding_offset": true,
  "riding_offset_strength": 1.0,
  "riding_offset_min": 0.25,
  "riding_offset_max": 4.0,
  "explosion_scale": true,
  "explosion_scale_strength": 1.0,
  "explosion_scale_min": 0.25,
  "explosion_scale_max": 3.0,
  "drops_scale": true,
  "drops_scale_strength": 1.0,
  "drops_scale_min": 1.0,
  "drops_scale_max": 3.0
}
```

| Key | Default | What it does |
|---|---|---|
| `micro_threshold` | 0.25 | The upper bound for a micro entity. Must be above 0 and below `macro_threshold` |
| `macro_threshold` | 2.5 | The lower bound for a macro entity |
| `porous_traversal` | true | Lets micro entities pass through fences, bars and bamboo |
| `surface_climbing` | true | Lets micro entities climb a wall they are pressed against |
| `amplified_knockback` | true | Lets the received-knockback channel follow the scale |
| `trampling` | true | Lets macro entities break fragile blocks underfoot |
| `trample_max_per_scan` | 8 | The most blocks one trample scan may destroy. A whole number of 1 or more |
| `trample_drops` | true | Whether trampled blocks drop their items |
| `seismic_footsteps` | true | Louder, deeper steps and camera shake for macro entities |
| `doorway_collision` | true | Compression damage for a macro entity whose head is inside a full block |
| `projectile_scale` | true | Projectiles fired by a scaled entity inherit its size, hitbox, damage and launch speed |
| `item_pickup_range` | true | Items and experience orbs are picked up from the scaled reach |
| `riding_offset` | true | A mount's passenger attachment follows the riding offset channel |
| `explosion_scale` | true | Explosions with a living cause use the scaled radius |
| `drops_scale` | true | Loot-table stack counts use the scaled count |
| `projectile_scale_strength` | 1.0 | Multiplier on the followed projectile factor. 0 to 4 |
| `projectile_scale_min` | 0.25 | Lower clamp on the projectile factor, including overrides. Above 0 |
| `projectile_scale_max` | 4.0 | Upper clamp on the projectile factor. At least the minimum |
| `projectile_damage_strength` | 1.0 | Linear damage strength: `1 + (factor - 1) * strength`. 0 to 4 |
| `projectile_speed_strength` | 0.5 | Launch-speed exponent: `factor ^ strength`. 0 to 2 |
| `item_pickup_range_strength` | 1.0 | Multiplier on the followed pickup factor. 0 to 4 |
| `item_pickup_range_min` | 0.25 | Lower clamp on the pickup factor. Above 0 |
| `item_pickup_range_max` | 4.0 | Upper clamp on the pickup factor. At least the minimum |
| `riding_offset_strength` | 1.0 | Multiplier on the followed seat factor. 0 to 4 |
| `riding_offset_min` | 0.25 | Lower clamp on the seat factor. Above 0 |
| `riding_offset_max` | 4.0 | Upper clamp on the seat factor. At least the minimum |
| `explosion_scale_strength` | 1.0 | Multiplier on the followed explosion factor. 0 to 4 |
| `explosion_scale_min` | 0.25 | Lower clamp on the explosion factor. Above 0 |
| `explosion_scale_max` | 3.0 | Upper clamp on the explosion factor. At least the minimum |
| `drops_scale_strength` | 1.0 | Multiplier on the followed drops factor. 0 to 4 |
| `drops_scale_min` | 1.0 | Lower clamp on the drops factor. 1.0 means never less than vanilla |
| `drops_scale_max` | 3.0 | Upper clamp on the drops factor. At least the minimum |

`comment` is accepted and ignored so an older default file does not log an unknown key. Any other
unknown key is logged and skipped. A malformed file is reported and the previous values are kept.
The two edges must satisfy `0 < micro < macro`; a frame can move either edge for its entities.

An entity is micro when its uniform scale is strictly below its micro edge, and macro when it is
strictly above its macro edge. The box's shape does not decide the tier, only the uniform scale
does.

## The M10 channel options

The five feature flags above are also the per-entity switches: a frame may set one in its
`thresholds` object, or a resolved threshold packet can carry it. When a flag is false, the
matching channel returns exactly 1.0 and the game behaves as vanilla, even if an override is
stored. The override is kept for when the feature is switched back on.

Each feature has a numeric family in the same file:

- `<channel>_strength` multiplies the followed value. It does not multiply an explicit per-entity
  override; an override is already a deliberate choice.
- `<channel>_min` and `<channel>_max` clamp the final factor, including an override, because these
  factors can change hitboxes, explosion radii and drop counts. A minimum above its maximum keeps
  the previous pair.
- `projectile_damage_strength` and `projectile_speed_strength` control the two secondary effects of
  the projectile channel. Setting either to 0 leaves that part vanilla while the size still
  follows.
- `drops_scale_min` defaults to 1.0 so a small entity never gets less loot than vanilla. Its
  channel exponent is 0.5, so the default count growth is a square root and the default maximum is
  3x.

All of these values are in the same server config packet as the edges and flags. A joining client
gets them on login, `/stature reload` resends them, and the debug screen's config packet count is
unchanged. They are not per-frame numeric overrides; a frame sets an override through its
`channels` object instead.

## Frame overrides

A frame can override the edges and any flag for the entities it sizes:

```json
"thresholds": {
  "micro": 0.2,
  "macro": 3.0,
  "trampling": false
}
```

A value the frame gives wins over the config file for that entity; a value it omits falls back to
the file. The server resolves the whole answer and sends it to the clients tracking the entity, so
a client never needs the frame registry to gate movement. A known flag with a non-boolean value
fails the frame file; an unknown flag name is logged and skipped.

## The mechanics

All of these are off for an ordinary entity: the check is a flag lookup and a tier comparison, and
nothing else runs.

### Porous traversal

A micro entity passes through fences, bars and bamboo as if their collision were empty. Glass
panes stay solid on purpose: a window is a wall even to something small. Pathfinding reads the
same shapes, so a micro mob routes through a fence instead of around it.

### Surface climbing

A micro entity that is pressed against a wall can climb it like a ladder. The check runs on both
sides, so the client predicts the same climb the server allows.

### Amplified knockback

`knockback_received` follows the scale by default, at `scale ^ -0.5`, so a giant shrugs off a
shove and a small entity flies further. Turning the flag off makes the received factor exactly 1.0
unless it is overridden; it does not affect `knockback_dealt`, which still follows at
`scale ^ 0.5`.

### Trampling

A macro entity breaks fragile blocks underfoot:

- Leaves, crops and glass panes. Panes are matched through the `c:glass_panes` convention tag plus
  the two vanilla pane blocks, so modded panes count too.
- The scan runs every fourth tick, staggered by entity id, and reads only the lower third of the
  box plus the block just below its feet. Leaves at head height are not something a foot touched.
- At most `trample_max_per_scan` blocks break per scan. `trample_drops` decides whether they drop.
- Every break asks the loader's permission seam first. A player must pass `mayBuild`, the level's
  `mayInteract` check and the loader's break event, so spawn protection and claim mods can veto a
  break. A mob must pass the vanilla `mob_griefing` rule and, on NeoForge, its own grief hooks.

### Seismic footsteps

A macro entity's steps are louder and deeper. The step volume is `base * scale`, capped at 4.0,
and the pitch is `base / sqrt(scale)`. A footstep packet goes to players within
`clamp(16 * volume, 16, 64)` blocks, which follows vanilla's own 16-blocks-per-volume rule. The
camera shake reaches at most 48 blocks, falls off with distance squared, and is multiplied by the
vanilla Screen Effect slider, so a player who has turned screen effects off feels nothing.

### Doorway compression

Once per second, a macro entity whose eye is inside a full collision block takes 2.0 damage of
type `stature:compression`. The check skips crouching and swimming, because those poses already
shrank the box, and skips entities already inside a wall, so suffocation is not charged twice.
Compression bypasses armour and its death message says the entity was crushed by their own size.

## Reloading and syncing

`/stature reload` re-reads the config file, frames and rigs, then refreshes every entity's
resolved thresholds and resends the config to connected players. Vanilla `/reload` rebuilds the
frame and rig registries and refreshes the per-entity thresholds, but leaves the config file
alone. A joining player is sent the config values on login, and a framed entity's resolved values
are sent when the player starts tracking it and whenever the answer changes.

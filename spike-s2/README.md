# Spike S2

Throwaway. It answers one question and then it is deleted: **is per-axis (non-uniform) entity
scaling achievable on Minecraft 26.3, and do the mixins even apply in this toolchain?**

`FINDINGS.md` is the answer, read out of the 26.3 sources. This folder is the boot that confirms it.

## What is in here

Four mixins, and nothing else.

| File | What it does | Where it lands |
|---|---|---|
| `mixin/EntityMixin.java` | gives every entity a depth multiplier and builds the box from it | `Entity.makeBoundingBox(Vec3)` (the no-arg one is `final` in 26.3 and delegates here) |
| `mixin/LivingEntityMixin.java` | width and height independently | `LivingEntity.getDimensions(Pose)`, which is `final`, which is why the spike exists |
| `mixin/client/LivingEntityRenderStateMixin.java` | room on the render state for three factors | `LivingEntityRenderState`, which carries one `scale` float |
| `mixin/client/LivingEntityRendererMixin.java` | copies them onto the state, then uses them | `extractRenderState` and `submit` |

The split of the last two is 26.3's shape rather than a choice: this version renders a **state**, not
an entity, so `submit` never sees the entity it is drawing and the factors have to travel on the
state. A single redirect on `entity.getScale()` cannot do it.

Depth is a multiplier of the entity's own width, because vanilla's footprint is square: `1.0` is
vanilla, `2.0` is twice as deep as it is wide, `0.5` is half. Width and height multiply what the game
already composed, so all three stack with age and with `generic.scale` rather than replacing either.

## Running it

Two ways, and they answer different halves.

**The self-test, which needs no eyes.** A dedicated server creates a zombie, applies the multipliers,
prints the numbers and stops itself:

    ./gradlew runServer

Then read the log for `[S2]`. What it proves: that the mixins applied at all, that a zombie's box
really becomes `0.300 x 2.340 x 0.300` with width and height apart (2a), that it becomes
`0.300 x 2.340 x 0.600` with depth apart as well (2b), and that the entity survives five ticks in that
shape. It cannot reach a client renderer, so the two client mixins are compile-verified only until
somebody runs the client.

**The client, which needs eyes.** `./gradlew runClient`, then:

    /s2 set 0.5 1.2 2.0

on yourself. Read the chat line for the multipliers and the log for the box, and look at the model:
width, height and depth should each move on their own. That is the whole of tier 2, visible.

## What it cannot decide

Whether the numbers are good is a design question rather than a platform one, and nothing here touches
the real work: the six call sites in `FINDINGS.md` that pass width where depth belongs, the frame
registry, saving and syncing a rectangular footprint, or NeoForge. The spike answers "can it be done
at all", which is what §14.2's risk was, and stops.

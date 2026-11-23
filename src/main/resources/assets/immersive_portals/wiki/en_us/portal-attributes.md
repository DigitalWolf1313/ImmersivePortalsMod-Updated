---
title: "Portal Attributes"
category: "Portals"
order: 6
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/Portal-Attributes.html"
updated: 2026-11-22
manual: true
---

These are the attributes that a portal has.

You can edit them by `/portal set_portal_nbt` command. (Don't use `/data` command)

[Portal Customization](wiki:portal-customization)

### Body Attributes

#### Orientation Axis

Two unit vectors that are perpendicular to each other. These two vectors define the orientation of the portal.

NBT tags:`axisWX` `axisWY` `axisWZ` `axisHX` `axisHY` `axisHZ`

It's recommended to use command `/portal rotate_portal_body` to edit this attribute.

#### Width, Height

Width is the portal shape's length along the direction of `axisW`. Height is the portal shape's length along the direction of `axisH`.

NBT tags: `width` `height`

It's recommended to use command `/portal set_portal_size` to edit this attribute.

### Transformation Attributes

#### Destination Dimension

The dimension id of the portal's destination dimension.

NBT tag: `dimensionTo`

#### Destination

The destination position.

NBT tag: `destinationX` `destinationY` `destinationZ`

#### Rotation Transformation

Optional. A quaternion that defines the portal's rotation transformation.

NBT tag: `rotationA` `rotationB` `rotationC` `rotationD`

#### Scale Transformation

A number that defines the portal's scale transformation.

NBT tag: `scale`

### Teleportation Attributes

#### Whether Teleportation Changes Scale

Whether the teleportation changes entity scale if the portal has a scale transformation. It requires Pehkui mod to work.

NBT tag: `teleportChangesScale`

Example: Make a portal that has scaling to not change the crossing entity's scale `/portal set_portal_nbt {teleportChangesScale:false}`

#### Whether Teleportation Changes Gravity Direction

Whether the teleportation transforms the player's gravity location if the portal has a rotation transformation. It requires Gravity Changer (Gravity API) mod to work.

NBT tag: `teleportChangesGravity`

#### Whether to Adjust The Entity's Position After Teleporting

If true, the player will be moved up if the player is inside the ground after teleportation.

NBT tag: `adjustPositionAfterTeleport`

#### Whether to Have Cross Portal Collision

If an entity touches a portal, its collision will be specially handled, which includes:

- The outer collision. Things behind the portal cannot collide with that entity.
- The inner collision. Things "inside" the portal can collide with that entity.

This attribute defines whether the inner collision is being handled.

NBT tag: `hasCrossPortalCollision`

#### Commands to Invoke After Teleporting

Optional. You can specify a list of commands to be invoked for entities that teleport through this portal. The command sender will be the entity that teleported.

The commands will be invoked with level-2 permission. Using command `/portal set_portal_nbt` command to change this requires level-2 permission.

NBT tag: `commandsOnTeleported`

Example: Make the portal to damage the entities that cross this portal `/portal set_portal_nbt {commandsOnTeleported:["/effect give @s minecraft:instant_damage 1"]}`

If the command contains quotation mark `"`, you need to change it to `\"`. Example: `/portal set_portal_nbt {commandsOnTeleported:["/say \"hi\""]}`

### Accessibility Attributes

#### Teleportable

If set to false, you cannot teleport through but can still see through.

NBT tag: `teleportable`

#### Interactable

Whether the player can place or break blocks through the portal.

NBT tag: `interactable`

#### Visibility

Whether the portal is visible. An invisible portal can still do teleportation, cross-portal collision and chunk loading.

This is different to the vanilla entity visibility (the armor stand's `Invisible` tag). In the portal gun mod, when a portal is inactive, it's invisible but its vanilla visibility is still on, the overlay still renders.

NBT tag: `isVisible`

#### Specific Accessor

Optional. The UUID of the specific player that can access this portal. If it's present but the value is null, the portal cannot be accessed by any player but can be accessed by non-player entities.

NBT tag: `specificPlayerId`

### Default Portal Animation {#portal-default-animation}

All portals by default will change smoothly when you use `/portal` commands to change the portal's attributes (except for the global portals).

Following things can be animated:

- Portal position, destination
- Portal orientation
- Portal rotation transformation
- Portal scale transformation
- Portal width and height (If the portal has a special shape, then width and height cannot be animated)

NBT tag: `animation`

> Start from 2.3.0, the tag becomes `defaultAnimation`.

The `animation` tag has two sub-tags: `curve` and `durationTicks`. `curve` can be `linear`, `sine`, `sineFlipped`, `circle` or `easeInOutCubic`. The `curve` does not change trajectory, it only determine the speed change during animation.

Example:

- Disable portal animation: `/portal set_portal_nbt {animation:{durationTicks:0}}`
- Give the portal a linear animation with duration of 2 seconds: `/portal set_portal_nbt {animation:{curve:"linear",durationTicks:40}}`

The portal animation is client-side. On the server side the portal moves abruptly. The animation can be smooth when the networking condition is not good.

The portal animation can be triggered by `/portal` commands. Using `/tp` or `/data` command won't trigger the animation.

The default animation is visual-only, so there is no relative teleportation or collision. If a portal moves through you, you won't get teleported. [Custom animations](wiki:portal-animation#relative-teleportation) do support relative teleportation, and so do animations recorded with the [portal wand](wiki:animate-wand).

### Bind-Cluster

This mod's every portal entity is one-way and one-faced. A bi-way bi-faced nether portal consists of 4 portal entities. That 4 portal entities are called a portal cluster. To edit that bi-way bi-faced portal, 4 portal entities needs to be changed.

By enabling `bindCluster`, if you edit one portal entity using `/portal` commands, the other 3 portals will also be updated accordingly. This makes portal manipulation easier.

NBT tag: `bindCluster`

### Rendering Attributes

#### Fuse View

If true, the portal rendering will not render the sky background and maintain the depth value in the portal view area. This results in the things inside the portal and the things outside the portal being fused together.

![](images/portal-attributes-1.png)

NBT tag: `fuseView`

Example: Make the portal fuse-view `/portal set_portal_nbt {fuseView:true}`

#### Rendering Mergable

If true, when the portal touches another portal that has the same spacial transformation, these portals will be "grouped" and the rendering performance will be improved which result in higher FPS. But the front clipping functionality does not work normally when rendering the grouped portal.

NBT tag: `renderingMergable`

#### Whether to Render Yourself

If false, you cannot see yourself (myself) from the portal.

NBT tag: `doRenderPlayer`

#### Whether to Render Iris Shadow

If false, the portal surface does not cast shadows while a shader pack is enabled. It requires Iris and the [Include Portals in Iris Shadows](wiki:config-options#include-portals-in-iris-shadows) option. It is true by default.

![Portal shadows enabled](images/iris_shadow_on.png)

![Portal shadows disabled with renderIrisShadow:false](images/iris_shadow_off.png)

NBT tag: `renderIrisShadow`

Example: Make the portal not cast shadows `/portal set_portal_nbt {renderIrisShadow:false}`

The command stick has the entries `Enable Portal Shadows` and `Disable Portal Shadows`.

### Light Attributes

#### Light Level

Makes the portal emit block light. The light comes from invisible light blocks that are kept around the portal's plane. The value is from 0 to 15 and 0 means no light.

NBT tag: `lightLevel`

Example: Make the portal emit light level 12 `/portal set_portal_nbt {lightLevel:12}`

The command stick has entries `Portal Light Level 1` to `Portal Light Level 15` and `Portal Light Off`.

- The light blocks are only placed into air. They are removed when the portal is removed and are repaired if something breaks them.
- One portal owns at most 450 light blocks.
- The light blocks are saved with the portal, so leftovers are cleaned up after a restart.
- A moved or dragged portal updates its light blocks in the same tick.
- The two faces of a bi-way portal do not fight over light blocks.

[`/portal copy_light_to_destination`](wiki:commands-reference#portal-copy-light-to-destination) copies the existing light around a portal onto the destination side instead of using one uniform level. These per-cell levels follow the portal when it moves, and setting a new `lightLevel` replaces them.

![Light copied to the destination side of a portal](images/portal_copy_light.png)

### Additional Attributes

#### Motion Affinity

If it's positive, then players colliding with the portal will be accelerated in the portal's facing direction. If it's negative, the player will be decelerated when moving fast.

NBT tag: `motionAffinity`

## Breakable Portal Attributes

Only exists for [breakable portals](wiki:portals#breakable-portals).

#### Unbreakable

If true, the portal won't break if the frame structure is broken.

NBT tag: `unbreakable`

Example: Make a nether portal unbreakable `/portal set_portal_nbt {unbreakable:true}`

### Overlay Attributes

#### Overlay Block State

Optional. It's the portal overlay block's block state.

NBT tag: `overlayBlockState`

#### Overlay Opacity

Overlay's opacity. Between 0 and 1.

NBT tag: `overlayOpacity`

#### Overlay Offset

The overlay offset along the portal's facing direction.

NBT tag: `overlayOffset`

Example: will give it a diamond block overlay with opacity 0.3 and offset -0.4 `/portal set_portal_nbt {overlayBlockState:{Name:"minecraft:diamond_block"}, overlayOpacity:0.3, overlayOffset:-0.4}`

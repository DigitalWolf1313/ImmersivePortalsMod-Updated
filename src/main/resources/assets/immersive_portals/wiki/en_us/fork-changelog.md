---
title: "Fork Changelog"
category: "Getting Started"
order: 2
description: "The latest updates of this fork."
updated: 2026-11-21
manual: true
---

## 6.4.0

:::tip Highlights in this update:
Added a new Animation portal wand, portals that emit light, an ingame wiki and several rendering and performance improvements.
:::

- **Added the Animation portal wand.** Drag the portal to a state, add a keyframe, and the wand builds a portal animation from the keyframes. Each keyframe has its own duration and easing, animations can loop, and both sides of a bi-way portal can be edited. Several portals can be linked to share one animation. See [Animate Portal Wand](wiki:animate-wand).
- **Portals can emit light.** A portal with `lightLevel` from 1 to 15 keeps invisible light blocks around its plane, so a portal can light up its surroundings. The command stick has entries for every level and for turning the light off. [`/portal copy_light_to_destination`](wiki:commands-reference#portal-copy-light-to-destination) copies the existing light around a portal onto the destination side. See [Light Attributes](wiki:portal-attributes#light-attributes).
- **Added an in-game wiki.** You're reading it right now! Open it inside the config screen or the by default unbound key. The pages work offline and the images are downloaded the first time they are needed.
- Added an option Portal Wand Keeps Locks Per Portal, so each portal keeps its own wand locks
- Fixed some portal animation bugs.
- Entities that are touching a portal now cast shadows with shader packs.
- Fixed the biome fog inside a portal changing while the player moves.
- Fixed the third person camera not colliding with blocks when it is looking through a portal.
- Fixed the third person view inside a portal breaking some GUI.
- Enabled Sodium's cave culling for both the outer view and the portal view.
- Improved performance without Sodium a little. Sodium is still strongly recommended.
- Squeezed out a bit more FPS from portal rendering and client chunk handling.

## 6.3.1

:::tip Highlights in this update:
Fixed VMP crashing, a more accurate Iris warning and reverted the query optimizations.
:::

- Fixed VMP crashing.
- Changed the Iris portal rendering warning. It now warns about seams along portal edges and about translucent things, such as stained glass and water, not rendering portals behind them. It no longer asks you to turn off TAA and motion blur.
- Reverted the query optimizations. They saved some FPS but made portals disappear when teleporting. The other optimizations are still in place.

## 6.3.0

:::tip Highlights in this update:
TAA and motion blur work with shaders, entity teleportation is much smoother, particles go through portals, and optimized portal rendering.
:::

- Fixed shader packs' TAA and motion blur breaking when a portal is in view.
- Portals now cast shadows with shader packs. It can be turned off with the "Include Portals in Iris Shadows" option, or for a single portal with the `renderIrisShadow` attribute. See [Whether to Render Iris Shadow](wiki:portal-attributes#whether-to-render-iris-shadow).
- Attempted to optimize portal rendering. You may not notice a difference.
- Added some rudimentary Distant Horizons compatibility.
- Fixed portals not being affected by the view's fog.
- Made entity teleportation much smoother.
- Particles now go through portals, and particles no longer face the wrong way inside portals.
- Sounds keep playing while you go through a portal. This can be turned off with the "Enable Cross Portal Sound" option.
- Fixed not being able to crouch or sneak on cross-portal collisions.
- Fixed Voxy not being compatible with the normal (non-compatibility) render mode.
- Fixed Vista's viewfinder not moving and not rendering portals.
- Fixed Veil breaking clipping and causing GL errors.
- Fixed EMF's Animation LOD Distance not accounting for portals.
- Fixed Punchy's moves and Sable's custom cameras breaking portal rendering.
- Fixed Punchy's physics not accounting for portals.
- Fixed Vanilla Backport's cold and warm mob variants not carrying over through cross-dimensional portals.
- Fixed some shader packs failing to load.
- Fixed Cross Portal Entity Rendering not triggering when it should have.

## 6.2.0

:::tip Highlights in this update:
Made Compatibility Render Mode support recursion, Made teleportation smoother, infinite falling and several rendering bugs are fixed.
:::

- Fixed entities not being able to be attacked or interacted with through a portal.
- Added support for portal recursion in Compatibility Render Mode. It is controlled by the "Portal Recursion Rendering in Compatibility Mode" option. See [Compatibility Render Mode](wiki:config-options#compatibility-render-mode).
- Made teleportation smoother and more correct for entities and players.
- Fixed Vanilla Backport's sulfur cubes not being able to be placed through portals.
- Fully fixed the hand not rendering between portals.
- Fixed Cross Portal Entity Rendering not working with shaders.
- Fixed underwater fog and effects not working with portals.
- Heavily fixed infinite falling for both entities and players.
- Fixed Axiom. Thanks to ro1net and Moulberry.

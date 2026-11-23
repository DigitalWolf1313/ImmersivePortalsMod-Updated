---
title: "Config Options"
category: "Reference"
order: 12
description: "Every option of the config screen and the config file."
source: "https://qouteall.fun/immptl/wiki/Config-Options.html"
updated: 2026-11-22
manual: true
---

## How to Access the Config

In Fabric version, you can access the config GUI via the mod menu (mod menu is provided by a mod, you need to manually install it).

You can also access the config GUI by using command `/imm_ptl_client_debug config` in game (this does not require the mod menu).

The config file is `/config/immersive_portals.json` (the config file includes both client and server configs).

## Client-Side Performance Configurations

When you are nearby a portal, the FPS may drop due to these reasons:

- Portal rendering. If the portal is invisible in the view (for example hidden by a wall) then it won't be rendered. Rendering a portal is roughly equivalent to rendering the whole world inside the portal again.
- Client lighting updates. Some remote light updates happen upon chunk data retrieval and some light updates happen upon portal rendering. The light updates may cause lag spikes.
- More chunk mesh rebuild. This mod tries not to build any remote chunk in the render thread during portal rendering. But rebuilding the chunks that are viewed through portals sometimes still costs performance.
- More frequent GC due to loading more chunks. Approaching a portal loads more chunks and entities which consumes more RAM and may increase GC frequency thus creating more lag spikes.

This mod's latest version can automatically reduce the chunks rendered through portal when client FPS is low.

### Max Portal Layer

Specifies the maximum portal-in-portal rendering layer.

When rendering infinite mirror room or world wrapping portals, the FPS may go very low because it renders too many portals in portals.

![](images/config-options-1.png)

If you set that value to 1, then it won't render any portal inside a portal.

![](images/config-options-2.png)

If it's 0, you can see the portal view area but the world inside the portal won't be rendered.

![](images/config-options-3.png)

### Lag Attack Proof

When FPS drops because of rendering too many portals (for example, a mirror room), it will enter "lag attack proof" mode and only render one layer of portals and only render near portals which helps you recover from the lag.

### Portal Render Limit

The maximum amount of portals that can be rendered in one frame.

### Enable Client Performance Adjustment

If enabled, less portal content is rendered when the game is laggy. It is enabled by default.

## Server-Side Performance Configurations

If a player is close to a portal, then the chunks on the other side will be loaded and ticked. The chunk loading radius of a portal is determined by the player's distance to the portal.

This mod has one-layer indirect loading and two-layer indirect loading. One-layer indirect loading means portals nearby a player can load chunks. Two-layer indirect loading means the portals near the one-layer loading portal's destination can also load chunks.

### Enable Server Performance Adjustment

If enabled, fewer chunks are loaded through portals when the server is laggy. It is enabled by default.

### Indirect Loading Radius Cap

Defines the maximum loading radius of indirect chunk loading through portals. Turning it down means that fewer chunks will be loaded when a player approaches a portal thus increase server performance.

The loading radius cap of global portals is twice this value.

This value is only used when the cap mode is `Manual`.

### Indirect Loading Radius Cap Mode

Controls where the indirect loading radius cap comes from.

- `RenderDistance` uses the server's chunk loading distance as the cap. It is the default.
- `Manual` uses the value of [Indirect Loading Radius Cap](#indirect-loading-radius-cap).

## Other Client-Side Configurations

### Compatibility Render Mode

If it's enabled, the portal rendering algorithm that avoids using the stencil buffer will be used and portal-in-portal cannot be rendered (unless [Portal Recursion Rendering in Compatibility Mode](#portal-recursion-rendering-in-compatibility-mode) is enabled). If when rendering portals your screen turns
into the portal then you should enable this option.
if you see portals through walls with (Iris) shaders enabled then you should enable this option.

### Portal Recursion Rendering in Compatibility Mode

Allows portal-in-portal rendering while Compatibility Render Mode is enabled. It is disabled by default.

### Include Portals in Iris Shadows

If enabled, portal surfaces cast shadows while a shader pack is enabled. It requires Iris. A single portal can opt out with its [`renderIrisShadow` attribute](wiki:portal-attributes#whether-to-render-iris-shadow). It is enabled by default.

### Enable Cross Portal Sound

If enabled, sounds from the other side of a portal can be heard, and sounds keep playing while you go through a portal. It is enabled by default.

### Render Yourself in Portal

If disabled, you cannot see yourself in portals. But other players will still be rendered.

### Pure Mirror

If enabled, the glass texture on mirrors will not be rendered. Mirrors also lose the glass texture while a shader pack is active.

### Cross Portal Entity Rendering

If disabled, the entities that are touching a portal will look clipped. However, sometimes cross portal entity rendering may cause rendering issues. It also works with shader packs.

### Portal Wand Keeps Locks Per Portal

If enabled, the [portal wand's](wiki:portal-customization#portal-wand) drag locks are not cleared when you point at another portal. Each portal has its own locks. They are cleared when the world is reloaded. It is disabled by default.

### Reduced Portal Rendering

If enabled, portals are only rendered at a short range and the chunks seen through portals are limited to a third of the render distance. It is disabled by default.

### Enable Nether Portal Sound and Particle

If disabled, nether portals do not play their sound and do not spawn particles. It is enabled by default.

### Tolerant Version Mismatch with Server

If enabled, the client is allowed to connect to a server that has a different version of this mod. A version mismatch may cause bugs. It is disabled by default.

## Other Server-Side Configurations

### Nether Portal Mode

[See also](wiki:portals#nether-portals)

### Light Vanilla Nether Portal When Crouching

If enabled, when you are crouching, flint and steel lights vanilla nether portal.

### End Portal Mode

[See also](wiki:portals#end-portals)

### Enable Alternate Dimensions

If disabled, the alternate dimensions won't be loaded, and all portals pointing to alternate dimensions will vanish.

### Enable Nether Portal Overlay

If enabled, the nether portals will have vanilla nether portal texture overlay.

![](images/config-options-4.png)

### Scale Limit

If the entity's scale is too small or too big after crossing the portal, its scale will be reset. (The scale is from [Pehkui](https://www.curseforge.com/minecraft/mc-mods/pehkui)) Having too big scale will lag the server's collision calculation. Having too small scale will make the entity nearly invisible. The scale limit only applies when one entity go through a portal.

If this config value is `x`, the actual scale limit is `1/2x` to `x` .

### Portal Searching Range

The range that the existing portal frame is being searched in when lighting a nether portal (or datapack custom portal).

### Ease Creative Permission

If enabled, creative mode players that do not have level 2 permission can use most `/portal` commands and the portal wand.

### Ease Command Stick Permission

If enabled, all players can use command sticks. If disabled, only the creative mode player and the ones with level 2 permission can use.

### Enable Mirror Creation

If disabled, flint and steel can no longer turn glass into a mirror.

### Enable Datapack Portal Generation

If disabled, datapack-based custom portal generation is turned off. See [Datapack-Based Custom Portal Generation](wiki:datapack-based-custom-portal-generation).

### Enable Warning

If disabled, the player is no longer told that a nether portal cannot connect to a vanilla portal block.

### Regular Portal Length Limit

The maximum length of a regular portal frame, in blocks. It is from 3 to 64 and 64 by default.

### Portals Change Gravity by Default

If enabled, newly created portals change the gravity direction of teleported entities. It only has an effect with a gravity mod. See [Spatial Transformation](wiki:spatial-transformation). It is disabled by default.

### Portal Wand Usable on Survival Mode

If enabled, survival mode players can use the [portal wand](wiki:portal-customization#portal-wand). Players with level 2 permission can always use it, and creative mode players can use it when Ease Creative Permission is enabled. It is disabled by default.

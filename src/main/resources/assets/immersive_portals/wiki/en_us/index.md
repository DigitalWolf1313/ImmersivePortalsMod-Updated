---
title: "Home"
category: "Getting Started"
order: 1
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/index.html"
updated: 2026-11-22
manual: true
---

This wiki describes the latest version of Immersive Portals Updated (abbreviated iPortal+, iPortalU or ImmPtl-U).

This build is a fork that adds more features and compatibility. [The changes of the latest updates are listed here.](wiki:fork-changelog)

The main additions of this fork are:

- An [Animate Portal wand](wiki:animate-wand) for the portal wand that records keyframe animations.
- Portals that [emit light](wiki:portal-attributes#light-attributes).
- [Shader pack improvements](wiki:miscellaneous#iris-shader-compatibility): TAA and motion blur work with portals.
- An in-game wiki that includes this fork's changes
- Optimization improvements
- [Support for more mods](#mods-this-fork-adds-support-for).

## Common Questions

### Can I use it in multiplayer?

Yes. The server and client must both install this mod. (This mod is not a plugin and cannot be used on Bukkit, Spigot or Paper.)

### Sodium Compatibility?

Start from MC 1.19.x+, this mod is **compatible with only one version of Sodium**. Fabric will show the error when you are using an incompatible version of Sodium. The same applies to Iris.

> You can [use dependency override](wiki:miscellaneous#remove-the-sodium-iris-version-lock) to make the game launch with other versions of Sodium/Iris. trying to launch sodium 0.6.13 with this wont work.

This fork is built for MC 1.21.1 and MC 1.20.1 and is compatible with Sodium 0.8.12 and iris 1.8.14

### Mod Compatibility?

**Immersive Portals mod is incompatible with some mods. It's recommended to check [Known compatibility issues](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/issues) before using this mod.** The mods that don't touch inner game mechanics are more likely to be compatible with iPortal+.

**This mod deeply changes the game engine and eliminted the game engine's limitation to achieve the result.** Vanilla Minecraft does not allow loading multiple dimensions on client at the same time. Vanilla's rendering engine also does not work with portal rendering. The see-through portal and seamless teleportation cannot be achieved without deeply changing the game engine.

**Any mod compatibility issue is NOT intentional.** Other mods use the game engine in some ways and this mod changes the game engine, so there may be incompatibility. Incompatibilities are not made intentionally. The compatibility issues come from how computer programs work.

**Fixing compatibility issues requires a lot of efforts.** Compatibility issues can be fixed by ad-hoc hacking which is both hard and fragile. This mod is compatible with Sodium and Iris via ad-hoc hacking which is fragile (It's possible that next version of Sodium/Iris will not be compatible with iPortal+). The close-sourced mods such as OptiFine are much harder to do ad-hoc hacking with so I marked OptiFine as incompatible.

#### Mods this fork adds support for

This fork adds or improves compatibility with these mods:

Sodium, Iris, Voxy, Distant Horizons (rudimentary), Veil, Vista, EMF, Punchy,
Pehkui, Gravity changer (fork), Vanilla Backport, Axiom and Very Many Players.

Compatibility efforts made to make these mods compatible may make other mods compatible too.

See the [fork changelog](wiki:fork-changelog) for what was fixed for each mod.

If you encounter compatibility issue in a large modpack, [binary search is a way of finding the incompatibe mod](#how-to-find-the-possible-incompatibe-mod).

If you are a mod developer, [the possible cases of incompatibility may come from these ways](wiki:api-for-other-mods#possible-sources-of-mod-incompatibility-with-immersive-portals).

### Will the portal impact performance?

Loading more chunks and rendering more things will affects performance. I already put a lot of efforts into optimizing this mod's performance and make portal rendering compatible with [Sodium](https://modrinth.com/mod/sodium) (not all Sodium versions are compatible with iPortal+, see the above).

It's recommended to allocate more memory to Minecraft. Not having enough memory will make the game laggy. Sometimes the official launcher will by default only allocate 2GB to Minecraft even if the computer have many memory. [How to allocate more RAM to Minecraft](https://filmora.wondershare.com/game-recording/how-to-allocate-more-ram-to-minecraft.html)

Also, [ImmediatelyFast](https://modrinth.com/mod/immediatelyfast) is an optimization mod that works with this mod.

### Where to report bugs or compatibility issues?

[Report Issues (Fabric version)](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/issues)

This fork does not have a forge version yet

**This mod is very complex and the core part of the mod is only mainly developed by only one person at spare time for free. Expect it to be imperfect.**

### How to integrate with other mod's portals?

You can use [datapack](wiki:datapack-based-custom-portal-generation) to make it convert other mod's portals into see-through portals.

### How to make portal helper craft-able in survival?

You can make other blocks to behave like portal helper. [Check this](wiki:portal-customization#how-to-use-similar-functionality-in-survival-mode)

### The nether portal is not see-through.

This mod does not change existing vanilla portals. You need to light new portals.

### The nether portal does not link.

It can only link to an empty obsidian frame with the same shape and orientation (unless you change the nether portal mode to `adaptive`). It will not link to a vanilla nether portal.

### How to find the possible incompatibe mod?

If you encounter compatibility issue (an issue that does not arise with only Immersive Portals mod), you can firstly check [Known compatibility issues](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/issues).

If the compatibility issue is not yet known, **binary search** is a way of finding the incompatible mod without removing the mods one-by-one. The procedure is simple:

1. Backup the world (because loading the world without the mod cause its content to be removed).
2. Remove half of the existing mods, and put them aside.
3. Run the game.
4. Does the issue still exist? If YES: Repeat from step 2 with the current mods. If NO: Swap out the current mods with the ones set aside, and repeat from step 1.
5. Repeat this process until the problematic mod(s) have been found.

If you find a new compatibility issue, please report it on GitHub.

### It does not render far content in portal.

This mod will automatically reduce cross-portal chunk loading range and rendering range when the FPS is low or when the free memory amount is low, to reduce lagging.

If you don't want that, open the config via mod menu, then disable "Enable Server Performance Adjustment" and "Enable Client Performance Adjustment" and "Lag Attack Proof".

If it's in a server, change `enableServerPerformanceAdjustment` to `false` in the server's mod config file and restart the server.

By default, the chunk loading radius on the other side follows the server's chunk loading distance. To use a fixed cap, set "Indirect Chunk Loading Radius Cap Mode" (`indirectLoadingRadiusCapMode`) to `Manual` on the server, then change "Indirect Chunk Loading Radius Cap" (`indirectLoadingRadiusCap`, 8 by default) to the value you want. [See also](wiki:config-options#indirect-loading-radius-cap-mode)

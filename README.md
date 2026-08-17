# Immersive Portals Mod

This fork does include some AI Generated code. So if you don't like AI, you can go back to the original.

It's a Minecraft mod that provides see-through portals and seamless teleportation. It also can create "Non-Euclidean" (Uneuclidean) space effect.

![immptl.png](https://i.loli.net/2021/09/30/chHMG45dsnZNqep.png)

[On CurseForge](https://www.curseforge.com/minecraft/mc-mods/immersive-portals-continued) (Updated)     [On Modrinth](https://modrinth.com/mod/immersive-portals-updated) (Review pending)    [Website](https://qouteall.fun/immptl/)

This mod changes a lot of underlying Minecraft mechanics. This mod allows the client to load multiple dimensions at the same time and synchronize remote world information(blocks/entities) to client. It can render portal-in-portals. The portal rendering is roughly compatible with some versions of Sodium and Iris. The portal can transform player scale and gravity direction.  [Implementation Details](https://qouteall.fun/immptl/wiki/Implementation-Details)

(This is the Fabric version of Immersive Portals. [The Forge version](https://github.com/iPortalTeam/ImmersivePortalsModForForge)) (does not contain this fork's changes.)

## Goals of this fork

This fork aims to

1: Improve mod compatibility

2: Fix any bugs

3: Add new features

No promises are made regarding long-term stability of this fork However
I will always do my best to keep it stable and maintainable.

I am also **Not** porting this mod to later version, others are already working on that.

## API

This mod also provides some API for:

* Manage see-through portals
* Dynamically add dimensions
* Synchronize remote chunks to client
* Render the world into GUI
* Other utilities

[API description](https://qouteall.fun/immptl/wiki/API-for-Other-Mods.html).

## How to run this code
https://fabricmc.net/wiki/tutorial:setup

## Other

[Wiki](https://qouteall.fun/immptl/wiki/)

[Discord Server](https://discord.gg/BZxgURK)

[Support qouteall on Patreon](https://www.patreon.com/qouteall)

## Credits

[Acuadragon100](https://github.com/Acuadragon100) for creating the [Valkyrien Skies crash fix](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/commit/1bc4936480270446ecf06c8c4ea976bdf102fb40) and checking some of my code.

[Mikulasz12](https://github.com/Mikulasz12) For creating A version of ["MixinSodiumViewport"](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/commit/3cd10cf5e6a0a3e8cd8984cd122e51e0c2db93cc) That works with Sodium 8.12

[r2smith141](https://github.com/r2smith141) for creating some [sable compatibility code](https://github.com/DigitalWolf1313/ImmersivePortalsMod-Updated/commit/e567f7e8b0a82eecf9812dc86cdc421111878e70).
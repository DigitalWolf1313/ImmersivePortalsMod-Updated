---
title: "Spatial Transformation"
category: "Dimensions"
order: 10
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/Spatial-Transformation.html"
updated: 2026-11-21
---

A portal is a window linking to a transformed space. The spacial transformation can be translation, rotation, scale and mirroring.

This mod's teleportation is eye-based. If an entity goes through a portal, it will calculate the entity's eye position transformed by the portal and then place the entity by the transformed eye position.

## Rotation Transformation

![rotation.png](images/spatial-transformation-1.png)

[Commands related to rotation](wiki:commands-reference#rotation)

### Teleportation

After crossing a portal with rotation transformation, the player's camera may be tilted. Then the camera rotation will smoothly turn into a valid state.

This mod has special compatibility with Gravity Changer mod. With Gravity Changer mod, if you enable `teleportChangesGravity` to a portal, then your gravity direction will be automatically changed when crossing the portal. (`teleportChangesGravity` is disabled by default.)(This fork fixes the Gravity Changer support in 1.21.1.)

### About Gravity Changer Mod

[The original Gravity Changer mod by Gaider10 (Andrew)](https://www.curseforge.com/minecraft/mc-mods/gravitychanger). Gaider10 (Andrew) stopped maintaining it.

In 1.20.1, use [qouteall's fork of Gravity API](https://modrinth.com/mod/gravity-api-fork).
In 1.21.1, use [NightEpiphany's fork of Gravity API](https://modrinth.com/mod/gravity-changer-unofficial-port).
(note: other fork's should also work)

## Scale Transformation

![image.png](images/spatial-transformation-2.png)

[Commands related to scaling](wiki:commands-reference#scale)

### Teleportation

Start from MC 1.21.1, this mod will use a modifier `iportal:scaling` to the entity attribute `minecraft:generic.scale` to do entity scaling. You can remove the scaling caused by portal using command `/attribute @s minecraft:generic.scale modifier remove iportal:scaling`. Going through portal won't change the base scale.

Whether to scale the entity going through portal is controlled by portal data `teleportationChangesScale`. (it is enabled by default)

this fork has special compatibility with the [Pehkui mod](https://www.curseforge.com/minecraft/mc-mods/pehkui) on 1.21.1. With Pehkui, it uses Pehkui's scale systems instead of the vanilla scale system.

## Mirror Transformation

[Mirrors](wiki:portals#mirrors).

### Teleportation

Mirrors do not allow teleportation. Because when the player goes into a mirror, the player itself should get mirrored. Currently there is no mod that provides the functionality of mirroring the player (imagine that the right hand appear on the left and turning right actually turns left).

However, you can create a fake-enterable mirror, by using command `/portal turn_into_fake_enterable_mirror` on a normal portal. [Details about that command](wiki:commands-reference#portal-turn-info-fake-enterable-mirror).

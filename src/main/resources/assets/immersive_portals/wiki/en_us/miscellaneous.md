---
title: "Miscellaneous"
category: "Reference"
order: 14
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/Miscellaneous.html"
updated: 2026-11-21
manual: true
---

### Text Translation

You can [contribute text translation on Crowdin](https://crowdin.com/project/immersive-portals-mod). Thanks to the translation contributors.

If you want to add another language, please contact DigitalWolf13.

### Iris Shader Compatibility

Immersive Portals mod roughly works with some Iris shaderpacks. Not all shaderpacks are compatible (path tracing shaders are incompatible).

:::tip
TAA And motion blur works with portals in this fork.
:::

![](images/shader_compat.png)

The portal rendering with shader **is not perfect because of technical difficulties. The Iris/OptiFine shaderpack standard is not designed to work with portal rendering. I already tried hard to hack the shader rendering process to make it work with portals.**

Following things cannot be correctly rendered with shaders:

- Semi-transparent things (e.g. stained glass) in front of the portal. (Due to that, all mirrors will cover the glass texture with shaders on)
- Portal overlay (because of the above)
- Fuse-view portals
- Colored Lighting (with same dimension portals)
- Path tracing (with same dimension portals)
- World Curvature with portals

Most shaders work fine, the above issues are in every shaderpack

Other than that, effects like rain distortion will be abnormal with portals. And the portal rendering with shader does not work on some ancient videocards.


### Could a Server with This Mod Work with Vanilla Client?

No. Because

- Networking protocol differences. This mod allows the client to load multiple dimensions at the same time, while vanilla only allow one dimension to be loaded at the same time. The vanilla networking packets does not discriminate between different dimensions, so Immersive Portals change the networking protocol to discriminate between dimensions. (It's theoretically possible to automatically adjust to networking protocol differences, but it would be very complex and error-prone so it's not worth the effort)
- This mod's portals are more flexible than vanilla. This mod's portal can be in any shape and can be tilted. Vanilla portal blocks cannot represent that.

### Could a Client with This Mod Work with Vanilla Server?

A client with this mod should be able to join a vanilla server (if not, report the issue).

However, if the server does not have this mod, then the server does not send chunk data in remote dimension to client, so client cannot render the see-through portal.

### Could a Portal Link to Another Server?

No. Inter-server portals require sending packets across servers, cross-server visibility check, a cross-server dimension id system, and entity transfer between servers. It's very hard to implement.

### Do Sounds and Particles Work Across a Portal?

yes. Particles go through portals in this fork, and sounds play from across the portal, The cross-portal sound can be turned off with the [Enable Cross Portal Sound](wiki:config-options#enable-cross-portal-sound) option.

### OptiFine Compatibility

In MC 1.18 and above, this mod is incompatible with OptiFine. Because OptiFine is not open-source, debugging with OptiFine is very hard. It's recommended to use Sodium and Iris. [Check this](https://lambdaurora.dev/optifine_alternatives/)

In older MC versions, this mod is problematic with OptiFine so it's not recommended to use this mod with OptiFine.

### What Happens if I Remove iPortal+?

All see-through portals will vanish. Even if you re-install iPortal+, the portals cannot recover. (Portals in unloaded chunks may remain.)

Vanilla nether/end portals still exist. Dimension stack bedrock replacement will not work.

### Can Fluid, Redstone and Light Work Across a Portal?

No. This mod's portal supports scaling and rotation transformation. The portal can be in anywhere and be in any orientation. It's hard to imagine how redstone signal transfers when the blocks are not aligned.

![](images/miscellaneous-2.png)

But you can use entities to carry signals through a portal. For example, an item dispenser on one side and a hopper on the other side.

Although it's possible to implement cross portal redstone and fluid when the portal is aligned with blocks, it would be very hard to implement. And cross-portal redstone machines won't be stable because of the chunk loading delay.

About lighting: The nether portals by default has glowstone-level lighting. For custom portals, you can put lights behind the portal to make it looks seamless. A portal can also emit light by itself. See [Light Attributes](wiki:portal-attributes#light-attributes).

### Upgrade a World that Have Immersive Portals Things

For example if you have a world in MC 1.15.2 that contains immersive portals, and then you want to upgrade the world to 1.18.2, then you need to firstly upgrade to 1.16.5 then 1.17.1 then 1.18.2. Otherwise the portals may get lost.

### Remove the Sodium/Iris version lock

This mod uses the internals of Sodium and Iris for compatibility. These internals may change in future versions of Sodium/Iris. So this mod locks the compatible Sodium/Iris version. If you want to launch the game with this mod and newer versions of Sodium/Iris, you can use [Dependency Override](https://fabricmc.net/wiki/tutorial:dependency_overrides).

Add `fabric_loader_dependencies.json` into `config` folder with these content:

```json
{
    "version": 1,
    "overrides": {
        "immersive_portals": {
            "-breaks": {
                "iris": "IGNORED",
                "sodium": "IGNORED"
            }
        }
    }
}
```

**Note: the game may crash when using the non-locked versions of Sodium/Iris with Immersive Portals.**

### The website source

[This wiki on GitHub](https://github.com/qouteall/immptl)

Fell free to create a PR if you want to suggest changes.

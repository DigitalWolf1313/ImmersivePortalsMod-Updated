---
title: "Datapack-Based Custom Portal Generation"
category: "Dimensions"
order: 11
description: "See through portals and teleport seamlessly."
source: "https://qouteall.fun/immptl/wiki/Datapack-Based-Custom-Portal-Generation.html"
updated: 2024-01-13
---

Configure custom portal generation mechanics using JSON files in a datapack.

[What are datapacks and how to install them](https://minecraft.gamepedia.com/Data_Pack)

## Examples

[Download the example datapack](https://github.com/qouteall/ImmersivePortalsMod/raw/1.20.4/example_custom_portal_gen.zip) You can use the example datapack by putting it into the `datapacks` folder of the world.

The example datapack has:

`convert_vanilla_nether_portal.json` : Convent vanilla nether portals into see-through portals (if the shapes are compatible)

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["minecraft:overworld"],"to": "minecraft:the_nether",
  "form": {"type": "imm_ptl:convert_conventional_portal","portal_block": "minecraft:nether_portal"},
  "trigger": {"type": "imm_ptl:conventional_dimension_change"}
}
```

`wood_portal.json`: From overworld to alternate5. The frame can be in any type of wood planks. Activated using a compass.

> It requires dimension `immersive_portals:void`. That dimension can be added by command `/dims add_dimension "immersive_portals:void" void`

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["minecraft:overworld"], "to": "immersive_portals:void",
  "form": {
    "type": "imm_ptl:heterogeneous", "frame_block": "minecraft:planks",
    "area_block": "minecraft:air", "generate_frame_if_not_found": true
  },
  "trigger": { "type": "imm_ptl:use_item","item": "minecraft:compass" }
}
```

`portal_helper_like.json`: A diamond portal that links to the nearby same-shaped portal in the same dimension. Similar to portal helper but breakable.

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["imm_ptl:any_dimension"],
  "to": "imm_ptl:the_same_dimension",
  "reversible": false,
  "form": {
    "type": "imm_ptl:try_hard_to_match",
    "from_frame_block": "minecraft:diamond_block", "area_block": "minecraft:air",
    "to_frame_block": "minecraft:diamond_block", "generate_frame_if_not_found": false
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:flint_and_steel"}
}
```

`special_nether_portal`: A new type of nether portal. Throw a nether quartz into a 3x3 lava pool surrounded by soul soil blocks. Can only be created in overworld.

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["minecraft:overworld"],  "to": "minecraft:the_nether",
  "reversible": false,
  "form": {
    "type": "imm_ptl:flipping_floor_square",
    "length": 3, "frame_block": "minecraft:soul_soil", "area_block": "minecraft:lava"
  },
  "trigger": { "type": "imm_ptl:throw_item", "item": "minecraft:quartz"}
}
```

`aether_like.json` : The glowstone portal that's activated using a water bucket from overworld to alternate2 dimension. Similar to the aether's.

> It requires dimension `immersive_portals:skyland`. That dimension can be added by command `/dims add_dimension "immersive_portals:skyland" skyland`

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["minecraft:overworld"], "to": "immersive_portals:skyland",
  "form": {
    "type": "imm_ptl:classical",
    "from_frame_block": "minecraft:glowstone", "area_block": "minecraft:air", 
    "to_frame_block": "minecraft:glowstone", "generate_frame_if_not_found": true
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:water_bucket" }
}
```

`twilight_forest_like.json` : A floor flipping portal to the skyland dimension. Activated by throwing diamond to a 2x2 water pool surrounded by dirt with flowers on top. Similar to the twilight forest portal. It will summon a lightning bolt after creating the portal.

> It requires dimension `immersive_portals:skyland`. That dimension can be added by command `/dims add_dimension "immersive_portals:skyland" skyland`

```
{
  "schema_version": "imm_ptl:v1",
  "from": ["minecraft:overworld"], "to": "immersive_portals:skyland",
  "form": {
    "type": "imm_ptl:flipping_floor_square",
    "length": 2, "frame_block": "minecraft:bamboo_plantable_on", "area_block": "minecraft:water",
    "up_frame_block": "minecraft:small_flowers", "bottom_block": "minecraft:bamboo_plantable_on"
  },
  "trigger": { "type": "imm_ptl:throw_item", "item": "minecraft:diamond" },
  "post_invoke_commands": [ "/summon minecraft:lightning_bolt ~ ~ ~" ]
}
```

`lapis_redstone_portal.json`: From overworld to the chaos dimension. The overworld side frame is lapis block, but the other side's frame is redstone block. And for this portal, one block in the overworld corresponds to 8 blocks in chaos dimension.

> It requires dimension `immersive_portals:chaos`. That dimension can be added by command `/dims add_dimension "immersive_portals:chaos" chaos`

```
{
  "schema_version": "imm_ptl:v1",
  "from": [ "minecraft:overworld" ], "to": "immersive_portals:chaos",
  "space_ratio_from": 1, "space_ratio_to": 8,
  "form": {
    "type": "imm_ptl:classical",
    "from_frame_block": "minecraft:lapis_block", "area_block": "minecraft:air",
    "to_frame_block": "minecraft:redstone_block", "generate_frame_if_not_found": true
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:flint_and_steel" }
}
```

## Details Explained

The datapack-based custom portal generation system allows configuring special portal generation mechanics. The portal generation can be triggered by the player using an item or throwing an item. It can be configured to only generate in one dimension and goes to another dimension. The space ratio can be configured to determine where the destination should be. A list of commands can be specified to be invoked after portal creation. There are different portal forms each with different kinds of portals and different destination selection mechanics.

All the generated portals will be `immersive_portals:general_breakable_portal`. They will be generated with placeholder blocks filling the area. And they will break if the frame is broken. (Placeholder blocks have illumination)

The generated portal's `portalTag` will be the identifier of the generation.

The custom portal generation json should be located in `data/<namespace>/custom_portal_generation/`.

### The Format of Custom Portal Generation

- `schema_version` String. Must be `imm_ptl:v1`.
- `from` List of dimension ids. The dimensions that this generation can perform in. A special case: if it is `["imm_ptl:any_dimension"]` then the generation can perform in any dimension.
- `to` Dimension id. The destination dimension's id. A special case: if it's `imm_ptl:the_same_dimension`, the destination dimension will be the same as the from dimension. If the destination dimension does not exist, this generation will be disabled. The destination dimension can be set to a datapack-added dimension or a mod-added dimension and only enables when that datapack or mod is present.
- `space_ratio_from` Integer (1 if missing). This side dimension's space ratio.
- `space_ratio_to` Integer (1 if missing). The other side dimension's space ratio. Together with the above defines the space mapping. For example, 8 blocks' length in overworld corresponds to 1 block's length in the nether. The overworld's space ratio is 8 and the nether's space ration is 1.
- `reversible` Boolean (true if missing). If true, the reverse version of this generation will also be loaded. If you configured a portal from overworld to nether with reversible false, then the portal can only be activated in the overworld. If it's true then the portal can also be activated in the nether. If there are multiple from dimensions, the first one will be selected for the destination of the reverse generation.
- `post_invoke_commands` String list. Optional. The commands that will be invoked after the portals generated. The command invoker will be the portal entities. Every generated portal will invoke these commands. For example if you want the generated portal to be non-teleportable, put `"/portal set_portal_nbt {teleportable:false}"` into this.
- `commands_on_generated` A list of lists of strings. Optional. Unlike the above, can make different portal entities to invoke different commands.
- `form` Custom portal generation form. Described below.
- `trigger` Custom portal generation trigger. Described below.

### The Format of Custom Portal Generation Form

#### `type` : `imm_ptl:classical`

The classical form. Similar to the nether portal, it can be in any shape and can be horizontal. The frame can only be constituted by one type of block. However, this side's frame block can be different from the other side's frame block. Upon activation, it will first check whether the other side's terrain is generated. If the other side's terrain is generated, it will load surrounding chunks and then search for the existing portal frame. If an existing frame is found, the portal will link to it.

- `from_frame_block` Block id. This side's frame block.
- `area_block` Block id. The portal frame content's block. (The activation spot's block will not be checked. If the area block is air and you trigger witl flint and steel, the activation spot's block will be fire block. If the area is all air blocks except this fire block the portal still can generate)
- `to_frame_block` Block id. The other side's frame block.
- `generate_frame_if_not_found` Boolean. If true, when the existing frame is not found, it will search for new portal placement nearby and generate the new portal frame. The frame block can be duplicated by generating the portal. If false, it will cancel generation if the existing frame is not found and the frame block cannot be duplicated.

#### `type` : `imm_ptl:heterogeneous`

Similar to the above but the frame block can be constituted by several different types of blocks. The block collection is specified by a [block tag](https://minecraft.gamepedia.com/Tag). When generating a new frame, the block pattern of this side's frame will be cloned.

- `area_block` Block tag or block id. Specifies the portal area block.
- `frame_block` Block tag or block id. Specifies the portal frame block.
- `generate_frame_if_not_found` Boolean.

#### `type` : `imm_ptl:flipping_floor_square`

The portal must be horizontal and the shape must be a square. The generated portal will be one-faced and has a rotating transformation of 180 degrees around the X-axis. The "world inside portal" is flipped. And the generated portal will have motion affinity 0.1, which means that the player will be accelerated when touching the portal. Unlike the other forms, the blocks above the frame and the blocks below the area can also be specified.

Upon activation, it will directly search for new portal placement without loading chunks and searching for the existing frame. The generated portal will occupy ground blocks.

- `length` Integer. The side length of the square.
- `frame_block` Block tag or block id. Specifies the portal frame blocks.
- `area_block` Block tag or block id. Specifies the portal area block.
- `up_frame_block` Block tag or block id. Optional. Specifies the blocks on the top of the frame blocks.
- `bottom_block` Block tag or block id. Optional. Specifies the blocks below the area blocks.

#### `type` : `imm_ptl:scaling_square`

The portal can be horizontal or vertical. But the shape must be a square. This side shape's side length can be different to the other side shapes'. The created portal will have a scaling transformation that adapts to the shape scale difference.

- `from_length` Integer. This side frame's side length.
- `to_length` Integer. Other side frame's side length.
- `from_frame_block` Block id. This side's frame block.
- `area_block` Block id. The portal frame content's block.
- `to_frame_block` Block id. The other side's frame block.
- `generate_frame_if_not_found` Boolean.

#### `type` : `imm_ptl:flipping_floor_square_new`

Similar to `imm_ptl:flipping_floor_square` it generates floor portal with a flipping rotation transformation. But this does not require hardcoding the square side length. And this searches for the existing frame on the other side before generating the new frame.

- `area_block` Block tag or block id. Specifies the portal area block.
- `frame_block` Block tag or block id. Specifies the portal frame block.
- `generate_frame_if_not_found` Boolean.

#### `type` : `imm_ptl:try_hard_to_match`

It diligently tries to match with the scaled and rotated portal frame. The classical nether portal can only link to the frame that has the exact size and orientation. But this portal generation can link to the shape with different scale and rotation as long as the topology shape matches. It can create the portal with rotation transformation and scale transformation. Unlike `imm_ptl:flipping_floor_square` and `imm_ptl:scaling_square`, the rotation and scale do not need to be hard-coded and the shape can be not square.

The maximum area side length is 20.

- `from_frame_block` Block id. This side's frame block.
- `area_block` Block id. The portal frame content's block.
- `to_frame_block` Block id. The other side's frame block.
- `generate_frame_if_not_found` Boolean.

#### `type` : `imm_ptl:convert_conventional_portal`

This can only be used with trigger `imm_ptl:conventional_dimension_change`. When a player goes through a conventional portal and experience a dimension travel with loading screen, this can be used to convert that conventional portal into a see-through portal.

**It converts the portal after the player goes through it once. It does not convert the portal immediately after creation.** Different mods have different ways of selecting destination of the portal that iPortal cannot know in advance. So this only runs after teleportation.

It can only convert the conventional portal that have the same type of portal block on both sides and the shapes are compatible.

If the conventional portal is horizontal, it will generate a one-faced bi-way portal with a flipping rotation transformation. Otherwise, it behaves the same as `imm_ptl:try_hard_to_match`.

For this form, the space ratio does not matter.

- `portal_block` Block id or block tag. The conventional portal's block.

#### `type` : `imm_ptl:one_way`

Creates one-way portal.

- `frame_block` Block id or block tag. The frame block.
- `area_block` Block id or block tag. The frame content block.
- `bi_faced` Boolean. If true generates bi-faced portal.
- `breakable` Boolean. Optional. If false, it will not generate the placeholder blocks and the portals will be unbreakable.

The destination can be specified by command `/portal set_portal_destination` in `commands_on_generated`.

### The Format of Custom Portal Generation Trigger

Specifies when and where should the portal generates.

#### `type` : `imm_ptl:use_item`

Activates when the player right-clicks using an item.

- `item` Item id.
- `consume` Boolean (false if missing). If true, the item will be consumed if the generation performed (except in creative mode).

It also works with block placement. For example, setting the item to `minecraft:dirt` will make it trigger when the player places a dirt block.

#### `type` : `imm_ptl:throw_item`

Checks every tick for the item entity that cannot be instantly picked up. (When a player throws one item it cannot be instantly picked up because the item entity has pick-up delay.) If an item is not thrown within 2 seconds, it will have no pick-up delay and cannot trigger the generation. The item can survive in lava for 1 tick, so throwing a non-fire-proof item into lava could still trigger the generation.

- `item` Item id.

#### `type` : `imm_ptl:conventional_dimension_change`

It will be triggered when the player experiences conventional dimension travel (with loading screen), for example going through a vanilla nether portal. This is useful for converting other mod's portal.

**This only works for the portals that are similar to nether portal**, such as the Lost Paradise (Aether) portal. "Similar to nether portal" means that the two side's portal block structure are composed by inner portal blocks and outer frame blocks.

## Other Examples

One-way end portal using bedrock as frame:

```json
{
  "schema_version": "imm_ptl:v1",
  "from": [ "minecraft:overworld" ], "to": "minecraft:the_end",
  "form": {
    "type": "imm_ptl:one_way", "frame_block": "minecraft:bedrock",
    "area_block": "minecraft:air", "bi_faced": false
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:flint_and_steel"},
  "commands_on_generated": [
    [
      "/portal set_portal_destination minecraft:the_end 0 80 0"
    ]
  ]
}
```

Allow making nether portal using crying obsidian:

```json
{
  "schema_version": "imm_ptl:v1",
  "from": [ "minecraft:overworld" ], "to": "minecraft:the_nether",
  "space_ratio_from": 8, "space_ratio_to": 1,
  "form": {
    "type": "imm_ptl:classical",
    "from_frame_block": "minecraft:crying_obsidian", "area_block": "minecraft:air",
    "to_frame_block": "minecraft:crying_obsidian", "generate_frame_if_not_found": true
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:flint_and_steel" }
}
```

If you want the nether portal generation that allows mixing crying obsidian and normal obsidian, firstly add a tag. In `/data/i_am_namespace/tags/blocks/my_tag.json`:

```json
{
    "values": [
        "minecraft:obsidian",
        "minecraft:crying_obsidian"
    ]
}
```

Then the custom portal generation can be

```json
{
  "schema_version": "imm_ptl:v1",
  "from": [ "minecraft:overworld" ], "to": "minecraft:the_nether",
  "space_ratio_from": 1, "space_ratio_to": 8,
  "form": {
    "type": "imm_ptl:heterogeneous",
    "frame_block": "i_am_namespace:my_tag", "area_block": "minecraft:air",
    "generate_frame_if_not_found": true
  },
  "trigger": { "type": "imm_ptl:use_item", "item": "minecraft:flint_and_steel" }
}
```
